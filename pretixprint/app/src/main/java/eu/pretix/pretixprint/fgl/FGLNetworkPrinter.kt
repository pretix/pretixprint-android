package eu.pretix.pretixprint.fgl

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer as PDFBoxRenderer
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.Socket
import kotlin.math.min
import java.io.OutputStream
import java.nio.ByteBuffer


class FGLNetworkPrinter(var ip: String, var port: Int) {

    fun printPDF(file: File) {
        val serverAddr = InetAddress.getByName(ip)
        val dpi = 200.0F  // TODO: configurable
        val pages = mutableListOf<ByteArray>()
        if (Build.VERSION.SDK_INT >= 21) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(fd)
            val pageCount = renderer.pageCount
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val img = Bitmap.createBitmap((page.width / 72 * dpi).toInt(), (page.height / 72 * dpi).toInt(), Bitmap.Config.ARGB_8888)
                page.render(img, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()
                pages.add(convertPageToFGL(img))
            }
            renderer.close()
            fd.close()
        } else {
            val doc = PDDocument.load(file.inputStream())
            val renderer = PDFBoxRenderer(doc)
            for (page in 0..(doc.pages.count - 1)) {
                val img = renderer.renderImageWithDPI(page, dpi)
                pages.add(convertPageToFGL(img))
            }
        }
        val socket = Socket(serverAddr, port)
        val ostream = socket.getOutputStream()
        val istream = socket.getInputStream()
        try {
            for (p in pages) {
                ostream.write(p)
                ostream.flush()
            }
            Thread.sleep(2000)
        } finally {
            istream.close()
            ostream.close()
            socket.close()
        }
    }

    private fun convertPageToFGL(img: Bitmap): ByteArray {
        val ostream = ByteArrayOutputStream()
        val pixels = IntArray(img.width * img.height)
        img.getPixels(pixels, 0, img.width, 0, 0, img.width, img.height)

        ostream.write("<CB>".toByteArray())
        for (yoffset in 0..(img.height - 1) step 8) {
            val row = ByteArray(img.width)
            for (x in 0..(img.width - 1)) {
                var col = 0
                for (j in 0..7) {
                    val px = pixels[min(x + img.width * (yoffset + j), pixels.size - 1)]
                    if ((px shr 24) and 0xff > 128 && ((px shr 16) and 0xff < 128 || (px shr 8) and 0xff < 128 || px and 0xff < 128)) {
                        // A > 128 && (R < 128 || G < 128 || B < 128)
                        col = col or (1 shl (7 - j))
                    }
                }
                row[x] = col.toByte()
            }
            ostream.write("<RC${yoffset},0><G${img.width}>".toByteArray())
            ostream.write(row)
            ostream.write("\n".toByteArray())
            ostream.flush()
        }
        ostream.write("<p>\n".toByteArray())
        return ostream.toByteArray()
    }
}
