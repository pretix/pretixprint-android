package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import android.graphics.Matrix
import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.escpos.EscPosConst
import com.github.anastaciocintra.escpos.image.BitonalThreshold
import com.github.anastaciocintra.escpos.image.CoffeeImage
import com.github.anastaciocintra.escpos.image.EscPosImage
import com.github.anastaciocintra.escpos.image.GraphicsImageWrapper
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.ui.GraphicESCPOSSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream


class GraphicESCPOS : StreamByteProtocol<Bitmap> {
    override val identifier = "GraphicESCPOS"
    override val nameResource = R.string.protocol_escpos
    override val defaultDPI = 203
    override val demopage = "demopage_8in_3.25in.pdf"

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val ostream = ByteArrayOutputStream()
        val escpos = EscPos(ostream)

        val targetWidthMM = Integer.valueOf(conf.get("hardware_${type}printer_maxwidth")
                ?: "100000").toFloat()
        val dpi = Integer.valueOf(conf.get("hardware_${type}printer_dpi")
                ?: defaultDPI.toString()).toFloat()
        val targetWidth = (targetWidthMM * 0.0393701 * dpi).toInt()

        var rotated = if (conf.get("hardware_${type}printer_rotate90") == "true") {
            val matrix = Matrix()
            matrix.postRotate(90f)
            Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        } else {
            img
        }

        val scaled = if (rotated.width > targetWidth) {
            val targetHeight = (targetWidth.toFloat() / rotated.width.toFloat() * rotated.height.toFloat()).toInt()
            Bitmap.createScaledBitmap(rotated, targetWidth, targetHeight, true)
        } else {
            rotated
        }

        var yoff = 0
        val maxheight = 200  // printer will crash if image is larger than its buffer, so we split it. 200 is a guess, probably the limit is around 250.
        while (yoff < scaled.height) {
            val cropped = Bitmap.createBitmap(scaled, 0, yoff, scaled.width, Math.min(scaled.height - yoff, maxheight), null, false)
            val algorithm = BitonalThreshold(127)
            val escposImage = EscPosImage(CoffeeImageAndroidImpl(cropped), algorithm)
            val imageWrapper = GraphicsImageWrapper()
            imageWrapper.setGraphicsImageBxBy(GraphicsImageWrapper.GraphicsImageBxBy.Normal_Default)
            escpos.write(imageWrapper, escposImage)
            yoff += maxheight
        }


        escpos.feed(5)
        escpos.cut(EscPos.CutMode.PART)
        escpos.close()
        ostream.flush()
        return ostream.toByteArray()
    }

    override fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream) {
        for (f in pages) {
            ostream.write(f.get())
            ostream.flush()
        }
        Thread.sleep(2000)  // todo: check if necessary
    }

    override fun createSettingsFragment(): SetupFragment? {
        return GraphicESCPOSSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}

class CoffeeImageAndroidImpl(private val bitmap: Bitmap) : CoffeeImage {
    override fun getWidth(): Int {
        return bitmap.width
    }

    override fun getHeight(): Int {
        return bitmap.height
    }

    override fun getSubimage(x: Int, y: Int, w: Int, h: Int): CoffeeImage {
        return CoffeeImageAndroidImpl(Bitmap.createBitmap(bitmap, x, y, w, h))
    }

    override fun getRGB(x: Int, y: Int): Int {
        return bitmap.getPixel(x, y)
    }

}