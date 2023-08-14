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
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.*
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

    override fun print(tmpfile: File, numPages: Int, context: Context, useCase: String, settings: Map<String, String>?) {
        val conf = settings?.toMutableMap() ?: mutableMapOf()
        for (entry in PreferenceManager.getDefaultSharedPreferences(context).all.iterator()) {
            if (!conf.containsKey(entry.key)) {
                conf[entry.key] = entry.value.toString()
            }
        }

        val future = CompletableFuture<Void>()
        val baos = ByteArrayOutputStream()
        val bais = ByteArrayInputStream(byteArrayOf())

        val mode = conf.get("hardware_${useCase}printer_mode")!!
        val proto = getProtoClass(mode)
        val dpi = Integer.valueOf(conf.get("hardware_${useCase}printer_dpi")
                ?: proto.defaultDPI.toString()).toFloat()
        val rotation = Integer.valueOf(conf.get("hardware_${useCase}printer_rotation") ?: "0")

        Sentry.configureScope { scope ->
            scope.setTag("printer.mode", mode)
            scope.setTag("printer.type", useCase)
            scope.setContexts("printer.dpi", dpi)
            scope.setContexts("printer.rotation", rotation)
        }

        try {
            Log.i("PrintService", "Starting renderPages")
            val futures = renderPages(proto, tmpfile, dpi, rotation, numPages, conf, useCase)

            Log.i("PrintService", "bindService")
            InnerPrinterManager.getInstance().bindService(context, object : InnerPrinterCallback() {
                override fun onConnected(printerService: SunmiPrinterService) {
                    Log.i("PrintService", "PrinterService connected")
                    when (proto) {
                        is StreamByteProtocol<*> -> {
                            proto.send(futures, bais, baos, conf, useCase)
                            printerService.sendRAWData(baos.toByteArray(), object : InnerResultCallback() {
                                override fun onRunResult(p0: Boolean) {
                                    Log.i("PrintService", "PrinterService onRunResult: $p0")
                                    future.complete(null)
                                }

                                override fun onReturnString(p0: String?) {
                                    Log.i("PrintService", "PrinterService onReturnString: $p0")
                                }

                                override fun onRaiseException(code: Int, msg: String?) {
                                    future.completeExceptionally(Exception("[$code] $msg"))
                                }

                                override fun onPrintResult(p0: Int, p1: String?) {
                                    Log.i("PrintService", "PrinterService onPrintResult: $p0 $p1")
                                }

                            })
                        }

                        is SunmiByteProtocol<*> -> {
                            proto.sendSunmi(printerService, futures, conf, useCase)
                            future.complete(null)
                        }

                        is SunmiPrinterXByteProtocol<*> -> {
                            PrinterSdk.getInstance().getPrinter(context, object : PrinterSdk.PrinterListen {

                                override fun onDefPrinter(printer: PrinterSdk.Printer?) {
                                    if(printer != null) {
                                        proto.sendSunmi(printer, futures, conf, useCase)
                                        future.complete(null)
                                    }
                                }

                                override fun onPrinters(printers: MutableList<PrinterSdk.Printer>?) {
                                }

                            })

                        }

                        is CustomByteProtocol<*> -> {
                            throw RuntimeException("Combination not supported")
                        }
                    }
                }

                override fun onDisconnected() {
                    Log.i("PrintService", "PrinterService onDisconnected")
                }
            })
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