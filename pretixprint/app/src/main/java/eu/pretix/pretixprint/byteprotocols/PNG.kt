package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import com.sunmi.printerx.PrinterSdk.Printer
import com.sunmi.printerx.api.PrintResult
import com.sunmi.printerx.enums.DividingLine
import com.sunmi.printerx.style.BitmapStyle
import com.sunmi.printerx.style.TextStyle
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.SunmiInternalConnection
import eu.pretix.pretixprint.ui.PNGSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayOutputStream
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
        val stream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        img.recycle()
        return byteArray
    }

    override fun sendSunmi(
        printer: Printer,
        pages: List<CompletableFuture<ByteArray>>,
        conf: Map<String, String>,
        type: String,
        waitAfterPage: Long
    ) {
        for (f in pages) {
            Log.i("PrintService", "[$type] Waiting for page to be converted")
            val page = f.get(60, TimeUnit.SECONDS)
            Log.i("PrintService", "[$type] Page ready, sending page")
            val future = CompletableFuture<Void>()
            val bmp = BitmapFactory.decodeByteArray(page, 0, page.size)

            val api = printer.lineApi()
            api.enableTransMode(true)
            api.printBitmap(bmp, BitmapStyle.getStyle())
            api.printDividingLine(DividingLine.EMPTY, 100)
            api.printTrans(object: PrintResult() {
                override fun onResult(resultCode: Int, message: String?) {
                    if (resultCode == 0) {
                        future.complete(null)
                    } else {
                        future.completeExceptionally(PrintException("Sunmi error: [$resultCode] $message"))
                    }
                }

            })
            api.enableTransMode(false)
            future.get(60, TimeUnit.SECONDS)
            Log.i("PrintService", "[$type] Page sent")
            Log.i("PrintService", "[$type] Job done, sleep")
            Thread.sleep(waitAfterPage)
        }
    }

    override fun createSettingsFragment(): SetupFragment {
        return PNGSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}