package eu.pretix.pretixprint.byteprotocols

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import eu.pretix.pretixprint.R
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
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.ceil


class TSPL : StreamByteProtocol<Bitmap> {

    override val identifier = "TSPL"
    override val nameResource = R.string.protocol_tspl

    override val demopage = "demopage_cr80.pdf"

    override val defaultDPI = 203
    val defaultMaxWidth = 57 // mm
    val defaultMaxLength = 20 // mm
    val defaultSpeed = 2 // inch/sec (supported by most TSC printers
    val defaultDensity = 8 // 1-15 (print temperature)
    val defaultSensor = 0 // 0=vertical gap; 1=black mark
    val defaultSensorSize = 2 // height of gap/mark in mm
    val defaultSensorOffset = 0 // offset after mark

    private var isConnected = false
    private var btSocket: BluetoothSocket? = null
    private var outStream: OutputStream? = null
    private var inStream: InputStream? = null

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt" // allow both
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is BluetoothConnection || type is USBConnection // todo: test network (didn't have one to test with)
    }

    override fun createSettingsFragment(): SetupFragment {
        return TSPLSettingsFragment()
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val stream = ByteArrayOutputStream()

        // test
        /*
        stream.write("TEXT 100,300,\"ROMAN.TTF\",0,12,12,@1\r\n".toByteArray(US_ASCII))
        stream.write("TEXT 100,400,\"ROMAN.TTF\",0,12,12,\"page\"\r\n".toByteArray(US_ASCII))
        //stream.write("BITMAP 10, 20, 5, 5, 0\r\n".toByteArray())
        stream.write("BAR 80,80,300,100".toByteArray(US_ASCII))
        stream.write("PRINT 1\r\n".toByteArray(US_ASCII))
        stream.flush()
        return stream.toByteArray()
         */

        // width
        val dpi = conf.get("hardware_${type}printer_dpi")?.toInt() ?: this.defaultDPI
        val maxWidthMM = conf.get("hardware_${type}printer_maxwidth")?.toInt() ?: this.defaultMaxWidth
        val targetWidth = (maxWidthMM * 0.0393701 * dpi).toInt() // in dots

        // scale if wider than target medium
        val scaledImg = if (img.width > targetWidth) {
            val targetHeight = (targetWidth.toFloat() / img.width.toFloat() * img.height.toFloat()).toInt()
            Bitmap.createScaledBitmap(img, targetWidth, targetHeight, true)
        } else {
            img
        }

        // write image to stream
        // Construct Start Command
        val mode = Integer.toString(0) // print mode
        // attention: tspl takes bitmap width in bytes, but height in dots
        val width = ceil(scaledImg.width / 8.0)
        val height = scaledImg.height
        val x = 0
        val y = 0
        val command = "BITMAP, $x, $y, $width, $height, $mode,"
        stream.write(command.toByteArray())

        // convert image to 1 bit bitmap
        val binaryStream = this.getBitmapStream(scaledImg)
        // write into result stream
        stream.write(binaryStream)

        // write final command
        stream.write("\r\n".toByteArray())

        // return byte array
        return stream.toByteArray()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    private fun config(conf: Map<String, String>, type: String) {
        // size
        val maxWidth = conf.get("hardware_${type}printer_maxwidth")?.toInt() ?: this.defaultMaxWidth
        val maxLength = conf.get("hardware_${type}printer_maxlength")?.toInt() ?: this.defaultMaxLength
        this.sendCommand("SIZE ${maxWidth} mm, ${maxLength} mm")

        // speed
        val speed = conf.get("hardware_${type}printer_speed")?.toInt() ?: this.defaultSpeed
        if (speed >= 1 && speed <= 15) {
            this.sendCommand("SPEED ${speed}")
        } else {
            this.sendCommand("SPEED ${this.defaultSpeed}")
        }

        // density
        val density = conf.get("hardware_${type}printer_density")?.toInt() ?: this.defaultDensity
        this.sendCommand("DENSITY ${density}")

        // tear (moves the medium forward to cutter/blade)
        // is now done -> in convert page to bytes
    //this.sendCommand("SET TEAR ON\r\n")
    }

    override fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream, conf: Map<String, String>, type: String) {
        Log.i("TSPL Protocol", "printing with TSPL protocol")

        this.outStream = ostream

        // configuration
        this.config(conf, type)

        //TscDll.setup(paper_width, paper_height, speed, density, sensor, sensor_distance, sensor_offset);
        //this.sendCommand("SIZE 57 mm, 130 mm\r\n")
        //this.sendCommand("GAP 2 mm, 0 mm\r\n");//Gap media
        //this.sendCommand("BLINE 2 mm, 0 mm\r\n");//blackmark media

        this.clearBuffer()
        this.sendCommand("TEXT 100,300,\"ROMAN.TTF\",0,12,12,@1\r\n")
        this.sendCommand("TEXT 100,400,\"ROMAN.TTF\",0,12,12,\"TEST FONT\"\r\n")
        this.printLabel()

        for(f in pages) {
            Log.i("PrintService", "[$type] Waiting for page to be converted")
            val page = f.get(60, TimeUnit.SECONDS)
            Log.i("PrintService", "[$type] Page ready, sending page")
            this.clearBuffer()
            ostream.write(page)
            Log.i("PrintService", "sent to printer")
            //this.printLabel()
        }

        //this.closePort(5000)
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


    fun sendCommand(message: ByteArray?): String? {
        return if (this.outStream != null && this.inStream != null) {
            try {
                this.outStream!!.write(message)
                "1"
            } catch (var3: IOException) {
                "-1"
            }
        } else {
            "-1"
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

    private fun openPort(address: String, delay: Int): Boolean {
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter.isEnabled) {
            this.isConnected = true
            val device = mBluetoothAdapter.getRemoteDevice(address)

            try {
                val myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                this.btSocket = device.createRfcommSocketToServiceRecord(myUUID)
            } catch (var10: IOException) {
                return false
            }

            mBluetoothAdapter.cancelDiscovery()

            try {
                this.btSocket?.connect()
                this.outStream = this.btSocket!!.outputStream
                this.inStream = this.btSocket!!.inputStream
            } catch (var9: IOException) {
                return false
            }

            try {
                Thread.sleep(delay.toLong())
            } catch (var8: InterruptedException) {
                var8.printStackTrace()
            }

            Log.i("TSPL Protocol", "connection established")

            return true
        } else {
            Log.i("TSPL Protocol", "connection failed")
            this.isConnected = false
            return false
        }
    }

    private fun closePort(timeout: Int): Boolean {
        try {
            Thread.sleep(timeout.toLong())
        } catch (var5: InterruptedException) {
            var5.printStackTrace()
        }

        if (btSocket!!.isConnected) {
            try {
                this.isConnected = false
                btSocket!!.close()
            } catch (var4: IOException) {
                return false
            }
            try {
                Thread.sleep(100L)
            } catch (var3: InterruptedException) {
                var3.printStackTrace()
            }
            return true
        } else {
            return false
        }
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


    fun getBitmapStream(original_bitmap: Bitmap): ByteArray {
        val options = BitmapFactory.Options()
        options.inPurgeable = true
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val gray_bitmap = this.bitmap2Gray(original_bitmap)
        val binary_bitmap = this.gray2Binary(gray_bitmap)
        val stream = ByteArray((binary_bitmap.width + 7) / 8 * binary_bitmap.height)
        val Width_bytes = (binary_bitmap.width + 7) / 8
        val Width = binary_bitmap.width
        val Height = binary_bitmap.height

        // set every pixel to black (0 / false / -1)
        var y: Int = 0
        while (y < Height * Width_bytes) {
            stream[y] = -1
            ++y
        }

        // set white pixels to white
        y = 0
        while (y < Height) {
            for (x in 0 until Width) {
                val pixelColor = binary_bitmap.getPixel(x, y)
                val colorR = Color.red(pixelColor)
                val colorG = Color.green(pixelColor)
                val colorB = Color.blue(pixelColor)
                val total = (colorR + colorG + colorB) / 3
                // when no color in current pixel
                if (total == 0) {
                    // set to white/transparent/paper color (1 / true)
                    // please don't ask any specifics about this line, I'm just glad it works
                    stream[y * ((Width + 7) / 8) + x / 8] = (stream[y * ((Width + 7) / 8) + x / 8].toInt() xor (128 shr x % 8).toByte().toInt()).toByte()
                }
            }
            ++y
        }
        return stream
    }

    fun getBlackStream(width: Int, height: Int): ByteArray {
        val options = BitmapFactory.Options()
        options.inPurgeable = true
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val stream = ByteArray((width + 7) / 8 * height)
        val Width_bytes = (width + 7) / 8
        var y: Int
        y = 0
        while (y < height * Width_bytes) {
            stream[y] = 0
            ++y
        }
        return stream
    }

    fun gray2Binary(graymap: Bitmap): Bitmap {
        val width = graymap.width
        val height = graymap.height
        var binarymap: Bitmap? = null
        binarymap = graymap.copy(Bitmap.Config.ARGB_8888, true)
        for (i in 0 until width) {
            for (j in 0 until height) {
                val col = binarymap.getPixel(i, j)
                val alpha = col and -16777216
                val red = col and 16711680 shr 16
                val green = col and '\uff00'.code shr 8
                val blue = col and 255
                val gray = (red.toFloat().toDouble() * 0.3 + green.toFloat().toDouble() * 0.59 + blue.toFloat().toDouble() * 0.11).toInt()
                var shortGray: Short
                shortGray = if (gray <= 127) {
                    0
                } else {
                    255
                }
                val newColor = alpha or (gray.toInt() shl 16) or (gray.toInt() shl 8) or gray.toInt()
                binarymap.setPixel(i, j, newColor)
            }
        }
        return binarymap
    }
}