package eu.pretix.pretixprint.fgl

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import kotlin.math.min


class FGLNetworkPrinter(var ip: String, var port: Int) {

    fun printPDF(pdfstream: InputStream) {
        val serverAddr = InetAddress.getByName(ip)
        val socket = Socket(serverAddr, port)
        val ostream = BufferedWriter(OutputStreamWriter(socket.getOutputStream()));

        val doc = PDDocument.load(pdfstream)
        val renderer = PDFRenderer(doc)
        for (page in 0..(doc.pages.count - 1)) {
            printPage(renderer, page, ostream)
            ostream.flush()
        }
        ostream.close()
        socket.close()
    }

    private fun printPage(doc: PDFRenderer, page: Int, ostream: BufferedWriter) {
        // TODO: configurable dpi
        val img = doc.renderImageWithDPI(page, 200.0F)
        val pixels = IntArray(img.width * img.height)
        img.getPixels(pixels, 0, img.width, 0, 0, img.width, img.height)

        for (yoffset in 0..(img.height - 1) step 8) {
            System.out.println("<RC${yoffset},0><G${img.width}>")
            val row = IntArray(img.width)
            for (x in 0..(img.width - 1)) {
                var col = 0
                for (j in 0..7) {
                    //val px = pixels[x + img.width * min(yoffset + j, img.height - 1)]
                    val px = img.getPixel(x, min(yoffset + j, img.height - 1))
                    val A = (px shr 24) and 0xff
                    val R = (px shr 16) and 0xff
                    val G = (px shr 8) and 0xff
                    val B = px and 0xff
                    if (A > 128 && (R < 128 || G < 128 || B < 128)) {
                        col = col or (1 shl (7 - j))
                    }
                }
                row[x] = col
            }
            ostream.write("<RC${yoffset},0><G${img.width}>")
            for (c in row) {
                ostream.write(c)
            }
        }
        ostream.write("<p>\n")
        System.out.println("<RC>ALL WRITTEN")
    }
}
