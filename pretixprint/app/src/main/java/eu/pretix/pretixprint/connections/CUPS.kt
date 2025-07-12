package eu.pretix.pretixprint.connections

import android.content.Context
import androidx.preference.PreferenceManager
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.print.getPrinter
import io.sentry.Sentry
import org.cups4j.CupsPrinter
import org.cups4j.PrintJob
import java.io.File
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class CUPSConnection : ConnectionType {
    override val identifier = "cups"
    override val nameResource = R.string.connection_type_cups
    override val inputType = ConnectionType.Input.PDF

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun print(
        tmpfile: File,
        numPages: Int,
        pagegroups: List<Int>,
        context: Context,
        type: String,
        settings: Map<String, String>?,
        done: () -> Unit
    ) {
        val conf = settings ?: emptyMap()
        fun getSetting(key: String, def: String): String {
            return conf!![key] ?: PreferenceManager.getDefaultSharedPreferences(context).getString(key, def)!!
        }

        val serverAddr = getSetting("hardware_${type}printer_ip", "127.0.0.1")
        val port = getSetting("hardware_${type}printer_port", "631")
        val name = getSetting("hardware_${type}printer_printername", "Test")

        Sentry.configureScope { scope ->
            scope.setTag("printer.type", type)
            scope.setContexts("printer.ip", serverAddr)
            scope.setContexts("printer.port", port)
            scope.setContexts("printer.printername", name)
        }

        var cp: CupsPrinter? = null
        try {
            cp = getPrinter(
                    serverAddr,
                    port,
                    name
            )
        } catch (e: IOException) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
        }
        if (cp == null) {
            throw PrintException(context.applicationContext.getString(R.string.err_printer_not_found, name))
        } else {
            try {
                val pj = PrintJob.Builder(tmpfile.readBytes()).build()
                cp.print(pj)
                done()
            } catch (e: IOException) {
                e.printStackTrace()
                throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
            }
        }
    }

    override suspend fun connectAsync(context: Context, type: String): StreamHolder = suspendCoroutine { cont ->
        cont.resumeWithException(NotImplementedError("raw connection is not available for cups"))
    }
}