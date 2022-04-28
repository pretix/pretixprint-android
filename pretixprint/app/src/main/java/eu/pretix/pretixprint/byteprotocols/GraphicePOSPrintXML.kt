package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.ui.GraphicePOSPrintXMLSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import org.jetbrains.anko.defaultSharedPreferences
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit


class GraphicePOSPrintXML : CustomByteProtocol<Bitmap> {
    override val identifier = "GraphicePOSPrintXML"
    override val nameResource = R.string.protocol_eposprintxml
    override val defaultDPI = 203
    override val demopage = "demopage_8in_3.25in.pdf"

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun convertPageToBytes(img: Bitmap,isLastPage: Boolean, previousPage: Bitmap?,conf: Map<String, String>, type: String): ByteArray {
        return GraphicESCPOS().convertPageToBytes(img, isLastPage, previousPage, conf, type)
    }

    override fun createSettingsFragment(): SetupFragment? {
        return GraphicePOSPrintXMLSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
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
                val escposdata = f.get(60, TimeUnit.SECONDS).toHex()
                wr.write("""
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                        <s:Body>
                            <epos-print xmlns="http://www.epson-pos.com/schemas/2011/03/epos-print">
                                <command>
                                    $escposdata
                                </command>
                            </epos-print>
                        </s:Body>
                    </s:Envelope>
                """.trimIndent())
                wr.flush()
                wr.close()

                responseCode
            }
        }
    }

    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

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