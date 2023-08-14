package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.api.PrintResult
import com.sunmi.printerx.enums.ImageAlgorithm
import com.sunmi.printerx.style.BaseStyle
import com.sunmi.printerx.style.BitmapStyle
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.SunmiInternalConnection
import eu.pretix.pretixprint.ui.SetupFragment
import eu.pretix.pretixprint.ui.SunmiPrinterXLabelsSettingsFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// Documentation: https://developer.sunmi.com/docs/en-US/xeghjk491/mameghjk546
class SunmiPrinterXLabels : SunmiPrinterXByteProtocol<Bitmap> {
    override val identifier = "SunmiPrinterXLabels"
    override val nameResource = R.string.protocol_sunmi_printerx_labels
    override val defaultDPI = 300  // Printer is natively 203dpi, but prints look better when rendered at 300dpi
    override val demopage = "demopage_8in_3.25in.pdf"

    override fun allowedForUsecase(type: String): Boolean {
        return type == "badge"
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

    override fun sendSunmi(printer: PrinterSdk.Printer, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String) {
        for (f in pages) {
            Log.i("PrintService", "Waiting for page to be converted")
            val page = f.get(60, TimeUnit.SECONDS)
            Log.i("PrintService", "Page ready, sending page")
            val bmp = BitmapFactory.decodeByteArray(page, 0, page.size)

            // Get configuration parameters
            val printWidth = (conf.get("hardware_${type}printer_sunmiprinterxlabels_print_width")!!).toInt()
            val rollWidth = (conf.get("hardware_${type}printer_sunmiprinterxlabels_roll_width")!!).toInt()
            val labelWidth = (conf.get("hardware_${type}printer_sunmiprinterxlabels_label_width")!!).toInt()
            val labelHeight = (conf.get("hardware_${type}printer_sunmiprinterxlabels_label_height")!!).toInt()

            // Calculate canvas
            val totalPrinterMargin = if (printWidth == 58) 10 else 8
            val totalLinerMargin = rollWidth - labelWidth

            val totalHorizontalMargin = maxOf(totalPrinterMargin, totalLinerMargin)


            val imageWidth = (rollWidth -  totalHorizontalMargin) * 8 // 8px per mm
            val imageHeight = labelHeight * 8

            val x0 = (totalHorizontalMargin - totalPrinterMargin) * (8/2)
            val y0 = 0

            val canvasWidth = x0 + imageWidth
            val canvasHeight = imageHeight

            // Print
            printer.canvasApi().run {
                initCanvas(BaseStyle.getStyle().setWidth(canvasWidth).setHeight(canvasHeight))
                renderBitmap(bmp, BitmapStyle.getStyle().setPosX(x0).setPosX(y0).setWidth(imageWidth).setHeight(imageHeight).setAlgorithm(ImageAlgorithm.BINARIZATION))
                printCanvas(1, object : PrintResult() {
                    override fun onResult(resultCode: Int, message: String?) {
                        if (resultCode == 0) {
                            // Print successful
                        } else {
                            println(printer.queryApi()?.status)
                        }
                    }

                })
            }


        }
    }

    override fun createSettingsFragment(): SetupFragment {
        return SunmiPrinterXLabelsSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}