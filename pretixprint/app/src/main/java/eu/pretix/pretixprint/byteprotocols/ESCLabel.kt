package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import android.util.Log
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.ui.ESCLabelSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit


class ESCLabel : StreamByteProtocol<Bitmap> {
    override val identifier = "ESCLabel"
    override val nameResource = R.string.protocol_esclabel
    override val defaultDPI = 600
    override val demopage = "demopage_cr80.pdf"

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is NetworkConnection
    }

    override fun createSettingsFragment(): SetupFragment? {
        return ESCLabelSettingsFragment()
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val ostream = ByteArrayOutputStream()
        val pixels = IntArray(img.width * img.height)
        img.getPixels(pixels, 0, img.width, 0, 0, img.width, img.height)

        // Spec: https://files.support.epson.com/pdf/pos/bulk/esclabel_crg_en_07.pdf

        // Section 2.8.1: Registering a Graphic in a Printer and Printing It
        // We are not using the ^GF command to embed the graphic directly into a field and printing
        // it (as shown in section 2.8.2), since the ^GF command apparently only supports b/w
        // graphics data.
        // The downside of the ~DY command however is, that the graphic has to be transferred and
        // be saved on the device before printing it.
        //
        // Command notes:
        // ~DY: Store file to printer memory
        //      Contrary to the documentation, the file name should also contain the extension

        // Delete all files volatile memory
        ostream.write("^XA^IDR:*.*^FS^XZ".toByteArray())

        // Register image in printer in volatile memory as a PNG
        val stream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.PNG, 100, stream)
        ostream.write("~DYR:IMAGE.PNG,B,P,${stream.size()},0,".toByteArray())
        stream.writeTo(ostream)
        img.recycle()

        // Create label and print previously transferred image
        ostream.write("^XA".toByteArray())
        ostream.write("^ILR:IMAGE.PNG^FS".toByteArray())
        ostream.write("^XZ".toByteArray())

        // Delete the image from the printer again
        ostream.write("^XA^IDR:*.*^FS^XZ".toByteArray())

        ostream.flush()
        return ostream.toByteArray()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    override fun send(
        pages: List<CompletableFuture<ByteArray>>,
        pagegroups: List<Int>,
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

    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02X".format(eachByte) }
}