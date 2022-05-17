package eu.pretix.pretixprint.connections

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.PrinterSdk.PrinterListen
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.*
import eu.pretix.pretixprint.print.lockManager
import eu.pretix.pretixprint.renderers.renderPages
import io.sentry.Sentry
import java8.util.concurrent.CompletableFuture
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class SunmiInternalConnection : ConnectionType {
    override val identifier = "sunmi"
    override val nameResource = R.string.connection_type_sunmi
    override val inputType = ConnectionType.Input.PDF

    override fun allowedForUsecase(type: String): Boolean {
        return Build.BRAND.uppercase() == "SUNMI"
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
        val conf = settings?.toMutableMap() ?: mutableMapOf()
        for (entry in PreferenceManager.getDefaultSharedPreferences(context).all.iterator()) {
            if (!conf.containsKey(entry.key)) {
                conf[entry.key] = entry.value.toString()
            }
        }

        val future = CompletableFuture<Void>()

        val mode = if (type == "receipt") "ESC/POS" else "PNG"
        val proto = getProtoClass(mode)
        val dpi = Integer.valueOf(conf.get("hardware_${type}printer_dpi")
                ?: proto.defaultDPI.toString()).toFloat()
        val rotation = Integer.valueOf(conf.get("hardware_${type}printer_rotation") ?: "0")

        Sentry.configureScope { scope ->
            scope.setTag("printer.mode", mode)
            scope.setTag("printer.type", type)
            scope.setContexts("printer.dpi", dpi)
            scope.setContexts("printer.rotation", rotation)
        }

        try {
            lockManager.withLock("sunmi") {
                Log.i("PrintService", "[$type] Starting renderPages")
                val futures = renderPages(proto, tmpfile, dpi, rotation, numPages, conf, type)
                val wap = Integer.valueOf(conf.get("hardware_${type}printer_waitafterpage") ?: "100").toLong()

                when (proto) {
                    is StreamByteProtocol<*> -> {
                        for (f in futures) {
                            Log.i("PrintService", "[$type] Waiting for page to be converted")
                            val page = f.get(60, TimeUnit.SECONDS)
                            Log.i("PrintService", "[$type] Page ready, sending page")

                            PrinterSdk.getInstance().getPrinter(context, object : PrinterListen {
                                override fun onDefPrinter(printer: PrinterSdk.Printer?) {
                                    if (printer != null) {
                                        try {
                                            printer.commandApi().sendEscCommand(page)
                                            future.complete(null)
                                        } catch (e: Exception) {
                                            future.completeExceptionally(e)
                                        }
                                    }
                                }

                                override fun onPrinters(printers: MutableList<PrinterSdk.Printer>?) {
                                }

                            })
                            future.get(60, TimeUnit.SECONDS)
                            Log.i("PrintService", "[$type] Page sent, sleep $wap after page")
                            Thread.sleep(wap)
                            Log.i("PrintService", "[$type] Sleep done")
                        }
                        done()
                    }

                    is SunmiByteProtocol<*> -> {
                        PrinterSdk.getInstance().getPrinter(context, object : PrinterListen {
                            override fun onDefPrinter(printer: PrinterSdk.Printer?) {
                                if (printer != null) {
                                    try {
                                        proto.sendSunmi(printer, futures, pagegroups, conf, type, wap)
                                        future.complete(null)
                                        done()
                                    } catch (e: Exception) {
                                        future.completeExceptionally(e)
                                    }
                                }
                            }

                            override fun onPrinters(printers: MutableList<PrinterSdk.Printer>?) {
                            }

                        })
                        future.complete(null)
                    }

                    is CustomByteProtocol<*> -> {
                        throw RuntimeException("Combination not supported")
                    }
                }
            }
        } catch (e: TimeoutException) {
            e.printStackTrace()
            throw PrintException("Rendering timeout, thread may have crashed")
        } catch (e: PrintError) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
        } catch (e: IOException) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
        }

        try {
            future.get(5, TimeUnit.MINUTES)
        } catch (e: ExecutionException) {
            e.printStackTrace()
            throw PrintError(e.cause?.message ?: e.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintError(e.message ?: e.toString())
        }
    }

    override fun isConfiguredFor(context: Context, type: String): Boolean {
        return true
    }

    override suspend fun connectAsync(context: Context, type: String): StreamHolder = suspendCoroutine { cont ->
        cont.resumeWithException(NotImplementedError("raw connection is not available for Sunmi"))
    }
}