package eu.pretix.pretixprint.byteprotocols

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.util.Log
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.BluetoothConnection
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.USBConnection
import eu.pretix.pretixprint.ui.ESCLabelSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import eu.pretix.pretixprint.ui.TSPLSettingsFragment
import java8.util.concurrent.CompletableFuture
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID


class TSPL : StreamByteProtocol<Bitmap> {

    override val identifier = "TSPL"
    override val nameResource = R.string.protocol_tspl

    override val demopage = "demopage_cr80.pdf"

    override val defaultDPI = 203
    val defaultMaxWidth = 57 // mm
    val defaultMaxLength = 100 // mm
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
        return "empty page".toByteArray()
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
        this.sendCommand("SET TEAR ON\r\n")
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
}