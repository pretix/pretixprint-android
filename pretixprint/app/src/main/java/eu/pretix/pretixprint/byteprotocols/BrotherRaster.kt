package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.connections.USBConnection
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
    override val defaultDPI = 600
    override val demopage = "demopage_8in_3.25in.pdf"

    /**
     * Label data list extracted from 2.3.2 Page size in the following documents:
     * @url https://download.brother.com/welcome/docp100278/cv_ql800_eng_raster_100.pdf
     * @url https://download.brother.com/welcome/docp100366/cv_ql1100_eng_raster_100.pdf
     *
     * @param width the paper/label width in mm. See 2.3.2 Page size, column Tape/Label Size
     * @param height the paper/label height in mm. See 2.3.2 Page size, column Tape/Label Size. 0 for endless paper
     * @param continuous does the roll contain single labels (false) or is endless paper (true)?
     * @param printableWidth in dots. See 2.3.2 Page size, column 3 Print area width
     * @param printableHeight in dots. See 2.3.2 Page size, column 4 Print area length. 0 for endless paper
     * @param twoColor some labels support red/black
     */
    enum class Label(val id: Int, val width: Int, val height: Int, val continuous: Boolean, val printableWidth: Int, val printableHeight: Int, val twoColor: Boolean = false) {
        c12mm(257, 12, 0, true, 106, 0),
        c29mm(258, 29, 0, true, 306, 0),
        c38mm(264, 38, 0, true, 413, 0),
        c50mm(262, 50, 0, true, 554, 0),
        c54mm(261, 54, 0, true, 590, 0),
        c62mm(259, 62, 0, true, 696, 0),
        c62mm_rb(259, 62, 0, true, 696, 0, true),
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

        fun size(): String {
            return if (this.continuous) "${this.width} mm" else "${this.width} mm × ${this.height} mm"
        }

        override fun toString(): String {
            var n = size()
            if (this.twoColor) {
                n += " (red/black)"
            }
            return n
        }
    }

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is NetworkConnection || type is USBConnection
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val ostream = ByteArrayOutputStream()

        val label = Label.values().find { it.name == conf.get("hardware_${type}printer_label") }!!
        val targetWidth = label.printableWidth

        val matrix = Matrix()
        var imgW = img.width
        var imgH = img.height
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

        // Note: printing twoColor labels doesn't support the quality flag
        var mediaFlag = 0x86
        if (!label.twoColor && conf.get("hardware_${type}printer_quality") == "true") {
            mediaFlag = 0xC6
        }
        val rasterHeight = maxHeight * (if(label.twoColor) 2 else 1)
        ostream.write(byteArrayOf(
            0x1B, 'i'.code.toByte(), 'z'.code.toByte(), // Media information
            mediaFlag.toByte(),
            if (label.continuous) 0x0A else 0x0B,
            label.width.toByte(),
            if (label.continuous) 0x00 else label.height.toByte(),
            rasterHeight.toByte(),
            (rasterHeight shr 8).toByte(),
            (rasterHeight shr 16).toByte(),
            (rasterHeight shr 24).toByte(),
            if (previousPage != null) 0x01 else 0x00,
            0x00
        ))
        ostream.write(byteArrayOf(0x1B, 'i'.code.toByte(), 'K'.code.toByte(), if (label.twoColor) 0x09 else 0x08)) // Cut at end, 300dpi
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
        val rasterDataWidth = if (scaled.width <= Label.c62mm.printableWidth) 90 else 162
        for (y in 0 until maxHeight) {
            // for usb to work, raster width has always be 90 bytes
            val row = ByteArray(rasterDataWidth)
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

            ostream.write(if (label.twoColor) 'w'.code else 'g'.code)
            ostream.write(if (label.twoColor) 0x01 else 0x00) // default color
            ostream.write(rasterDataWidth)
            ostream.write(row, 0, rasterDataWidth)

            // HACK for printing usual black/white on red/black labels
            if (label.twoColor) {
                ostream.write('w'.code)
                ostream.write(0x02) // second color
                ostream.write(rasterDataWidth)
                ostream.write(ByteArray(rasterDataWidth), 0, rasterDataWidth) // simply nothing
            }
        }

        // Print command with feeding
        if (isLastPage) {
            ostream.write(0x1A)
        } else {
            ostream.write(0x0C)
        }

        return ostream.toByteArray()
    }

    override fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream, conf: Map<String, String>, type: String) {
        // Invalidate: 200 bytes full of 0x00
        ostream.write(byteArrayOf(0x1B, 'i'.code.toByte(), 'a'.code.toByte(), 0x01) + ByteArray(200)) // Mode: raster

        // Initialize
        var prefix = byteArrayOf(0x1B, '@'.code.toByte())

        for (f in pages) {
            Log.i("PrintService", "[$type] Waiting for page to be converted")
            val page = f.get()
            Log.i("PrintService", "[$type] Page ready, sending page")
            ostream.write(prefix + page)
            ostream.flush()
            // prefix is only needed for the first page
            prefix = byteArrayOf()
            Log.i("PrintService", "[$type] Page sent")
        }
        Log.i("PrintService", "[$type] Job done, sleep")
        Thread.sleep(2000)
    }

    override fun createSettingsFragment(): SetupFragment? {
        return BrotherRasterSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}