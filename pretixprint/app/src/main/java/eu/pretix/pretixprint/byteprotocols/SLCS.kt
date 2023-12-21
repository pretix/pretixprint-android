package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import android.util.Log
import androidx.fragment.app.Fragment
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.IMinInternalConnection
import eu.pretix.pretixprint.connections.SunmiInternalConnection
import eu.pretix.pretixprint.ui.SLCSSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
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
        return (type !is SunmiInternalConnection) and (type !is IMinInternalConnection)
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val ostream = ByteArrayOutputStream()
        val pixels = IntArray(img.width * img.height)
        img.getPixels(pixels, 0, img.width, 0, 0, img.width, img.height)

        // Spec: https://bixolon.com/_upload/manual/Manual_LabelPrinter_SLCS_english_V2[8].pdf

        // Clean initialization
        ostream.write("CB\n".toByteArray())  // clear buffer
        ostream.write("SW${img.width}\n".toByteArray())  // set label width to input width
        ostream.write("SM0,0\n".toByteArray())  // clear margins

        // Image printing
        ostream.write("LC".toByteArray())  // send compressed image
        ostream.write("R".toByteArray())  // compression type
        ostream.write(byteArrayOf(0))  // color
        ostream.write(byteArrayOf(0, 0, 0, 0))  // x and y offset
        val bytewidth = img.width / 8
        ostream.write(byteArrayOf((bytewidth and 0xFF).toByte(), ((bytewidth shr 8) and 0xFF).toByte()))
        ostream.write(byteArrayOf((img.height and 0xFF).toByte(), ((img.height shr 8) and 0xFF).toByte()))

        val imagedata = ByteArrayOutputStream()
        val row = ByteArray(bytewidth)
        for (y in 0 until img.height) {
            /*
            Build a row of bytes for each row, each byte represents 8 pixels
             */
            for (xoffset in 0 until bytewidth) {
                var col = 0
                for (j in 0..7) {
                    val px = pixels[min((xoffset * 8 + j) + img.width * y, pixels.size - 1)]
                    if ((px shr 24) and 0xff > 128 && ((px shr 16) and 0xff < 128 || (px shr 8) and 0xff < 128 || px and 0xff < 128)) {
                        // A > 128 && (R < 128 || G < 128 || B < 128)
                        col = col or (1 shl (7 - j))
                    }
                }
                row[xoffset] = col.toByte()
            }

            /*
            Compress with run-length encoding, but only for 0xFF and 0x00, as per bixolon spec:
            "This is the algorithm to compress the continuous data.
            The compression is applied to 0x00 & 0xff data but not the others.
            0xff 0x04 data is created if 0xff is repeated four times like 0x00 0x00 0x00 0x00.
            In the same way, 0x00 0x04 is created by four times repeats of 0x00 such as 0x00 0x00 0x00 0x00."
             */
            var count = 0
            for ((i, byte) in row.withIndex()) {
                if (byte != 0x00.toByte() && byte != 0xFF.toByte()) {
                    imagedata.write(byte.toInt())
                    count = 0
                } else {
                    if (count >= 253 || i + 1 >= row.size || row[i + 1] != byte) {
                        imagedata.write(byte.toInt())
                        imagedata.write(count + 1)
                        count = 0
                    } else {
                        count += 1
                    }
                }
            }
        }
        ostream.write(imagedata.toByteArray())
        ostream.write("\n".toByteArray())

        // Print one label
        ostream.write("P1\n".toByteArray())
        ostream.flush()
        return ostream.toByteArray()
    }

    override fun send(
        pages: List<CompletableFuture<ByteArray>>,
        istream: InputStream,
        ostream: OutputStream,
        conf: Map<String, String>,
        type: String,
        waitAfterPage: Long
    ) {
        for (f in pages) {
            Log.i("PrintService", "[$type] Waiting for page to be converted")
            val page = f.get(60, TimeUnit.SECONDS)
            Log.i("PrintService", "[$type] Page ready, sending page")
            ostream.write(page)
            ostream.flush()
            Log.i("PrintService", "[$type] Page sent")
        }
        Log.i("PrintService", "[$type] Job done, sleep")
        Thread.sleep(waitAfterPage)
    }

    override fun createSettingsFragment(): SetupFragment? {
        return SLCSSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}