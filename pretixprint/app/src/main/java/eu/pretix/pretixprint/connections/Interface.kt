package eu.pretix.pretixprint.connections

import android.content.Context
import androidx.preference.PreferenceManager
import java8.util.concurrent.CompletableFuture
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface ConnectionType {
    enum class Input {
        PDF,
        PLAIN_BYTES
    }

    val identifier: String
    val nameResource: Int
    val inputType: Input

    fun allowedForUsecase(type: String): Boolean
    fun print(tmpfile: File, numPages: Int, pagegroups: List<Int>, context: Context, useCase: String, settings: Map<String, String>? = null, done: (() -> Unit))

    fun isConfiguredFor(context: Context, type: String): Boolean {
        return !PreferenceManager.getDefaultSharedPreferences(context).getString("hardware_${type}printer_ip", "").isNullOrEmpty()
    }
    fun connect(context: Context, type: String): CompletableFuture<StreamHolder>
}

open class StreamHolder(val inputStream: InputStream, val outputStream: OutputStream) : Closeable {
    override fun close() {
        inputStream.close()
        outputStream.close()
    }
}

class SocketStreamHolder(inputStream: InputStream, outputStream: OutputStream, val socket: Closeable): StreamHolder(inputStream, outputStream) {
    override fun close() {
        super.close()
        socket.close()
    }
}