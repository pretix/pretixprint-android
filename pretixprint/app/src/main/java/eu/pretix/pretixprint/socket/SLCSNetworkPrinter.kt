package eu.pretix.pretixprint.socket

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min
import com.tom_roush.pdfbox.rendering.PDFRenderer as PDFBoxRenderer


class SLCSNetworkPrinter(ip: String, port: Int, dpi: Int) : SocketNetworkPrinter(ip, port, dpi) {
    override fun convertPageToBytes(img: Bitmap): ByteArray {
        val ostream = ByteArrayOutputStream()
        val pixels = IntArray(img.width * img.height)
        img.getPixels(pixels, 0, img.width, 0, 0, img.width, img.height)

        ostream.write("CB\n".toByteArray())
        ostream.write("SM0,0\n".toByteArray())
        ostream.write("LD".toByteArray())
        ostream.write(byteArrayOf(0, 0, 0, 0))  // x and y offset
        val bytewidth = img.width / 8
        ostream.write(byteArrayOf((bytewidth and 0xFF).toByte(), ((bytewidth shr 8) and 0xFF).toByte()))
        ostream.write(byteArrayOf((img.height and 0xFF).toByte(), ((img.height shr 8) and 0xFF).toByte()))
        val data = ByteArray(bytewidth * img.height)

        for (y in 0..(img.height - 1)) {
            for (xoffset in 0..(bytewidth - 1)) {
                var col = 0
                for (j in 0..7) {
                    val px = pixels[min((xoffset * 8 + j) + img.width * y, pixels.size - 1)]
                    if ((px shr 24) and 0xff > 128 && ((px shr 16) and 0xff < 128 || (px shr 8) and 0xff < 128 || px and 0xff < 128)) {
                        // A > 128 && (R < 128 || G < 128 || B < 128)
                        col = col or (1 shl (7 - j))
                    }
                }
                data[y * bytewidth + xoffset] = col.toByte()
            }
        }
        ostream.write(data)
        ostream.write("\n".toByteArray())
        ostream.write("P1\n".toByteArray())
        ostream.flush()
        return ostream.toByteArray()
    }
}
