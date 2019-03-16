package eu.pretix.pretixprint.socket

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min
import com.tom_roush.pdfbox.rendering.PDFRenderer as PDFBoxRenderer


class FGLNetworkPrinter(ip: String, port: Int, dpi: Int) : SocketNetworkPrinter(ip, port, dpi) {

    override fun convertPageToBytes(img: Bitmap): ByteArray {
        val ostream = ByteArrayOutputStream()
        val w = img.width
        val h = img.height
        val stepsize = 100
        val pixels = IntArray(w * h)
        img.getPixels(pixels, 0, w, 0, 0, w, h)

        //ostream.write("<CB>".toByteArray())
        for (yoffset in 0..(h - 1) step 8) {
            for (xoffset in 0..(w - 1) step stepsize) {
                val row = ByteArray(stepsize)
                var any = false
                for (x in xoffset..min(xoffset + stepsize - 1, w - 1)) {
                    var col = 0
                    for (j in 0..7) {
                        val px = pixels[min(x + w * (yoffset + j), pixels.size - 1)]
                        if ((px shr 24) and 0xff > 128 && ((px shr 16) and 0xff < 128 || (px shr 8) and 0xff < 128 || px and 0xff < 128)) {
                            // A > 128 && (R < 128 || G < 128 || B < 128)
                            col = col or (1 shl (7 - j))
                        }
                    }
                    row[x - xoffset] = col.toByte()
                    if (col > 0)
                        any = true
                }
                if (any) {
                    ostream.write("<RC${yoffset},${xoffset}><G${stepsize}>".toByteArray())
                    ostream.write(row)
                    ostream.write("\n".toByteArray())
                }
            }
        }
        ostream.write("<p>\n".toByteArray())
        return ostream.toByteArray()
    }
}
