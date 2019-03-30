package eu.pretix.pretixprint.socket

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java8.util.concurrent.CompletableFuture
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Executors
import com.tom_roush.pdfbox.rendering.PDFRenderer as PDFBoxRenderer

abstract class SocketNetworkPrinter(var ip: String, var port: Int, var dpi: Int) {
    protected var threadPool = Executors.newCachedThreadPool()

    abstract fun convertPageToBytes(img: Bitmap, isLastPage: Boolean): ByteArray

    fun getCooldown(): Long {
        return 2000;
    }

    fun renderPages(file: File, d: Float): List<CompletableFuture<ByteArray>> {
        val pagenum = PDDocument.load(file.inputStream()).pages.count
        val futures = mutableListOf<CompletableFuture<ByteArray>>()
        for (i in 0 until pagenum) {
            val future = CompletableFuture<ByteArray>()
            futures.add(future)
            threadPool.submit {
                if (Build.VERSION.SDK_INT >= 21) {
                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(fd)
                    val pageCount = renderer.pageCount
                    val page = renderer.openPage(i)
                    val img = Bitmap.createBitmap((page.width / 72.0 * d).toInt(), (page.height / 72.0 * d).toInt(), Bitmap.Config.ARGB_8888)
                    page.render(img, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()
                    future.complete(convertPageToBytes(img, i == pageCount - 1))
                    renderer.close()
                    fd.close()
                } else {
                    val doc = PDDocument.load(file.inputStream())
                    val renderer = PDFBoxRenderer(doc)
                    for (page in 0..(doc.pages.count - 1)) {
                        val img = renderer.renderImageWithDPI(page, d)
                        future.complete(convertPageToBytes(img, page == doc.pages.count - 1))
                    }
                }
            }
        }
        return futures
    }

    open fun printPDF(file: File) {
        val serverAddr = InetAddress.getByName(ip)
        var d = dpi.toFloat()
        if (d < 1) {
            d = 200f  // Set default
        }
        val pages = renderPages(file, d)
        val socket = Socket(serverAddr, port)
        val ostream = socket.getOutputStream()
        val istream = socket.getInputStream()
        try {
            for (f in pages) {
                ostream.write(f.get())
                ostream.flush()
            }
            Thread.sleep(getCooldown())
        } finally {
            istream.close()
            ostream.close()
            socket.close()
        }
    }
}
