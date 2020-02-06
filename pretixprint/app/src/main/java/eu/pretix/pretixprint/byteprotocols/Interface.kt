package eu.pretix.pretixprint.byteprotocols

import android.graphics.Bitmap
import java8.util.concurrent.CompletableFuture
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

interface ByteProtocol<T> {
    val identifier: String
    val nameResource: Int

    fun allowedForUsecase(type: String): Boolean
    fun convertPageToBytes(img: T, isLastPage: Boolean, previousPage: T?): ByteArray
    fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream)
}

class PrintError(message: String) : IOException(message);
