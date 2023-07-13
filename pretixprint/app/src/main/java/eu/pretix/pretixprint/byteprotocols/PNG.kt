package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.escpos.EscPosConst
import com.github.anastaciocintra.escpos.image.BitonalThreshold
import com.github.anastaciocintra.escpos.image.CoffeeImage
import com.github.anastaciocintra.escpos.image.EscPosImage
import com.github.anastaciocintra.escpos.image.GraphicsImageWrapper
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.SunmiInternalConnection
import eu.pretix.pretixprint.ui.GraphicESCPOSSettingsFragment
import eu.pretix.pretixprint.ui.PNGSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
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

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String, context: Context): ByteArray {
        val stream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        img.recycle()
        return byteArray
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