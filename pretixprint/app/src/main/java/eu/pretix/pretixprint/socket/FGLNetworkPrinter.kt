package eu.pretix.pretixprint.socket

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min
import com.tom_roush.pdfbox.rendering.PDFRenderer as PDFBoxRenderer


class FGLNetworkPrinter(ip: String, port: Int, dpi: Int) : SocketNetworkPrinter(ip, port, dpi) {

    override fun convertPageToBytes(img: Bitmap): ByteArray {
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
