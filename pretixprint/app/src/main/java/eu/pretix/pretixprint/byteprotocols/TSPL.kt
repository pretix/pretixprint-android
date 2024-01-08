package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.Sensor
import eu.pretix.pretixprint.connections.BluetoothConnection
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.USBConnection
import eu.pretix.pretixprint.ui.SetupFragment
import eu.pretix.pretixprint.ui.TSPLSettingsFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit


class TSPL : StreamByteProtocol<Bitmap> {
    override val identifier = "TSPL"
    override val nameResource = R.string.protocol_tspl
    override val demopage = "demopage_8in_3.25in.pdf"

    private var outStream: OutputStream? = null

    override val defaultDPI: Int = 203
    val defaultMaxWidth: Double = 82.55 // mm (82.55mm = 3")
    val defaultMaxLength: Double = 203.2 // mm (203.2mm = 8")
    val defaultSpeed: Int = 2 // inch/sec (2 is supported by most TSC printers)
    val defaultDensity: Int = 8 // 1-15 (density = print temperature)
    val defaultSensor: Int = Sensor.sGap.sensor
    val defaultSensorHeight: Double = 3.0 // height of gap/mark in mm
    val defaultSensorOffset: Double = 1.5 // offset after mark

    val ditheringThreshold: Int = 128

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt" // allow both ticket and badge printing
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is BluetoothConnection || type is USBConnection
        // in theory this should work for network, too. However, I don't have a wifi/ethernet TSPL-printer to test
    }

    override fun createSettingsFragment(): SetupFragment {
        return TSPLSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val stream = ByteArrayOutputStream()

        // scale down to fit target medium
        val dpi = conf.get("hardware_${type}printer_dpi")?.toInt() ?: this.defaultDPI
        val maxWidthMM = conf.get("hardware_${type}printer_maxwidth")?.toInt()
                ?: this.defaultMaxWidth
        val targetWidth = (maxWidthMM.toFloat() * 0.0393701 * dpi).toInt() // in dots
        val scaledImg = if (img.width > targetWidth) {
            val targetHeight = (targetWidth.toFloat() / img.width.toFloat() * img.height.toFloat()).toInt()
            Bitmap.createScaledBitmap(img, targetWidth, targetHeight, true)
        } else {
            img
        }

        // BITMAP start command
        val mode: String = Integer.toString(0) // print mode (0 = override pixel, 1 = OR, 1 = XOR)
        val width: Int = scaledImg.width
        val widthInBytes: Int = (width + 7) / 8
        val height: Int = scaledImg.height
        val xOffset = 0
        val yOffset = 0
        stream.write("BITMAP, $xOffset, $yOffset, $widthInBytes, $height, $mode,".toByteArray()) // as tspl takes binary bitmap, width is in bytes, but byte-height equates to dot-height

        // byte array of binary b/w image
        val imgStream = ByteArray(widthInBytes * height)
        // array storing errors for dithering
        val quantizationErrors = Array(width) { IntArray(height) }

        // set all pixels to black
        var y = 0
        while (y < height * widthInBytes) {
            imgStream[y] = 0
            ++y
        }

        // iterate all pixels and convert into binary
        // applies floyd-steinberg dithering
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = scaledImg.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val alpha = Color.alpha(pixel)
                // convert pixel to grayscale with luminosity method and apply alpha channel
                val grayScale = ((0.21 * red.toDouble() + 0.72 * green.toDouble() + 0.07 * blue.toDouble()) * alpha.toDouble() / 255).toInt()

                // apply errors from previous pixels
                val oldPixel = grayScale + quantizationErrors[x][y]

                // decide whether this pixel is black or white
                var newPixel = 0 // BLACK by default
                if (oldPixel >= this.ditheringThreshold) {
                    // WHITE new pixel
                    newPixel = 255
                    // set pixel to white/transparent/paper color (1 / true)
                    val byteIndex: Int = y * widthInBytes + (x / 8)
                    val bitIndex: Int = x % 8
                    val oldByte = imgStream[byteIndex]
                    val pixelBitInt = 128 shr bitIndex // 128 = 10000000 shiftRight bit
                    val newByte: Byte = (oldByte.toInt() xor pixelBitInt).toByte()
                    imgStream[byteIndex] = newByte
                }

                // error = effect on neighboring (following) pixels
                val pixelError = oldPixel - newPixel

                // add errors to next pixels
                // right pixel
                if (x < width - 1) {
                    quantizationErrors[x + 1][y] += pixelError * 7 / 16
                }
                // bottom left pixel
                if (x > 0 && y < height - 1) {
                    quantizationErrors[x - 1][y + 1] += pixelError * 3 / 16
                }
                // bottom pixel
                if (y < height - 1) {
                    quantizationErrors[x][y + 1] += pixelError * 5 / 16
                }
                // bottom right pixel
                if (x < width - 1 && y < height - 1) {
                    quantizationErrors[x + 1][y + 1] += pixelError * 1 / 16
                }
            }
        }

        // write into result stream
        stream.write(imgStream)
        stream.write("\r\n".toByteArray())

        // move page forward to edge/blade if it's the last one
        if (isLastPage) {
            stream.write("SET TEAR ON\r\n".toByteArray())
        } else {
            stream.write("SET TEAR OFF\r\n".toByteArray())
        }

        // print page
        stream.write("PRINT 1,1\r\n".toByteArray())

        // return byte array
        stream.flush()
        return stream.toByteArray()
    }

    private fun configurePrinter(conf: Map<String, String>, type: String) {
        // size
        val maxWidth = conf.get("hardware_${type}printer_maxwidth")?.toInt() ?: this.defaultMaxWidth
        val maxLength = conf.get("hardware_${type}printer_maxlength")?.toInt()
                ?: this.defaultMaxLength
        this.sendCommand("SIZE $maxWidth mm,$maxLength mm\r\n")

        // speed
        val speed = conf.get("hardware_${type}printer_speed")?.toInt() ?: this.defaultSpeed
        if (speed >= 1 && speed <= 15) {
            this.sendCommand("SPEED $speed\r\n")
        } else {
            this.sendCommand("SPEED ${this.defaultSpeed}\r\n")
        }

        // density (print temp)
        val density = conf.get("hardware_${type}printer_density")?.toInt() ?: this.defaultDensity
        this.sendCommand("DENSITY ${density}\r\n")

        // sensor type
        val sensor = conf.get("hardware_${type}printer_sensor")?.toInt() ?: this.defaultSensor
        val sensorHeight = conf.get("hardware_${type}printer_sensor_height")?.toDouble()
                ?: this.defaultSensorHeight
        val sensorOffset = conf.get("hardware_${type}printer_sensor_offset")?.toDouble()
                ?: this.defaultSensorOffset
        when (sensor) {
            Sensor.sContinuous.sensor -> {
                this.sendCommand("GAP 0,0\r\n")
            }

            Sensor.sGap.sensor -> {
                this.sendCommand("GAP $sensorHeight mm,$sensorOffset mm\r\n")
            }

            Sensor.sMark.sensor -> {
                this.sendCommand("BLINE $sensorHeight mm,$sensorOffset mm\r\n")
            }
        }
    }

    override fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream, conf: Map<String, String>, type: String) {
        Log.i("PrintService", "[$type] Using TSPL protocol")
        this.outStream = ostream
        this.clearBuffer() // clear the printer's RAM
        this.configurePrinter(conf, type)

        for (f in pages) {
            Log.i("PrintService", "[$type] Waiting for page to be converted")
            val page = f.get(60, TimeUnit.SECONDS)
            Log.i("PrintService", "[$type] Page ready, sending page")
            ostream.write(page)
            this.clearBuffer()
            Log.i("PrintService", "[$type] Page sent to printer")
        }

        ostream.flush()
    }

    private fun sendCommand(cmd: String): Boolean {
        val msgBuffer: ByteArray = cmd.toByteArray()

        return try {
            this.outStream!!.write(msgBuffer)

            Log.i("TSPL Protocol", "sent command: ${cmd}")
            true
        } catch (var4: IOException) {

            Log.i("TSPL Protocol", "failed sending command: ${cmd}")
            false
        }
    }

    private fun clearBuffer(): Boolean {
        return this.sendCommand("CLS\r\n")
    }
}