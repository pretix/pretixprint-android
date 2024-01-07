package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
    val defaultGrayScaleThreshold: Int = 0 // when should pixels be printed (0-100%)

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt" // allow both ticket and badge printing
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is BluetoothConnection || type is USBConnection
        // todo: test network (I don't have an ethernet-capable TSPL printer)
    }

    override fun createSettingsFragment(): SetupFragment {
        return TSPLSettingsFragment()
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

        // preprocess bitmap (e.g. increase contrast for b/w print)
        val processedImg = this.bitmap2Gray(scaledImg)

        // BITMAP start command
        val mode: String = Integer.toString(0) // print mode (0 = override pixel, 1 = OR, 1 = XOR)
        val width: Int = processedImg.width
        val widthInBytes: Int = (width + 7) / 8
        val height: Int = processedImg.height
        val xOffset = 0
        val yOffset = 0
        stream.write("BITMAP, $xOffset, $yOffset, $widthInBytes, $height, $mode,".toByteArray()) // as tspl takes binary bitmap, width is in bytes, but height in dots

        // byte array of binary b/w image
        val imgStream = ByteArray(widthInBytes * height)

        // set all pixels to white
        var y = 0
        while (y < height * widthInBytes) {
            imgStream[y] = 0
            ++y
        }

        // set pixels above threshold to white / transparent
        y = 0
        while (y < height) {
            for (x in 0 until width) {
                val pixel = processedImg.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val alpha = Color.alpha(pixel)
                val grayScale = (red + green + blue) / 3 * alpha / 255

                if (grayScale > 128) {
                    // set pixel to white/transparent/paper color (1 / true)
                    val byteIndex:Int = y * widthInBytes + (x / 8)
                    val bitIndex:Int = x % 8
                    val oldByte = imgStream[byteIndex]
                    val pixelBitInt = 128 shr bitIndex // 128 = 10000000 shiftRight bit
                    val newByte: Byte = (oldByte.toInt() xor pixelBitInt).toByte()
                    imgStream[byteIndex] = newByte
                    //imgStream[y * ((width + 7) / 8) + x / 8] = (imgStream[y * ((width + 7) / 8) + x / 8].toInt() xor (128 shr x % 8).toByte().toInt()).toByte()
                }
            }
            ++y
        }

        // write into result stream
        stream.write(imgStream)
        stream.write("\r\n".toByteArray())

        // if last page, move page forward to edge, otherwise don't
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

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    private fun configurePrinter(conf: Map<String, String>, type: String) {
        // size
        val maxWidth = conf.get("hardware_${type}printer_maxwidth")?.toInt() ?: this.defaultMaxWidth
        val maxLength = conf.get("hardware_${type}printer_maxlength")?.toInt()
                ?: this.defaultMaxLength
        this.sendCommand("SIZE $maxWidth mm,$maxLength mm")

        // speed
        val speed = conf.get("hardware_${type}printer_speed")?.toInt() ?: this.defaultSpeed
        if (speed >= 1 && speed <= 15) {
            this.sendCommand("SPEED $speed")
        } else {
            this.sendCommand("SPEED ${this.defaultSpeed}")
        }

        // density (print temp)
        val density = conf.get("hardware_${type}printer_density")?.toInt() ?: this.defaultDensity
        //this.sendCommand("DENSITY ${density}")

        //TscDll.setup(paper_width, paper_height, speed, density, sensor, sensor_distance, sensor_offset);
        //this.sendCommand("SIZE 57 mm, 130 mm\r\n")
        //this.sendCommand("GAP 2 mm, 0 mm\r\n");//Gap media
        //this.sendCommand("BLINE 2 mm, 0 mm\r\n");//blackmark media

        // todo: gap and blackMark setting
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
                this.sendCommand("GAP $sensorHeight mm,$sensorOffset\r\n mm")
            }

            Sensor.sMark.sensor -> {
                this.sendCommand("BLINE $sensorHeight mm,$sensorOffset\r\n mm")
            }
        }
    }

    override fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream, conf: Map<String, String>, type: String) {
        Log.i("PrintService", "[$type] Using TSPL protocol")
        this.outStream = ostream
        this.clearBuffer() // clear the printer
        this.configurePrinter(conf, type)
        //this.printTestLabel()

        for (f in pages) {
            Log.i("PrintService", "[$type] Waiting for page to be converted")
            val page = f.get(60, TimeUnit.SECONDS)
            Log.i("PrintService", "[$type] Page ready, sending page")
            ostream.write(page)
            Log.i("PrintService", "[$type] Page sent to printer")
        }

        ostream.flush()
    }

    private fun printTestLabel() {
        this.clearBuffer()
        this.sendCommand("TEXT 100,300,\"ROMAN.TTF\",0,12,12,@1\r\n")
        this.sendCommand("TEXT 100,400,\"ROMAN.TTF\",0,12,12,\"TEST FONT\"\r\n")
        this.printLabel()
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

    private fun printLabel(): Boolean {
        val quantity = 1
        val copy = 1
        val message = "PRINT $quantity, $copy\r\n"
        return this.sendCommand(message)
    }

    // this is from the TSC_DLL_EXAMPLE Android SDK
    fun bitmap2Gray(bmSrc: Bitmap): Bitmap {
        val width = bmSrc.width
        val height = bmSrc.height
        var bmpGray: Bitmap? = null
        bmpGray = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val c = Canvas(bmpGray)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0.0f)
        val f = ColorMatrixColorFilter(cm)
        paint.setColorFilter(f)
        c.drawBitmap(bmSrc, 0.0f, 0.0f, paint)
        return bmpGray
    }
}