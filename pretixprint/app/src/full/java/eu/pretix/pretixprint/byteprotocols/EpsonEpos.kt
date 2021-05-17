package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import com.epson.epos2.printer.Printer
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture


class EpsonEpos : CustomByteProtocol<ByteArray> {
    override val identifier = "EpsonEpos"
    override fun allowedForUsecase(type: String): Boolean {
        return type == "receipt"
    }
    override val defaultDPI = 200
    override val demopage = "demopage.txt"

    override val nameResource = R.string.protocol_epsonepos

    override fun convertPageToBytes(img: ByteArray, isLastPage: Boolean, previousPage: ByteArray?, conf: Map<String, String>, type: String): ByteArray {
        return img
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun sendUSB(usbManager: UsbManager, usbDevice: UsbDevice, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val printer = Printer(Printer.TM_M30II, Printer.MODEL_ANK, context)
        printer.connect("USB:${usbDevice.serialNumber}", Printer.PARAM_DEFAULT)
        try {
            send(pages, printer, conf, type, context)
        } finally {
            printer.disconnect()
        }
    }

    override fun sendNetwork(host: String, port: Int, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val printer = Printer(Printer.TM_M30II, Printer.MODEL_ANK, context)
        printer.connect("TCP:$host", Printer.PARAM_DEFAULT)
        try {
            send(pages, printer, conf, type, context)
        } finally {
            printer.disconnect()
        }
    }

    override fun sendBluetooth(deviceAddress: String, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val printer = Printer(Printer.TM_M30II, Printer.MODEL_ANK, context)
        printer.connect("BT:$deviceAddress", Printer.PARAM_DEFAULT)
        try {
            send(pages, printer, conf, type, context)
        } finally {
            printer.disconnect()
        }
    }

    private fun send(pages: List<CompletableFuture<ByteArray>>, printer: Printer, conf: Map<String, String>, type: String, context: Context) {
        System.out.println("EPSON EPOS START")
        printer.setReceiveEventListener { printerObj, code, status, jobId ->
            System.out.println("EPSON EPOS RECEIVE EVENT $printerObj # $code # $status # $jobId")
        }

        Thread {
            Looper.prepare()

            try {
                for (f in pages) {
                    printer.addCommand(f.get())
                }
                printer.sendData(Printer.PARAM_DEFAULT)
                Thread.sleep(2000)
            } catch (e: Exception) {
                e.printStackTrace()
                throw PrintError(e.message ?: e.toString())
            } finally {
                printer.clearCommandBuffer()
                printer.setReceiveEventListener(null)
            }
        }.start()
    }

    override fun createSettingsFragment(): SetupFragment? {
        return null
    }

    override fun inputClass(): Class<ByteArray> {
        return ByteArray::class.java
    }
}
