package eu.pretix.pretixprint.byteprotocols

import android.R.attr.bitmap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.SunmiInternalConnection
import eu.pretix.pretixprint.ui.PNGSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit


class PNG : SunmiByteProtocol<Bitmap> {
    override val identifier = "PNG"
    override val nameResource = R.string.protocol_png
    override val defaultDPI = 200
    override val demopage = "demopage_8in_3.25in.pdf"

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is SunmiInternalConnection
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val size: Int = img.rowBytes * img.height
        val byteBuffer: ByteBuffer = ByteBuffer.allocate(size)
        img.copyPixelsToBuffer(byteBuffer)
        return byteBuffer.array()
    }

    override fun sendSunmi(printerService: SunmiPrinterService, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String) {
        for (f in pages) {
            Log.i("PrintService", "Waiting for page to be converted")
            val page = f.get(60, TimeUnit.SECONDS)
            Log.i("PrintService", "Page ready, sending page")
            val future = CompletableFuture<Void>()
            val bmp = BitmapFactory.decodeByteArray(page, 0, page.size)

            printerService.enterPrinterBuffer(true)
            printerService.printBitmap(bmp, null)
            printerService.lineWrap(3, null)
            try {
                printerService.cutPaper(null)
            } catch (e: java.lang.Exception) {
                // not supported by all models
            }
            printerService.commitPrinterBufferWithCallback(object : InnerResultCallback() {
                override fun onRunResult(p0: Boolean) {
                    Log.i("PrintService", "PrinterService onRunResult: $p0")
                    future.complete(null)
                }

                override fun onReturnString(p0: String?) {
                    Log.i("PrintService", "PrinterService onReturnString: $p0")
                }

                override fun onRaiseException(code: Int, msg: String?) {
                    future.completeExceptionally(Exception("[$code] $msg"))
                }

                override fun onPrintResult(p0: Int, p1: String?) {
                    Log.i("PrintService", "PrinterService onPrintResult: $p0 $p1")
                    if (p0 == 0) { // Transaction print successful
                        future.complete(null)
                    }
                }
            })
            printerService.exitPrinterBuffer(true)
            future.get()
            Log.i("PrintService", "Page sent")
        }
    }

    override fun createSettingsFragment(): SetupFragment {
        return PNGSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}