package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Looper
import androidx.fragment.app.Fragment
import com.zebra.sdk.comm.BluetoothConnectionInsecure
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.TcpConnection
import com.zebra.sdk.comm.UsbConnection
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.ui.LinkOSSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import org.jetbrains.anko.defaultSharedPreferences
import java.io.ByteArrayOutputStream
import java.io.IOException


class LinkOS : CustomByteProtocol<Bitmap> {
    override val identifier = "LinkOS"
    override val defaultDPI = 203
    override val demopage = "demopage_8in_3.25in.pdf"

    override val nameResource = R.string.protocol_linkos

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return true
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val ostream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.PNG, 0, ostream)
        return ostream.toByteArray()
    }

    override fun sendUSB(usbManager: UsbManager, usbDevice: UsbDevice, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val connection = UsbConnection(usbManager, usbDevice)
        try {
            connection.open()
            send(pages, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    override fun sendNetwork(host: String, port: Int, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val connection = TcpConnection(host, port)
        try {
            connection.open()
            send(pages, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    override fun sendBluetooth(deviceAddress: String, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val connection = BluetoothConnectionInsecure(deviceAddress)
        try {
            connection.open()
            send(pages, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    private fun send(pages: List<CompletableFuture<ByteArray>>, connection: Connection, conf: Map<String, String>, type: String, context: Context) {
        fun getSetting(key: String, def: String): String {
            return conf[key] ?: context.defaultSharedPreferences.getString(key, def)!!
        }

        // ToDo: Make the printer connection blocking, displaying an error message if appropriate.
        Thread {
            Looper.prepare()
            var zebraPrinter: ZebraPrinter? = null

            try {
                zebraPrinter = ZebraPrinterFactory.getInstance(connection)

                for (f in pages) {
                    // ToDo: Proper path or use ZebraImage
                    zebraPrinter.printImage("/path/to/graphics.jpg", 0, 0)
                }
                Thread.sleep(2000)
            } catch (e: Exception) {
                e.printStackTrace()
                throw PrintError(e.message ?: e.toString())
            }
        }.start()
    }

    override fun createSettingsFragment(): SetupFragment? {
        return LinkOSSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}