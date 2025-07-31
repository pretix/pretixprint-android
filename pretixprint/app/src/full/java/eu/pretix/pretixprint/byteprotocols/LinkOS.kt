package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Looper
import androidx.preference.PreferenceManager
import com.zebra.sdk.comm.*
import com.zebra.sdk.graphics.ZebraImageFactory
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.connections.BluetoothConnection
import eu.pretix.pretixprint.connections.USBConnection
import eu.pretix.pretixprint.ui.LinkOSSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import androidx.core.graphics.createBitmap


class LinkOS : CustomByteProtocol<Bitmap> {
    override val identifier = "LinkOS"
    override val defaultDPI = 203
    override val demopage = "demopage_8in_3.25in.pdf"

    override val nameResource = R.string.protocol_linkos

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is USBConnection || type is NetworkConnection || type is BluetoothConnection
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        // ZebraImageFactory does not cope well with transparency - it's just black.
        // So we're actively drawing the original picture on a white background.
        val backgroundedImage = createBitmap(img.width, img.height, img.config!!)
        val canvas = Canvas(backgroundedImage)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(img, 0f, 0f, null)

        val ostream = ByteArrayOutputStream()
        backgroundedImage.compress(Bitmap.CompressFormat.PNG, 0, ostream)
        return ostream.toByteArray()
    }

    override fun sendUSB(usbManager: UsbManager, usbDevice: UsbDevice, pages: List<CompletableFuture<ByteArray>>, pagegroups: List<Int>, conf: Map<String, String>, type: String, context: Context) {
        val connection = UsbConnection(usbManager, usbDevice)
        try {
            connection.open()
            send(pages, pagegroups, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    override fun sendNetwork(host: String, port: Int, pages: List<CompletableFuture<ByteArray>>, pagegroups: List<Int>, conf: Map<String, String>, type: String, context: Context) {
        val connection = TcpConnection(host, port)
        try {
            connection.open()
            send(pages, pagegroups, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    override fun sendBluetooth(deviceAddress: String, pages: List<CompletableFuture<ByteArray>>, pagegroups: List<Int>, conf: Map<String, String>, type: String, context: Context) {
        val connection = BluetoothConnectionInsecure(deviceAddress)
        try {
            connection.open()
            send(pages, pagegroups, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    private fun send(pages: List<CompletableFuture<ByteArray>>, pagegroups: List<Int>, connection: Connection, conf: Map<String, String>, type: String, context: Context) {
        fun getSetting(key: String, def: String): String {
            return conf[key] ?: PreferenceManager.getDefaultSharedPreferences(context).getString(key, def)!!
        }

        val future = CompletableFuture<Void>()
        future.completeAsync {
            if (Looper.myLooper() == null) Looper.prepare()
            var zebraPrinter: ZebraPrinter? = null

            zebraPrinter = ZebraPrinterFactory.getInstance(connection)

            for (f in pages) {
                val img = ZebraImageFactory.getImage(ByteArrayInputStream(f.get(60, TimeUnit.SECONDS)))
                zebraPrinter.printImage(img, 0, 0, img.width, img.height, false)
            }
            Thread.sleep(2000)
            null
        }
        try {
            future.get(5, TimeUnit.MINUTES)
        } catch (e: ExecutionException) {
            e.printStackTrace()
            throw PrintError(e.cause?.message ?: e.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintError(e.message ?: e.toString())
        }
    }

    override fun createSettingsFragment(): SetupFragment? {
        return LinkOSSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}