package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.ui.ESCLabelSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.awt.color.ICC_ColorSpace
import java.awt.color.ICC_Profile
import java.awt.image.BufferedImage
import java.awt.image.ColorConvertOp
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO


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

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String, context: Context): ByteArray {
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
        ostream.write("^XA ^IDR:*.*^FS^XZ".toByteArray())

        // Register image in printer in volatile memory as a PNG
        val stream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.PNG, 100, stream)

        val iccConvert = true

        if (iccConvert) {
            System.setProperty("java.iccprofile.path", context.cacheDir.path)
            for (filename in listOf("CIEXYZ.pf", "GRAY.pf", "LINEAR_RGB.pf", "PYCC.pf", "sRGB.pf")) {
                val file = File(context.cacheDir, filename)
                if (file.exists()) {
                    file.delete()
                }
                val asset = context.assets.open("icc/$filename")
                val output = FileOutputStream(file)

                val buffer = ByteArray(1024)
                var size = asset.read(buffer)
                while (size != -1) {
                    output.write(buffer, 0, size)
                    size = asset.read(buffer)
                }
                asset.close()
            }
            val iccColorSpace = ICC_ColorSpace(
                ICC_Profile.getInstance(context.resources.assets.open("icc/EB260 Normal - Epson C6000 series [MK].icm"))
            )
            val colorConvertOp = ColorConvertOp(iccColorSpace, null)
            val bufferedImageIn = ImageIO.read(ByteArrayInputStream(stream.toByteArray()))
            val bufferedImageOut = BufferedImage(bufferedImageIn.width, bufferedImageIn.height, bufferedImageIn.type)
            colorConvertOp.filter(bufferedImageIn, bufferedImageOut)
            bufferedImageIn.flush()
            ImageIO.write(bufferedImageOut, "png", stream)
        }

        stream.flush()
        val imageHexString = stream.toByteArray().toHex()
        ostream.write("~DYR:IMAGE.PNG,P,P,${imageHexString.length/2},,${imageHexString}".toByteArray())
        img.recycle()

        // Create label and print previously transferred image
        ostream.write("^XA".toByteArray())
        ostream.write("^FO0,0^IMR:IMAGE.PNG^FS".toByteArray())
        ostream.write("^XZ".toByteArray())

        // Delete the image from the printer again
        ostream.write("^XA ^IDR:*.*^FS^XZ".toByteArray())

        ostream.flush()
        return ostream.toByteArray()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    override fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream, conf: Map<String, String>, type: String) {
        for (f in pages) {
            Log.i("PrintService", "Waiting for page to be converted")
            val page = f.get(60, TimeUnit.SECONDS)
            Log.i("PrintService", "Page ready, sending page")
            ostream.write(page)
            ostream.flush()
            Log.i("PrintService", "Page sent")
        }
        Log.i("PrintService", "Job done, sleep")
        Thread.sleep(2000)
    }

    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02X".format(eachByte) }
}