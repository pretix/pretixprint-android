package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Looper
import androidx.fragment.app.Fragment
import com.zebra.sdk.comm.BluetoothConnectionInsecure
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.TcpConnection
import com.zebra.sdk.comm.UsbConnection
import com.zebra.sdk.common.card.containers.GraphicsInfo
import com.zebra.sdk.common.card.enumerations.*
import com.zebra.sdk.common.card.graphics.ZebraCardGraphics
import com.zebra.sdk.common.card.graphics.ZebraCardImageI
import com.zebra.sdk.common.card.graphics.ZebraGraphics
import com.zebra.sdk.common.card.graphics.enumerations.RotationType
import com.zebra.sdk.common.card.jobSettings.ZebraCardJobSettingNames
import com.zebra.sdk.common.card.printer.ZebraCardPrinter
import com.zebra.sdk.common.card.printer.ZebraCardPrinterFactory
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.ui.LinkOSCardSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import org.jetbrains.anko.defaultSharedPreferences
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit


class LinkOSCard : CustomByteProtocol<Bitmap> {
    override val identifier = "LinkOSCard"
    override val defaultDPI = 300
    override val demopage = "CR80.pdf"

    override val nameResource = R.string.protocol_linkoscard

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return true
    }

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?, conf: Map<String, String>, type: String): ByteArray {
        val ostream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.PNG, 0, ostream)
        return ostream.toByteArray()
    }

    override fun sendUSB(usbManager: UsbManager, usbDevice: UsbDevice, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val connection = UsbConnection(usbManager, usbDevice)
        try {
            connection.open()
            send(pages, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    override fun sendNetwork(host: String, port: Int, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val connection = TcpConnection(host, port)
        try {
            connection.open()
            send(pages, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    override fun sendBluetooth(deviceAddress: String, pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        val connection = BluetoothConnectionInsecure(deviceAddress)
        try {
            connection.open()
            send(pages, connection, conf, type, context)
        } finally {
            connection.close()
        }
    }

    private fun send(pages: List<CompletableFuture<ByteArray>>, connection: Connection, conf: Map<String, String>, type: String, context: Context) {
        fun getSetting(key: String, def: String): String {
            return conf[key] ?: context.defaultSharedPreferences.getString(key, def)!!
        }

        // ToDo: Make the printer connection blocking, displaying an error message if appropriate.
        Thread {
            Looper.prepare()
            var zebraCardPrinter: ZebraCardPrinter? = null

            try {
                var doubleSided = getSetting("hardware_${type}printer_doublesided", "false").toBoolean()
                val cardSource = getSetting("hardware_${type}printer_cardsource", "AutoDetect")
                val cardDestination = getSetting("hardware_${type}printer_carddestination", "Eject")

                zebraCardPrinter = ZebraCardPrinterFactory.getInstance(connection)

                doubleSided = (zebraCardPrinter.printCapability == TransferType.DualSided && doubleSided)


                for (f in pages) {
                    zebraCardPrinter.setJobSetting(ZebraCardJobSettingNames.CARD_SOURCE, cardSource)
                    zebraCardPrinter.setJobSetting(ZebraCardJobSettingNames.CARD_DESTINATION, cardDestination)

                    val graphicsData = drawGraphics(zebraCardPrinter, f.get(60, TimeUnit.SECONDS), doubleSided, context)

                    zebraCardPrinter.print(1, graphicsData)
                }
                Thread.sleep(2000)
            } catch (e: Exception) {
                e.printStackTrace()
                throw PrintError(e.message ?: e.toString())
            } finally {
                zebraCardPrinter?.destroy()
            }
        }.start()
    }

    private fun drawGraphics(zebraCardPrinter: ZebraCardPrinter, imageData: ByteArray, doubleSided: Boolean, context: Context) : List<GraphicsInfo> {
        val graphicsData = ArrayList<GraphicsInfo>()
        var graphics: ZebraGraphics? = null
        try {
            graphics = ZebraCardGraphics(zebraCardPrinter)

            // Front Side
            val zebraCardImage = drawImage(graphics, PrintType.Color, imageData, 0, 0, 0, 0, context)
            graphicsData.add(addImage(CardSide.Front, PrintType.Color, 0, 0, -1, zebraCardImage))

            // Front Side Overlay
            graphicsData.add(addImage(CardSide.Front, PrintType.Overlay, 0, 0, 1, null));

            if (doubleSided) {
                // Back Side
                // If we are introducing native double-sided cards, we would load a new page here
                graphicsData.add(addImage(CardSide.Back, PrintType.Color, 0, 0, -1, zebraCardImage))

                // Back Side Overlay
                graphicsData.add(addImage(CardSide.Back, PrintType.Overlay, 0, 0, 1, null));
            }
            // If we are introducing native double-sided cards, this needs to be placed after the front side
            graphics.clear()
        } finally {
            graphics?.close()
        }

        return graphicsData
    }

    private fun drawImage(graphics: ZebraGraphics, printType: PrintType, imageData: ByteArray, xOffset: Int, yOffset: Int, width: Int, height: Int, context: Context) : ZebraCardImageI {
        graphics.initialize(context, 0, 0, OrientationType.Landscape, printType, Color.WHITE)
        graphics.drawImage(imageData, xOffset, yOffset, width, height, RotationType.RotateNoneFlipNone)
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

    override fun createSettingsFragment(): SetupFragment? {
        return LinkOSCardSettingsFragment()
    }

    override fun inputClass(): Class<Bitmap> {
        return Bitmap::class.java
    }
}
