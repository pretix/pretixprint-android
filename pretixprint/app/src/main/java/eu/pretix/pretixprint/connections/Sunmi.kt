package eu.pretix.pretixprint.connections

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.PrinterSdk.PrinterListen
import com.sunmi.printerx.style.FileStyle
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.*
import eu.pretix.pretixprint.print.lockManager
import eu.pretix.pretixprint.renderers.renderPages
import io.sentry.Sentry
import java8.util.concurrent.CompletableFuture
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


class SunmiInternalConnection : ConnectionType {
    override val identifier = "sunmi"
    override val nameResource = R.string.connection_type_sunmi
    override val inputType = ConnectionType.Input.PDF

    override fun allowedForUsecase(type: String): Boolean {
        return Build.BRAND.uppercase() == "SUNMI"
    }

    override fun print(tmpfile: File, numPages: Int, context: Context, type: String, settings: Map<String, String>?) {
        val conf = settings?.toMutableMap() ?: mutableMapOf()
        for (entry in PreferenceManager.getDefaultSharedPreferences(context).all.iterator()) {
            if (!conf.containsKey(entry.key)) {
                conf[entry.key] = entry.value.toString()
            }
        }

        val future = CompletableFuture<Void>()
        val baos = ByteArrayOutputStream()
        val bais = ByteArrayInputStream(byteArrayOf())

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

                when (proto) {
                    is StreamByteProtocol<*> -> {
                        val wap = Integer.valueOf(conf.get("hardware_${type}printer_waitafterpage") ?: "100").toLong()
                        proto.send(futures, bais, baos, conf, type, 0L)
                        PrinterSdk.getInstance().getPrinter(context, object : PrinterListen {
                            override fun onDefPrinter(printer: PrinterSdk.Printer?) {
                                if (printer != null) {
                                    try {
                                        printer.commandApi().sendEscCommand(baos.toByteArray())
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
                        Thread.sleep(wap)
                    }

                    is SunmiByteProtocol<*> -> {
                        PrinterSdk.getInstance().getPrinter(context, object : PrinterListen {
                            override fun onDefPrinter(printer: PrinterSdk.Printer?) {
                                if (printer != null) {
                                    try {
                                        proto.sendSunmi(printer, futures, conf, type)
                                        future.complete(null)
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
}