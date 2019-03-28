package eu.pretix.pretixprint.socket

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer as PDFBoxRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.Charset

abstract class SocketNetworkPrinter(var ip: String, var port: Int, var dpi: Int) {

    abstract fun convertPageToBytes(img: Bitmap, isLastPage: Boolean): ByteArray

    fun getCooldown(): Long {
        return 2000;
    }

    fun printPDF(file: File) {
        val serverAddr = InetAddress.getByName(ip)
        var d = dpi.toFloat()
        if (d < 1) {
            d = 200f  // Set default
        }
        val pages = mutableListOf<ByteArray>()
        if (Build.VERSION.SDK_INT >= 21) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(fd)
            val pageCount = renderer.pageCount
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val img = Bitmap.createBitmap((page.width / 72.0 * d).toInt(), (page.height / 72.0 * d).toInt(), Bitmap.Config.ARGB_8888)
                page.render(img, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()
                pages.add(convertPageToBytes(img, i == pageCount - 1))
            }
            renderer.close()
            fd.close()
        } else {
            val doc = PDDocument.load(file.inputStream())
            val renderer = PDFBoxRenderer(doc)
            for (page in 0..(doc.pages.count - 1)) {
                val img = renderer.renderImageWithDPI(page, d)
                pages.add(convertPageToBytes(img, page == doc.pages.count - 1))
            }
        }
        val socket = Socket(serverAddr, port)
        val ostream = socket.getOutputStream()
        val istream = socket.getInputStream()
        try {
            for (p in pages) {
                ostream.write(p)
                ostream.flush()
                while (istream.read() != 6) {
                    Log.d("Foobar", "Sleeping")
                    Thread.sleep(100)
                }
                Log.d("Foobar", "Out")
            }
            Thread.sleep(getCooldown())
        } finally {
            istream.close()
            ostream.close()
            socket.close()
        }
    }
}
