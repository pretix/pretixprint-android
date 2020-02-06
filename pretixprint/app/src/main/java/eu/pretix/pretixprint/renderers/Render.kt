package eu.pretix.pretixprint.renderers

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java8.util.concurrent.CompletableFuture
import java.io.File
import java.lang.RuntimeException

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
