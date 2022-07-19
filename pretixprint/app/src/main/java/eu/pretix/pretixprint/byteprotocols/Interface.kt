package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

sealed interface ByteProtocolInterface<T> {
    val identifier: String
    val nameResource: Int
    val defaultDPI: Int
    val demopage: String

    fun allowedForUsecase(type: String): Boolean
    fun allowedForConnection(type: ConnectionType): Boolean
    fun convertPageToBytes(img: T, isLastPage: Boolean, previousPage: T?, conf: Map<String, String>, type: String): ByteArray
    fun createSettingsFragment(): SetupFragment?
    fun inputClass(): Class<T>
}

interface StreamByteProtocol<T> : ByteProtocolInterface<T> {
    fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream, conf: Map<String, String>, type: String)
}

interface CustomByteProtocol<T> : ByteProtocolInterface<T> {
    fun sendUSB(usbManager: UsbManager, usbDevice: UsbDevice, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context)
    fun sendNetwork(host: String, port: Int, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context)
    fun sendBluetooth(deviceAddress: String, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context)
}

interface SunmiByteProtocol<T> : ByteProtocolInterface<T> {
    fun sendSunmi(printerService: SunmiPrinterService, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String)
}

fun getProtoClass(proto: String): ByteProtocolInterface<Any> {
    for (p in protocols) {
        if (p.identifier == proto) {
            return p as ByteProtocolInterface<Any>
        }
    }
    return FGL() as ByteProtocolInterface<Any>  // backwards compatible
}

class PrintError(message: String) : IOException(message);
