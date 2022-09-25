package eu.pretix.pretixprint.byteprotocols

import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.brother.sdk.lmprinter.Channel
import com.brother.sdk.lmprinter.OpenChannelError
import com.brother.sdk.lmprinter.PrinterDriverGenerator
import com.brother.sdk.lmprinter.PrinterModel as BrotherPrinterModel
import com.brother.sdk.lmprinter.setting.PrintImageSettings
import com.brother.sdk.lmprinter.setting.QLPrintSettings
import com.brother.sdk.lmprinter.setting.QLPrintSettings.LabelSize
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.ui.BrotherRasterSettingsFragment
import eu.pretix.pretixprint.ui.BrotherSDKSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import org.jetbrains.anko.defaultSharedPreferences
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import com.brother.sdk.lmprinter.PrintError as BrotherPrintError


class BrotherSDK : CustomByteProtocol<Bitmap> {
    override val identifier = "BrotherSDK"
    override val nameResource = R.string.protocol_brothersdk
    override val defaultDPI = 300
    override val demopage = "demopage_8in_3.25in.pdf"

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return true
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val stream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.PNG, 0, stream)
        return stream.toByteArray()
    }

    override fun createSettingsFragment(): SetupFragment? {
        return BrotherSDKSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    fun getSetting(key: String, def: String, conf: Map<String, String>, context: Context): String {
        return conf[key] ?: context.defaultSharedPreferences.getString(key, def)!!
    }

    fun printPages(channel: Channel, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        for (f in pages) {
            Log.d("PrintService", "Opening channel")
            val result = PrinterDriverGenerator.openChannel(channel)
            if (result.error.code != OpenChannelError.ErrorCode.NoError) {
                Log.e("", "Error - Open Channel: " + result.error.code)
                return
            }
            Log.d("PrintService", "Channel opened")

            val dir: File? = context.getExternalFilesDir(null)

            val labelSize = Label.values().find { it.name == conf.get("hardware_${type}printer_label") }!!
            val printerModel = PrinterModel.values().find { it.name == conf.get("hardware_${type}printer_brothermodel") }!!
            val resolution = Resolution.values().find { it.name == conf.get("hardware_${type}printer_brotherresolution") }!!
            val autoCut = conf.get("hardware_${type}printer_autocut") == "true"
            val quality = conf.get("hardware_${type}printer_quality") == "true"
            val rotate90 = conf.get("hardware_${type}printer_rotate90") == "true"

            val printerDriver = result.driver
            val printSettings = QLPrintSettings(printerModel.brotherPrinterModel)
            printSettings.labelSize = labelSize.sdkLabelSize
            printSettings.isAutoCut = autoCut
            printSettings.printQuality = if (quality) PrintImageSettings.PrintQuality.Best else PrintImageSettings.PrintQuality.Fast
            printSettings.resolution = resolution.sdkResolution
            printSettings.hAlignment = PrintImageSettings.HorizontalAlignment.Center
            printSettings.vAlignment = PrintImageSettings.VerticalAlignment.Center
            printSettings.workPath = dir.toString()

            val byteArray = f.get(30,TimeUnit.SECONDS)
            Log.i("PrintService", "Waiting for page to be converted")
            var bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            if (rotate90) {
                bitmap = bitmap.rotate(90f)
            }
            Log.i("PrintService", "Page ready, sending page")
            val printError: BrotherPrintError = printerDriver.printImage(bitmap, printSettings)
            Log.i("PrintService", "Page sent")
            if (printError.code !== BrotherPrintError.ErrorCode.NoError) {
                Log.d("", "Error - Print Image: " + printError.code)
            } else {
                Log.d("", "Success - Print Image")
            }

            printerDriver.closeChannel()

        }
    }

    override fun sendNetwork(host: String, port: Int, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val channel: Channel = Channel.newWifiChannel(host)
        printPages(channel,pages, conf, type, context)
        }

    override fun sendUSB(usbManager: UsbManager, usbDevice: UsbDevice, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val channel: Channel = Channel.newUsbChannel(usbManager)
        printPages(channel, pages, conf, type, context)
    }

    override fun sendBluetooth(deviceAddress: String, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val channel: Channel = Channel.newBluetoothChannel(deviceAddress, bluetoothAdapter)
        printPages(channel, pages, conf, type, context)
    }

    enum class PrinterModel(val brotherPrinterModel: BrotherPrinterModel, val modelName: String){
        QL_580N(BrotherPrinterModel.QL_580N, "QL-580N"),
        QL_710W(BrotherPrinterModel.QL_710W, "QL-710W"),
        QL_720NW(BrotherPrinterModel.QL_720NW, "QL-720NW"),
        QL_800(BrotherPrinterModel.QL_800, "QL-800"),
        QL_810W(BrotherPrinterModel.QL_810W,"QL-810W"),
        QL_820NWB(BrotherPrinterModel.QL_820NWB,"QL820-NWB"),
        QL_1100(BrotherPrinterModel.QL_1100,"QL-1100"),
        QL_1110NWB(BrotherPrinterModel.QL_1110NWB,"QL-1110NWB"),
        QL_1115NWB(BrotherPrinterModel.QL_1115NWB,"QL-1115NWB"),
    }

    /**
     * Label data list extracted from 2.3.2 Page size in the following documents:
     * @url https://download.brother.com/welcome/docp100278/cv_ql800_eng_raster_100.pdf
     * @url https://download.brother.com/welcome/docp100366/cv_ql1100_eng_raster_100.pdf
     *
     * @param width the paper/label width in mm. See 2.3.2 Page size, column Tape/Label Size
     * @param length the paper/label height in mm. See 2.3.2 Page size, column Tape/Label Size. 0 for endless paper
     * @param continuous does the roll contain single labels (false) or is endless paper (true)?
     * @param printableWidth in dots. See 2.3.2 Page size, column 3 Print area width
     * @param printableLength in dots. See 2.3.2 Page size, column 4 Print area length. 0 for endless paper
     * @param twoColor some labels support red/black
     * @param sdkLabelSize label size identifier for the SDK
     */
    enum class Label(val sdkLabelSize: LabelSize, val id: Int, val width: Int, val length: Int, val continuous: Boolean, val printableWidth: Int, val printableLength: Int, val twoColor: Boolean = false) {
        c12mm(LabelSize.RollW12, 257, 12, 0, true, 106, 0, false),
        c29mm(LabelSize.RollW29, 258, 29, 0, true, 306, 0),
        c38mm(LabelSize.RollW38, 264, 38, 0, true, 413, 0),
        c50mm(LabelSize.RollW50,262, 50, 0, true, 554, 0),
        c54mm(LabelSize.RollW54,261, 54, 0, true, 590, 0),
        c62mm(LabelSize.RollW62, 259, 62, 0, true, 696, 0),
        c62mm_rb(LabelSize.RollW62RB, 259, 62, 0, true, 696, 0, true),
        c102mm(LabelSize.RollW102, 260, 102, 0, true, 1164, 0),
        c103mm(LabelSize.RollW103, 265, 103, 0, true, 1200, 0),
        d17x54(LabelSize.DieCutW17H54, 269, 17, 54, false, 165, 566),
        d17x87(LabelSize.DieCutW17H87,270, 17, 87, false, 165, 956),
        d23x23(LabelSize.DieCutW23H23, 370, 23, 23, false, 236, 202),
        d29x42(LabelSize.DieCutW29H42, 358, 29, 42, false, 306, 425),
        d29x90(LabelSize.DieCutW29H90, 271, 29, 90, false, 306, 991),
        d38x90(LabelSize.DieCutW38H90,272, 38, 90, false, 413, 991),
        d39x48(LabelSize.DieCutW39H48,367, 39, 48, false, 425, 495),
        d52x29(LabelSize.DieCutW52H29,374, 52, 29, false, 578, 271),
        d60x86(LabelSize.DieCutW60H86,383, 60, 86, false, 672, 954),
        d62x29(LabelSize.DieCutW62H29, 274, 62, 29, false, 696, 271),
        d62x100(LabelSize.DieCutW62H100, 275, 62, 100, false, 696, 1109),
        d102x51(LabelSize.DieCutW102H51, 365, 102, 51, false, 1164, 526),
        d102x152(LabelSize.DieCutW102H152, 366, 102, 152, false, 1164, 1660),
        d103x164(LabelSize.DieCutW103H164, 385, 103, 164, false, 1200, 1822);

        fun size(): String {
            return if (this.continuous) "${this.width} mm" else "${this.width} mm × ${this.length} mm"
        }

        override fun toString(): String {
            var n = size()
            if (this.twoColor) {
                n += " (red/black)"
            }
            return n
        }
    }

    enum class Resolution(val sdkResolution: PrintImageSettings.Resolution, val text: String) {
        high(PrintImageSettings.Resolution.High, "High"),
        normal(PrintImageSettings.Resolution.Normal, "Normal"),
        low(PrintImageSettings.Resolution.Low, "Low")
    }

    fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}