package eu.pretix.pretixprint.byteprotocols

import android.content.Context
import com.zebra.sdk.comm.Connection
import java8.util.concurrent.CompletableFuture
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

interface ByteProtocolInterface<T> {
    val identifier: String
    val nameResource: Int
    val defaultDPI: Int
    val demopage: String

    fun allowedForUsecase(type: String): Boolean
    fun convertPageToBytes(img: T, isLastPage: Boolean, previousPage: T?): ByteArray
}

interface StreamByteProtocol<T> : ByteProtocolInterface<T> {
    fun send(pages: List<CompletableFuture<ByteArray>>, istream: InputStream, ostream: OutputStream)
}

interface ZebraByteProtocol<T> : ByteProtocolInterface<T> {
    fun send(pages: List<CompletableFuture<ByteArray>>, connection: Connection, conf: Map<String, String>, type: String, context: Context)
}

fun getProtoClass(proto: String): ByteProtocolInterface<Any> {
    return when (proto) {
        "FGL" -> {
            FGL()
        }
        "SLCS" -> {
            SLCS()
        }
        "ESC/POS" -> {
            ESCPOS()
        }
        "LinkOSCard" -> {
            LinkOSCard()
        }
        "LinkOS" -> {
            LinkOS()
        }
        else -> {
            FGL()
        }
    } as ByteProtocolInterface<Any>
}

class PrintError(message: String) : IOException(message);
