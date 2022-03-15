package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import androidx.fragment.app.Fragment
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.ui.SLCSSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min


class SLCS : StreamByteProtocol<Bitmap> {
    override val identifier = "SLCS"
    override val nameResource = R.string.protocol_slcs
    override val defaultDPI = 200
    override val demopage = "demopage_8in_3.25in.pdf"

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return true
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val ostream = ByteArrayOutputStream()
        val pixels = IntArray(img.width * img.height)
        img.getPixels(pixels, 0, img.width, 0, 0, img.width, img.height)

        // http://www.bixolon.com/upload/download/manual_slp-d42xx_slcs_english_rev_1_03.pdf

        ostream.write("CB\n".toByteArray())  // clear buffer
        ostream.write("SW${img.width}\n".toByteArray())  // set label width to input width
        ostream.write("SM0,0\n".toByteArray())  // clear margins
        ostream.write("LD".toByteArray())  // send iamge
        ostream.write(byteArrayOf(0, 0, 0, 0))  // x and y offset
        val bytewidth = img.width / 8
        ostream.write(byteArrayOf((bytewidth and 0xFF).toByte(), ((bytewidth shr 8) and 0xFF).toByte()))
        ostream.write(byteArrayOf((img.height and 0xFF).toByte(), ((img.height shr 8) and 0xFF).toByte()))
        val data = ByteArray(bytewidth * img.height)

        for (y in 0 until img.height) {
            for (xoffset in 0 until bytewidth) {
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

    override fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream, conf: Map<String, String>, type: String) {
        for (f in pages) {
            ostream.write(f.get())
            ostream.flush()
        }
        Thread.sleep(2000)
    }

    override fun createSettingsFragment(): SetupFragment? {
        return SLCSSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}