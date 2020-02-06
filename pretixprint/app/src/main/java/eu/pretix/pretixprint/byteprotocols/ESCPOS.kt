package eu.pretix.pretixprint.byteprotocols

import eu.pretix.pretixprint.R
import java8.util.concurrent.CompletableFuture
import java.io.InputStream
import java.io.OutputStream


class ESCPOS : ByteProtocol<ByteArray> {
    override val identifier = "ESC/POS"
    override val nameResource = R.string.protocol_escpos

    override fun allowedForUsecase(type: String): Boolean {
        return type == "receipt"
    }

    override fun convertPageToBytes(img: ByteArray, isLastPage: Boolean, previousPage: ByteArray?): ByteArray {
        return img
    }

    override fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream) {
        for (f in pages) {
            ostream.write(f.get())
            ostream.flush()
        }
    }
}