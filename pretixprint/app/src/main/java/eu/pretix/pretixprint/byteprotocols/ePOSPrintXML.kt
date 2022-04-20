package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.ui.SetupFragment
import eu.pretix.pretixprint.ui.ePOSPrintXMLSettingsFragment
import java8.util.concurrent.CompletableFuture
import org.jetbrains.anko.defaultSharedPreferences
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit


class ePOSPrintXML : CustomByteProtocol<ByteArray> {
    override val identifier = "ePOSPrintXML"
    override val nameResource = R.string.protocol_eposprintxml
    override val defaultDPI = 200
    override val demopage = "demopage.eposprintxml"

    override fun allowedForUsecase(type: String): Boolean {
        return type == "receipt"
    }

    override fun convertPageToBytes(img: ByteArray, isLastPage: Boolean, previousPage: ByteArray?, conf: Map<String, String>, type: String): ByteArray {
        return img
    }

    override fun createSettingsFragment(): SetupFragment? {
        return ePOSPrintXMLSettingsFragment()
    }

    override fun inputClass(): Class<ByteArray> {
        return ByteArray::class.java
    }

    override fun sendNetwork(
        host: String,
        port: Int,
        pages: List<CompletableFuture<ByteArray>>,
        conf: Map<String, String>,
        type: String,
        context: Context
    ) {
        fun getSetting(key: String, def: String): String {
            return conf[key] ?: context.defaultSharedPreferences.getString(key, def)!!
        }

        val deviceId = getSetting("hardware_${type}printer_deviceId", "local_printer")
        val url = URL("http://$host:$port/cgi-bin/epos/service.cgi?devid=$deviceId&timeout=10000")

        for (f in pages) {
            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                setRequestProperty("SOAPAction", "\"\"")

                val wr = OutputStreamWriter(outputStream)
                wr.write(String(f.get(60, TimeUnit.SECONDS)))
                wr.flush()
                wr.close()

                responseCode
            }
        }
    }

    override fun sendUSB(
        usbManager: UsbManager,
        usbDevice: UsbDevice,
        pages: List<CompletableFuture<ByteArray>>,
        conf: Map<String, String>,
        type: String,
        context: Context
    ) {
        TODO("Not yet implemented")
    }

    override fun sendBluetooth(
        deviceAddress: String,
        pages: List<CompletableFuture<ByteArray>>,
        conf: Map<String, String>,
        type: String,
        context: Context
    ) {
        TODO("Not yet implemented")
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is NetworkConnection
    }
}