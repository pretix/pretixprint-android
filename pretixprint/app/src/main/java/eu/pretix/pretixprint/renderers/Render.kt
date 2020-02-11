package eu.pretix.pretixprint.renderers

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import eu.pretix.pretixprint.byteprotocols.ByteProtocol
import java8.util.concurrent.CompletableFuture
import java.io.File
import java.lang.RuntimeException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

fun <T> renderFileTo(file: File, i: Int, d: Float, future: CompletableFuture<T>, type: Class<T>) {
    if (type.isAssignableFrom(Bitmap::class.java)) {
        if (Build.VERSION.SDK_INT >= 21) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(fd)
            val page = renderer.openPage(i)
            val img = Bitmap.createBitmap((page.width / 72.0 * d).toInt(), (page.height / 72.0 * d).toInt(), Bitmap.Config.ARGB_8888)
            page.render(img, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            @Suppress("UNCHECKED_CAST")
            future.complete(img as T)
            page.close()
            renderer.close()
            fd.close()
        } else {
            val doc = PDDocument.load(file.inputStream())
            val renderer = PDFRenderer(doc)
            for (page in 0 until doc.pages.count) {
                val img = renderer.renderImageWithDPI(page, d)
                @Suppress("UNCHECKED_CAST")
                future.complete(img as T)
            }
        }
    } else if (type.isAssignableFrom(ByteArray::class.java)) {
        future.complete(file.readBytes() as T)
    } else {
        throw RuntimeException("Unknown type signature")
    }
}

val threadPool: ExecutorService = Executors.newCachedThreadPool()

inline fun <reified T> renderPages(protocol: ByteProtocol<T>, file: File, d: Float, numPages: Int): List<CompletableFuture<ByteArray>> {
    val futures = mutableListOf<CompletableFuture<ByteArray>>()
    var previousBmpFuture: CompletableFuture<T>? = null

    for (i in 0 until numPages) {
        val bmpFuture = CompletableFuture<T>()
        val byteFuture = CompletableFuture<ByteArray>()

        if (previousBmpFuture != null) {
            previousBmpFuture.thenApplyAsync {
                renderFileTo<T>(file, i, d, bmpFuture, T::class.java)
            }
            bmpFuture.thenCombineAsync(previousBmpFuture) { bmp1, bmp2 ->
                byteFuture.complete(protocol.convertPageToBytes(bmp1, i == numPages - 1, bmp2))
            }
        } else {
            threadPool.submit {
                renderFileTo<T>(file, i, d, bmpFuture, T::class.java)
            }
            bmpFuture.thenApplyAsync {
                byteFuture.complete(protocol.convertPageToBytes(it, i == numPages - 1, null))
            }
        }

        previousBmpFuture = bmpFuture
        futures.add(byteFuture)
    }
    return futures
}
