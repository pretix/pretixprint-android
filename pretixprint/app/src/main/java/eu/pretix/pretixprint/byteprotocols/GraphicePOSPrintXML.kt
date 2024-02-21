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


class GraphicePOSPrintXML : CustomByteProtocol<Bitmap> {
    override val identifier = "GraphicePOSPrintXML"
    override val nameResource = R.string.protocol_eposprintxml
    override val defaultDPI = 203
    override val demopage = "demopage_8in_3.25in.pdf"

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?,conf: Map<String, String>, type: String): ByteArray {
        return GraphicESCPOS().convertPageToBytes(img, isLastPage, previousPage, conf, type)
    }

    override fun createSettingsFragment(): SetupFragment? {
        return GraphicePOSPrintXMLSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    override fun sendNetwork(host: String, port: Int, pages: List<CompletableFuture<ByteArray>>, pagegroups: List<Int>, conf: Map<String, String>, type: String, context: Context) {
        ePOSPrintXML().sendNetwork(host, port, pages, pagegroups, conf, type, context)
    }

    override fun sendUSB(usbManager: UsbManager, usbDevice: UsbDevice, pages: List<CompletableFuture<ByteArray>>, pagegroups: List<Int>, conf: Map<String, String>, type: String, context: Context) {
        ePOSPrintXML().sendUSB(usbManager, usbDevice, pages, pagegroups, conf, type, context)
    }

    override fun sendBluetooth(deviceAddress: String, pages: List<CompletableFuture<ByteArray>>, pagegroups: List<Int>, conf: Map<String, String>, type: String, context: Context) {
        ePOSPrintXML().sendBluetooth(deviceAddress, pages, pagegroups, conf, type, context)
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is NetworkConnection
    }
}