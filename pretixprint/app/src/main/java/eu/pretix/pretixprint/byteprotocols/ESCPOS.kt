package eu.pretix.pretixprint.byteprotocols

import android.util.Log
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.ui.ESCPOSSettingsFragment
import eu.pretix.pretixprint.ui.SetupFragment
import java8.util.concurrent.CompletableFuture
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit


open class ESCPOS : StreamByteProtocol<ByteArray> {
    override val identifier = "ESC/POS"
    override val nameResource = R.string.protocol_escpos
    override val defaultDPI = 200
    override val demopage = "demopage.txt"

    override fun allowedForUsecase(type: String): Boolean {
        return type == "receipt"
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return true
    }

    override fun convertPageToBytes(img: ByteArray, isLastPage: Boolean, previousPage: ByteArray?, conf: Map<String, String>, type: String): ByteArray {
        return img
    }

    override fun send(
        pages: List<CompletableFuture<ByteArray>>,
        istream: InputStream,
        ostream: OutputStream,
        conf: Map<String, String>,
        type: String,
        waitAfterPage: Long
    ) {
        for (f in pages) {
            Log.i("PrintService", "[$type] Waiting for page to be converted")
            val page = f.get(60, TimeUnit.SECONDS)
            Log.i("PrintService", "[$type] Page ready, sending page")
            ostream.write(page)
            ostream.flush()
            Log.i("PrintService", "[$type] Page sent, sleep after page")
            Thread.sleep(waitAfterPage)
        }
   }

    override fun createSettingsFragment(): SetupFragment? {
        return ESCPOSSettingsFragment()
    }

    override fun inputClass(): Class<ByteArray> {
        return ByteArray::class.java
    }
}