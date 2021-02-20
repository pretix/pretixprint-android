package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.comm.TcpConnection
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import eu.pretix.pretixprint.R
import java8.util.concurrent.CompletableFuture
import org.jetbrains.anko.defaultSharedPreferences
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


class LinkOS : ByteProtocol<Bitmap> {
    override val identifier = "LinkOS"
    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override val nameResource = R.string.protocol_linkos

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?): ByteArray {
        val ostream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.PNG, 0, ostream)
        return ostream.toByteArray()
    }

    override fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream) {
        throw PrintError("LinkOS uses the other send() function!")
    }

    fun send(pages: List<CompletableFuture<ByteArray>>, conf: Map<String, String>, type: String, context: Context) {
        fun getSetting(key: String, def: String): String {
            return conf[key] ?: context.defaultSharedPreferences.getString(key, def)!!
        }

        // ToDo: Make the printer connection blocking, displaying an error message if appropriate.
        Thread {
            Looper.prepare()
            var connection: Connection? = null
            var zebraPrinter: ZebraPrinter? = null

            try {
                val serverAddr = getSetting("hardware_${type}printer_ip", "127.0.0.1")
                val port = Integer.valueOf(getSetting("hardware_${type}printer_port", "9100"))

                connection = TcpConnection(serverAddr, port)
                connection.open()

                zebraPrinter = ZebraPrinterFactory.getInstance(connection)


                for (f in pages) {
                    // ToDo: Proper path or use ZebraImage
                    zebraPrinter.printImage("/path/to/graphics.jpg", 0, 0)
                }
                Thread.sleep(2000)
            } catch (e: Exception) {
                e.printStackTrace()
                throw IOException(e.message)
            } finally {
                cleanUp(connection)
            }
        }.start()
    }

    fun cleanUp(connection: Connection?) {
        try {
            connection?.close()
        } catch (e: ConnectionException) {
            e.printStackTrace()
        }
    }
}

