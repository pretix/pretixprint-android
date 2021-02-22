package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import com.zebra.sdk.comm.Connection
import com.zebra.sdk.printer.ZebraPrinter
import com.zebra.sdk.printer.ZebraPrinterFactory
import eu.pretix.pretixprint.R
import java8.util.concurrent.CompletableFuture
import org.jetbrains.anko.defaultSharedPreferences
import java.io.ByteArrayOutputStream
import java.io.IOException


class LinkOS : ZebraByteProtocol<Bitmap> {
    override val identifier = "LinkOS"
    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }
    override val defaultDPI = 203
    override val demopage = "demopage_8in_3.25in.pdf"

    override val nameResource = R.string.protocol_linkos

    override fun convertPageToBytes(img: Bitmap, isLastPage: Boolean, previousPage: Bitmap?): ByteArray {
        val ostream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.PNG, 0, ostream)
        return ostream.toByteArray()
    }

    override fun send(pages: List<CompletableFuture<ByteArray>>, connection: Connection, conf: Map<String, String>, type: String, context: Context) {
        fun getSetting(key: String, def: String): String {
            return conf[key] ?: context.defaultSharedPreferences.getString(key, def)!!
        }

        // ToDo: Make the printer connection blocking, displaying an error message if appropriate.
        Thread {
            Looper.prepare()
            var zebraPrinter: ZebraPrinter? = null

            try {
                zebraPrinter = ZebraPrinterFactory.getInstance(connection)

                for (f in pages) {
                    // ToDo: Proper path or use ZebraImage
                    zebraPrinter.printImage("/path/to/graphics.jpg", 0, 0)
                }
                Thread.sleep(2000)
            } catch (e: Exception) {
                e.printStackTrace()
                throw IOException(e.message)
            }
        }.start()
    }
}