package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.comm.TcpConnection
import com.zebra.sdk.common.card.containers.GraphicsInfo
import com.zebra.sdk.common.card.enumerations.*
import com.zebra.sdk.common.card.exceptions.ZebraCardException
import com.zebra.sdk.common.card.graphics.ZebraCardGraphics
import com.zebra.sdk.common.card.graphics.ZebraCardImageI
import com.zebra.sdk.common.card.graphics.ZebraGraphics
import com.zebra.sdk.common.card.graphics.enumerations.RotationType
import com.zebra.sdk.common.card.jobSettings.ZebraCardJobSettingNames
import com.zebra.sdk.common.card.printer.ZebraCardPrinter
import com.zebra.sdk.common.card.printer.ZebraCardPrinterFactory
import eu.pretix.pretixprint.R
import java8.util.concurrent.CompletableFuture
import org.jetbrains.anko.defaultSharedPreferences
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


class LinkOSCard : ByteProtocol<Bitmap> {
    override val identifier = "LinkOSCard"
    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override val nameResource = R.string.protocol_linkoscard

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?): ByteArray {
        val ostream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.PNG, 0, ostream)
        return ostream.toByteArray()
    }

    override fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream) {
        throw PrintError("LinkOSCard uses the other send() function!")
    }

    fun send(pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        fun getSetting(key: String, def: String): String {
            return conf[key] ?: context.defaultSharedPreferences.getString(key, def)!!
        }

        // ToDo: Make the printer connection blocking, displaying an error message if appropriate.
        Thread {
            Looper.prepare()
            var connection: Connection? = null
            var zebraCardPrinter: ZebraCardPrinter? = null

            try {
                val serverAddr = getSetting("hardware_${type}printer_ip", "127.0.0.1")
                val port = Integer.valueOf(getSetting("hardware_${type}printer_port", "9100"))
                var doubleSided = getSetting("hardware_${type}printer_doublesided", "false").toBoolean()
                val cardSource = getSetting("hardware_${type}printer_cardsource", "AutoDetect")
                val cardDestination = getSetting("hardware_${type}printer_carddestination", "Eject")

                connection = TcpConnection(serverAddr, port)
                connection.open()

                zebraCardPrinter = ZebraCardPrinterFactory.getInstance(connection)

                doubleSided = (zebraCardPrinter.printCapability == TransferType.DualSided && doubleSided)

                for (f in pages) {
                    zebraCardPrinter.setJobSetting(ZebraCardJobSettingNames.CARD_SOURCE, cardSource)
                    zebraCardPrinter.setJobSetting(ZebraCardJobSettingNames.CARD_DESTINATION, cardDestination)

                    val graphicsData = drawGraphics(zebraCardPrinter, f.get(), doubleSided, context)

                    zebraCardPrinter.print(1, graphicsData)
                }
                Thread.sleep(2000)
            } catch (e: Exception) {
                e.printStackTrace()
                throw IOException(e.message)
            } finally {
                cleanUp(connection, zebraCardPrinter)
            }
        }.start()
    }

    fun cleanUp(connection: Connection?, zebraCardPrinter: ZebraCardPrinter?) {
        try {
            zebraCardPrinter?.destroy()
        } catch (e: ZebraCardException) {
            e.printStackTrace()
        }

        try {
            connection?.close()
        } catch (e: ConnectionException) {
            e.printStackTrace()
        }
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
}
