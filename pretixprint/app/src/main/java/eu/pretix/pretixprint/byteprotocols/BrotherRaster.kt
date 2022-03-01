package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import android.graphics.Matrix
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.ui.BrotherRasterSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min

// https://download.brother.com/welcome/docp100278/cv_ql800_eng_raster_100.pdf
// https://download.brother.com/welcome/docp100366/cv_ql1100_eng_raster_100.pdf
class BrotherRaster : StreamByteProtocol<Bitmap> {
    override val identifier = "BrotherRaster"
    override val nameResource = R.string.protocol_brother
    override val defaultDPI = 300
    override val demopage = "demopage_8in_3.25in.pdf"

    // Label data list extracted from
    // https://download.brother.com/welcome/docp100366/cv_ql1100_eng_raster_100.pdf
    enum class Label(val id: Int, val width: Int, val height: Int, val continuous: Boolean, val printableWidth: Int, val printableHeight: Int) {
        c12mm(257, 12, 0, true, 106, 0),
        c29mm(258, 29, 0, true, 306, 0),
        c38mm(264, 38, 0, true, 413, 0),
        c50mm(262, 50, 0, true, 554, 0),
        c54mm(261, 54, 0, true, 590, 0),
        c62mm(259, 62, 0, true, 696, 0),
        c102mm(260, 102, 0, true, 1164, 0),
        c103mm(265, 103, 0, true, 1200, 0),
        d17x54(269, 17, 54, false, 165, 566),
        d17x87(270, 17, 87, false, 165, 956),
        d23x23(370, 23, 23, false, 236, 202),
        d29x42(358, 29, 42, false, 306, 425),
        d29x90(271, 29, 90, false, 306, 991),
        d38x90(272, 38, 90, false, 413, 991),
        d39x48(367, 39, 48, false, 425, 495),
        d52x29(374, 52, 29, false, 578, 271),
        d60x86(383, 60, 86, false, 672, 954),
        d62x29(274, 62, 29, false, 696, 271),
        d62x100(275, 62, 100, false, 696, 1109),
        d102x51(365, 102, 51, false, 1164, 526),
        d102x152(366, 102, 152, false, 1164, 1660),
        d103x164(385, 103, 164, false, 1200, 1822);

        fun size() : String {
            if (this.continuous) {
                return "${this.width} mm"
            }
            return "${this.width} mm × ${this.height} mm"
        }

        override fun toString(): String {
            return size()
        }
    }

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val ostream = ByteArrayOutputStream()

        val label = Label.values().find { it.name == conf.get("hardware_${type}printer_label") }!!
        val targetWidth = label.printableWidth

        val matrix = Matrix()
        var imgW = img.width
        var imgH = img.height
        // rotate
        if (conf.get("hardware_${type}printer_rotate90") == "true") {
            matrix.setRotate(90f)
            imgH = img.width
            imgW = img.height
        }
        // resize
        var targetHeight = imgH
        if (imgW > targetWidth) {
            targetHeight = (targetWidth.toFloat() / imgW.toFloat() * imgH.toFloat()).toInt()
            val sx: Float = targetWidth / imgW.toFloat()
            val sy: Float = targetHeight / imgH.toFloat()
            matrix.postScale(sx, sy)
        }
        // flip
        matrix.postScale(-1f, 1f, targetWidth / 2f, targetHeight / 2f)
        val scaled = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)

        val maxHeight = scaled.height.coerceAtLeast(label.printableHeight)

        ostream.write(byteArrayOf(0x1B, 'i'.code.toByte(), 'a'.code.toByte(), 0x01)) // Mode: raster
        ostream.write(byteArrayOf(0x1B, 'i'.code.toByte(), 'S'.code.toByte())) // request statusinfo

        ostream.write(byteArrayOf(
            0x1B, 'i'.code.toByte(), 'z'.code.toByte(), // Media information
            0xC6.toByte(),
            if (label.continuous) 0x0A else 0x0B,
            label.width.toByte(),
            if (label.continuous) 0x00 else label.height.toByte(),
            maxHeight.toByte(),
            (maxHeight shr 8).toByte(),
            (maxHeight shr 16).toByte(),
            (maxHeight shr 24).toByte(),
            if (previousPage != null) 0x01 else 0x00,
            0x00
        ))
        ostream.write(byteArrayOf(0x1B, 'i'.code.toByte(), 'K'.code.toByte(), 0x08)) // Cut at end, 300dpi
        ostream.write(byteArrayOf(0x1B, 'i'.code.toByte(), 'M'.code.toByte(), 0x40)) // Auto Cut
        ostream.write(byteArrayOf(0x1B, 'i'.code.toByte(), 'A'.code.toByte(), 0x01)) // Cut after every label
        if (label.continuous) {
            ostream.write(byteArrayOf(0x1B, 'i'.code.toByte(), 'd'.code.toByte(), 0x23, 0x00)) // Margins: 3mm
        } else {
            ostream.write(byteArrayOf(0x1B, 'i'.code.toByte(), 'd'.code.toByte(), 0x00, 0x00)) // Margins: 0mm
        }

        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        val bytewidth = scaled.width / 8
        for (y in 0 until maxHeight) {
            val row = ByteArray(bytewidth)
            for (xoffset in 0 until bytewidth) {
                var col = 0
                // check for overprinting (more height than scaled image)
                if (y <= scaled.height) {
                    for (j in 0..7) {
                        val px = pixels[min((xoffset * 8 + j) + scaled.width * y, pixels.size - 1)]
                        if ((px shr 24) and 0xff > 128 && ((px shr 16) and 0xff < 128 || (px shr 8) and 0xff < 128 || px and 0xff < 128)) {
                            // A > 128 && (R < 128 || G < 128 || B < 128)
                            col = col or (1 shl (7 - j))
                        }
                    }
                } else {
                    // fill rest of paper with white nothingness
                    col = 0
                }
                row[xoffset] = col.toByte()
            }

            ostream.write('g'.code)
            ostream.write(0x00) // default color
            val len = min(90, row.size)
            ostream.write(len)
            ostream.write(row, 0, len)
            ostream.flush()
        }

        // Print command with feeding
        if (isLastPage) {
            ostream.write(0x1A)
        } else {
            ostream.write(0x0C)
        }

        /*
        val sb = StringBuffer(ostream.size()*3)
        for (b in ostream.toByteArray()) {
            var i = b.toInt() and 0xFF
            sb.append(" " + "0123456789ABCDEF"[i shr 4] + "0123456789ABCDEF"[i and 0x0F])
        }
        */

        return ostream.toByteArray()
    }

    override fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream, conf: Map<String, String>, type: String) {
        // Invalidate
        for (i in 0 until 400) {
            ostream.write(0x00)
        }
        ostream.flush()

        // Initialize
        ostream.write(byteArrayOf(0x1B, '@'.code.toByte()))

        for (f in pages) {
            ostream.write(f.get())
            ostream.flush()
        }

        val wap = Integer.valueOf(conf.get("hardware_${type}printer_waitafterpage") ?: "2000")
        Thread.sleep(wap.toLong())
    }

    override fun createSettingsFragment(): SetupFragment? {
        return BrotherRasterSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}