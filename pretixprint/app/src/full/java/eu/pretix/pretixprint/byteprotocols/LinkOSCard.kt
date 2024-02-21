package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Looper
import androidx.preference.PreferenceManager
import com.zebra.sdk.comm.*
import com.zebra.sdk.common.card.containers.GraphicsInfo
import com.zebra.sdk.common.card.enumerations.*
import com.zebra.sdk.common.card.graphics.ZebraCardGraphics
import com.zebra.sdk.common.card.graphics.ZebraCardImageI
import com.zebra.sdk.common.card.graphics.ZebraGraphics
import com.zebra.sdk.common.card.graphics.enumerations.RotationType
import com.zebra.sdk.common.card.jobSettings.ZebraCardJobSettingNames
import com.zebra.sdk.common.card.printer.ZebraCardPrinter
import com.zebra.sdk.common.card.printer.ZebraCardPrinterFactory
import com.zebra.sdk.common.card.settings.ZebraCardSettingNames
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.connections.USBConnection
import eu.pretix.pretixprint.connections.BluetoothConnection
import eu.pretix.pretixprint.ui.LinkOSCardSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO


class LinkOSCard : CustomByteProtocol<Bitmap> {
    override val identifier = "LinkOSCard"
    override val defaultDPI = 300
    override val demopage = "demopage_cr80.pdf"

    override val nameResource = R.string.protocol_linkoscard

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is USBConnection || type is NetworkConnection || type is BluetoothConnection
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val ostream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.PNG, 0, ostream)
        return ostream.toByteArray()
    }

    override fun sendUSB(usbManager: UsbManager, usbDevice: UsbDevice, pages: List<CompletableFuture<ByteArray>>, pagegroups: List<Int>, conf: Map<String, String>, type: String, context: Context) {
        val connection = UsbConnection(usbManager, usbDevice)
        try {
            connection.open()
            send(pages, pagegroups, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    override fun sendNetwork(host: String, port: Int, pages: List<CompletableFuture<ByteArray>>, pagegroups: List<Int>, conf: Map<String, String>, type: String, context: Context) {
        val connection = TcpConnection(host, port)
        try {
            connection.open()
            send(pages, pagegroups, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    override fun sendBluetooth(deviceAddress: String, pages: List<CompletableFuture<ByteArray>>, pagegroups: List<Int>, conf: Map<String, String>, type: String, context: Context) {
        val connection = BluetoothConnectionInsecure(deviceAddress)
        try {
            connection.open()
            send(pages, pagegroups, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    private fun send(pages: List<CompletableFuture<ByteArray>>, pagegroups: List<Int>, connection: Connection, conf: Map<String, String>, type: String, context: Context) {
        fun getSetting(key: String, def: String): String {
            return conf[key] ?: PreferenceManager.getDefaultSharedPreferences(context).getString(key, def)!!
        }

        val future = CompletableFuture<Void>()
        future.completeAsync {
            if (Looper.myLooper() == null) Looper.prepare()
            var zebraCardPrinter: ZebraCardPrinter? = null

            try {
                var forceDoubleSided = getSetting("hardware_${type}printer_doublesided", "false").toBoolean()
                var canDoubleSided = false
                val cardSource = getSetting("hardware_${type}printer_cardsource", "AutoDetect")
                val cardDestination = getSetting("hardware_${type}printer_carddestination", "Eject")

                zebraCardPrinter = ZebraCardPrinterFactory.getInstance(connection)

                canDoubleSided = zebraCardPrinter.printCapability == TransferType.DualSided
                forceDoubleSided = (canDoubleSided && forceDoubleSided)


                var printJobs = mutableListOf<List<GraphicsInfo>>()
                var pageoffset = 0
                for (groupsize in pagegroups) {
                    if (forceDoubleSided) {
                        // User requested to print everything double sided, so we will - no matter what.
                        // A 2-page document will in this case yield 2 cards:
                        // - card 1: page 1 + page 1
                        // - card 2: page 2 + page 2
                        for (i in 0 until groupsize) {
                            printJobs.add(drawGraphics(zebraCardPrinter, pages[pageoffset + i].get(60, TimeUnit.SECONDS), listOf(CardSide.Front, CardSide.Back), context))
                        }
                    } else if (canDoubleSided && groupsize > 1) {
                        // User did not requested everything to be printer double sided, but the printer
                        // is still capable printing on both sides and the current group of pages is actually
                        // more than one page. In this case, we print page 1 and 2 on one card.
                        // Even remainders of the pagegroup will be processed as double sided prints,
                        // any remainder will be printed on a single-sided card.
                        for (i in 0 until groupsize) {
                            if (i % 2 == 0 && i == groupsize-1) {
                                // Even page (0, 2, 4, ...) and at last page of the group
                                // --> Last page to print onto a single sided card
                                printJobs.add(drawGraphics(zebraCardPrinter, pages[pageoffset + i].get(60, TimeUnit.SECONDS), listOf(CardSide.Front), context))
                            } else if (i % 2 == 0) {
                                // Even page (0, 2, 4, ...) but not last page of the group
                                // --> CardSide.Front of a double sided card.
                                printJobs.add(drawGraphics(zebraCardPrinter, pages[pageoffset + i].get(60, TimeUnit.SECONDS), listOf(CardSide.Front), context))
                            } else {
                                // Uneven page (1, 3, 5, ...)
                                // --> CardSide.Back of a double sided card.
                                printJobs[printJobs.lastIndex] = printJobs[printJobs.lastIndex] + drawGraphics(zebraCardPrinter, pages[pageoffset + i].get(60, TimeUnit.SECONDS), listOf(CardSide.Back), context)
                            }
                        }
                    } else {
                        // One page goes on one side of one card.
                        for (i in 0 until groupsize) {
                            printJobs.add(drawGraphics(zebraCardPrinter, pages[pageoffset + i].get(60, TimeUnit.SECONDS), listOf(CardSide.Front), context))
                        }
                    }
                    pageoffset += groupsize
                }

                for (job in printJobs) {
                    zebraCardPrinter.setJobSetting(ZebraCardJobSettingNames.CARD_SOURCE, cardSource)
                    zebraCardPrinter.setJobSetting(ZebraCardJobSettingNames.CARD_DESTINATION, cardDestination)
                    zebraCardPrinter.setJobSetting(ZebraCardJobSettingNames.DELETE_AFTER, "yes")

                    val jobId = zebraCardPrinter.print(1, job)

                    // Zebra Card printers only have a limited Job Buffer - in the case of the ZC-Series, it's
                    // 4 print jobs.
                    // This infinite loop is on purpose, as having a page-local timeout could lead to lost
                    // pages on giant print jobs and a global timeout could lead to prematurely aborted
                    // print jobs missing pages at the end.
                    // However: We are polling for with a jobId and are checking if the printer is
                    // readyForNextJob, which offers two ways out of a stuck print job and by extension,
                    // an infinite loop:
                    // - Either the blocking condition (empty ribbon, no more cards, stuck cards...) is
                    //   resolved and the printer reverts to being readyForNextJob and the job is completed
                    //   as intended, breaking the loop.
                    // - Or the printer is power-cycled, leading to requesting the jobStatus with a
                    //   specific jobId to throw an exception. In this case the loop is still broken and
                    //   the print job spooling prematurely ended.
                    while (true) {
                        val jobStatusInfo = zebraCardPrinter.getJobStatus(jobId)
                        if (jobStatusInfo.readyForNextJob) {
                            break
                        } else {
                            Thread.sleep(2000)
                        }
                    }
                }
                Thread.sleep(2000)
            } finally {
                zebraCardPrinter?.destroy()
            }
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

    private fun drawGraphics(zebraCardPrinter: ZebraCardPrinter, imageData: ByteArray, cardSides: List<CardSide>, context: Context) : List<GraphicsInfo> {
        val graphicsData = ArrayList<GraphicsInfo>()
        var graphics: ZebraGraphics? = null
        try {
            graphics = ZebraCardGraphics(zebraCardPrinter)

            val COLOR_OPTION = "ymc"
            val MONO_RIBBON_OPTIONS = listOf("k", "mono", "black", "white", "red", "blue", "silver", "gold")
            val OVERLAY_RIBBON_OPTIONS = listOf("ymcko", "kro", "kdo")

            val installedRibbon = zebraCardPrinter.getSettingValue(ZebraCardSettingNames.RIBBON_DESCRIPTION)

            // This is grossly simplifying - in reality there are more options than just Color or MonoK - GrayDye for example
            val printType = if (installedRibbon.contains(COLOR_OPTION, true)) { PrintType.Color } else { PrintType.MonoK }

            for (cardSide in cardSides) {
                // Image
                val zebraCardImage = drawImage(graphics, printType, imageData, 0, 0, 0, 0, context)
                graphicsData.add(addImage(cardSide, printType, 0, 0, -1, zebraCardImage))

                // Overlay
                if (isPrintTypeSupported(installedRibbon, OVERLAY_RIBBON_OPTIONS)) {
                    graphicsData.add(addImage(cardSide, PrintType.Overlay, 0, 0, 1, null))
                }

            }
            graphics.clear()
        } finally {
            graphics?.close()
        }

        return graphicsData
    }

    private fun drawImage(graphics: ZebraGraphics, printType: PrintType, imageData: ByteArray, xOffset: Int, yOffset: Int, width: Int, height: Int, context: Context) : ZebraCardImageI {
        val image = ImageIO.read(ByteArrayInputStream(imageData))
        val rotation = if (image.width > image.height) {
            RotationType.RotateNoneFlipNone
        } else {
            RotationType.Rotate90FlipNone
        }
        graphics.initialize(context, 0, 0, OrientationType.Landscape, printType, Color.WHITE)
        graphics.drawImage(imageData, xOffset, yOffset, width, height, rotation)
        return graphics.createImage()
    }

    private fun addImage(side: CardSide, printType: PrintType, xOffset: Int, yOffset: Int, fillColor: Int, zebraCardImage: ZebraCardImageI?) : GraphicsInfo {
        val graphicsInfo = GraphicsInfo()
        graphicsInfo.fillColor = fillColor
        graphicsInfo.graphicData = zebraCardImage
        graphicsInfo.graphicType = if (zebraCardImage != null) GraphicType.BMP else GraphicType.NA
        graphicsInfo.opacity = 0
        graphicsInfo.overprint = false
        graphicsInfo.printType = printType
        graphicsInfo.side = side
        graphicsInfo.xOffset = xOffset
        graphicsInfo.yOffset = yOffset
        return graphicsInfo
    }

    private fun isPrintTypeSupported(installedRibbon: String, ribbonTypeOptions: List<String>): Boolean {
        var isSupported = true
        for (option in ribbonTypeOptions) {
            if (!installedRibbon.contains(option, true)) {
                isSupported = false
            } else {
                isSupported = true
                break
            }
        }
        return isSupported
    }


    override fun createSettingsFragment(): SetupFragment? {
        return LinkOSCardSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}
