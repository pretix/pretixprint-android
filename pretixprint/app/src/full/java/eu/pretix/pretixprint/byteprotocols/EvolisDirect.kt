package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.evolis.libevolis.androidsdk.EvolisPrinter
import com.evolis.libevolis.androidsdk.model.*
import com.evolis.libevolis.androidsdk.model.info.ASDK_EvolisPrinterInfos
import eu.pretix.pretixprint.BuildConfig
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.imaging.BmpImageParser
import eu.pretix.pretixprint.ui.EvolisDirectSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import androidx.core.graphics.createBitmap


class EvolisDirect : CustomByteProtocol<Bitmap> {
    override val identifier = "EvolisDirect"
    override val defaultDPI = 300
    override val demopage = "demopage_cr80.pdf"

    override val nameResource = R.string.protocol_evolisdirect

    override fun allowedForUsecase(type: String): Boolean {
        if (Build.VERSION.SDK_INT < 22) {
            return false
        }
        return type != "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is NetworkConnection
    }

    override fun convertPageToBytes(
        img: Bitmap,
        isLastPage: Boolean,
        previousPage: Bitmap?,
        conf: Map<String, String>,
        type: String
    ): ByteArray {
        // Evolis does not cope well with transparency - it's just black.
        // So we're actively drawing the original picture on a white background.
        val backgroundedImage = createBitmap(img.width, img.height, img.config!!)
        val canvas = Canvas(backgroundedImage)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(img, 0f, 0f, null)

        val ostream = ByteArrayOutputStream()
        backgroundedImage.compress(Bitmap.CompressFormat.PNG, 0, ostream)
        return ostream.toByteArray()
    }

    override fun sendUSB(
        usbManager: UsbManager,
        usbDevice: UsbDevice,
        pages: List<CompletableFuture<ByteArray>>,
        pagegroups: List<Int>,
        conf: Map<String, String>,
        type: String,
        context: Context
    ) {
        if (!usbDevice.productName!!.contains(" ")) {
            throw PrintError("Does not look like an Evolis device (no space in product name)")
        }
        val parts = usbDevice.productName!!.trim().split(" ", limit = 2)
        val printer = EvolisPrinter(context, parts[0], parts[1])
        send(pages, pagegroups, printer, conf, type, context)
    }

    override fun sendNetwork(
        host: String,
        port: Int,
        pages: List<CompletableFuture<ByteArray>>,
        pagegroups: List<Int>,
        conf: Map<String, String>,
        type: String,
        context: Context
    ) {
        val printer = EvolisPrinter(context, host)
        send(pages, pagegroups, printer, conf, type, context)
    }

    override fun sendBluetooth(
        deviceAddress: String,
        pages: List<CompletableFuture<ByteArray>>,
        pagegroups: List<Int>,
        conf: Map<String, String>,
        type: String,
        context: Context
    ) {
        TODO("Not yet implemented")
    }

    private fun send(
        pages: List<CompletableFuture<ByteArray>>,
        pagegroups: List<Int>,
        printer: EvolisPrinter,
        conf: Map<String, String>,
        type: String,
        context: Context
    ) {
        fun getSetting(key: String, def: String): String {
            return conf[key] ?: PreferenceManager.getDefaultSharedPreferences(context).getString(key, def)!!
        }

        val future = CompletableFuture<Void>()
        future.completeAsync {
            if (Looper.myLooper() == null) Looper.prepare()
            var doubleSided = getSetting("hardware_${type}printer_doublesided", "false").toBoolean()
            val cardSource = getSetting("hardware_${type}printer_cardsource", "EVOLIS_IT_BOTH")
            val cardDestination =
                getSetting("hardware_${type}printer_carddestination", "EVOLIS_OT_STANDARD")
            val info = ASDK_EvolisPrinterInfos()
            val inforet = printer.ASDK_Infos_GetPrinterInfo(info)
            if (inforet != ASDK_RETURN_CODE.ASDK_NO_ERROR) {
                throw PrintError(inforet.toString())
            }

            doubleSided = (info.isHasFlip && doubleSided)
            checkStatus(printer)

            printer.ASDK_PrinterSettings_SetCardInsertion(ASDK_INTRAY.valueOf(cardSource))
            printer.ASDK_PrinterSettings_SetCardEjection(ASDK_OUTTRAY.valueOf(cardDestination))

            for (f in pages) {
                printer.ASDK_Print_ResetPrintData()
                val png = f.get(60, TimeUnit.SECONDS)
                val image = ImageIO.read(ByteArrayInputStream(png))
                //val parser = org.apache.commons.imaging.formats.bmp.BmpImageParser()
                val parser = BmpImageParser()
                val ostream = ByteArrayOutputStream()
                parser.writeImage(image, ostream, emptyMap())
                val bitmap = ostream.toByteArray()

                if (BuildConfig.DEBUG) {
                    val tmpfile = File.createTempFile("bitmap_", ".bmp", context.cacheDir)
                    tmpfile.writeBytes(bitmap)
                }

                printer.ASDK_Print_SetBitmap(ASDK_PRINTER_FACE.EVOLIS_PF_FRONT, bitmap)
                if (doubleSided) {
                    // If we are introducing native double-sided cards, we would load a new page here
                    printer.ASDK_Print_SetBitmap(ASDK_PRINTER_FACE.EVOLIS_PF_BACK, bitmap)
                }

                // Print overlay layer (todo: does this need to be configurable?)
                printer.ASDK_Print_SetParam("FOverlayManagement", "FULLVARNISH")

                val printret = printer.ASDK_Print_Print()
                if (printret != ASDK_RETURN_CODE.ASDK_NO_ERROR) {
                    throw PrintError(printret.toString())
                }
                checkStatus(printer)
            }
            Thread.sleep(2000)
            null
        }
        try {
            future.get(5, TimeUnit.MINUTES)
        } catch (e: ExecutionException) {
            e.printStackTrace()
            throw PrintError(e.cause?.message ?: e.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintError(e.message ?: e.toString())
        }
    }

    private fun checkStatus(printer: EvolisPrinter) {
        val start = System.currentTimeMillis()
        while (true) {
            val listStatus: List<ASDK_PRINTER_STATUS> = ArrayList()
            val returnValue = printer.ASDK_SupportTools_GetPrinterStatus(listStatus)
            if (returnValue != ASDK_RETURN_CODE.ASDK_NO_ERROR) {
                throw PrintError(returnValue.toString())
            } else {
                if (listStatus.contains(ASDK_PRINTER_STATUS.INF_BUSY)) {
                    if (System.currentTimeMillis() - start > 5 * 60 * 1000) {
                        throw PrintError("BUSY")
                    }
                    Log.i("EvolisDirect", "INF_BUSY")
                    Thread.sleep(250)
                } else if (listStatus.contains(ASDK_PRINTER_STATUS.INF_CLEANING_RUNNING)) {
                    throw PrintError("CLEANING_RUNNING")
                } else if (listStatus.contains(ASDK_PRINTER_STATUS.INF_UPDATING_FIRMWARE)) {
                    throw PrintError("UPDATING_FIRMWARE")
                } else if (listStatus.contains(ASDK_PRINTER_STATUS.INF_SLEEP_MODE)) {
                    throw PrintError("SLEEP_MODE")
                } else if (listStatus.contains(ASDK_PRINTER_STATUS.DEF_COVER_OPEN)) {
                    throw PrintError("COVER_OPEN")
                } else if (listStatus.contains(ASDK_PRINTER_STATUS.DEF_UNSUPPORTED_RIBBON)) {
                    throw PrintError("UNSUPPORTED_RIBBON")
                } else if (listStatus.contains(ASDK_PRINTER_STATUS.DEF_RIBBON_ENDED)) {
                    throw PrintError("RIBBON_ENDED")
                } else if (listStatus.contains(ASDK_PRINTER_STATUS.DEF_NO_RIBBON)) {
                    throw PrintError("NO_RIBBON")
                } else if (listStatus.contains(ASDK_PRINTER_STATUS.DEF_FEEDER_EMPTY)) {
                    throw PrintError("FEEDER_EMPTY")
                } else if (listStatus.contains(ASDK_PRINTER_STATUS.DEF_PRINTER_LOCKED)) {
                    throw PrintError("PRINTER_LOCKED")
                } else if (listStatus.contains(ASDK_PRINTER_STATUS.DEF_HOPPER_FULL)) {
                    throw PrintError("HOPPER_FULL")
                } else if (listStatus.contains(ASDK_PRINTER_STATUS.DEF_REJECT_BOX_FULL)) {
                    throw PrintError("REJECT_BOX_FULL")
                } else {
                    break
                }
            }
        }
    }

    override fun createSettingsFragment(): SetupFragment {
        return EvolisDirectSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}
