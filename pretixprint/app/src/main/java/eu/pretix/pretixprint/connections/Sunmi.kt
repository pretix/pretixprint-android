package eu.pretix.pretixprint.connections

import android.content.Context
import android.os.Build
import android.util.Log
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
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

    override fun print(tmpfile: File, numPages: Int, context: Context, type: String, settings: Map<String, String>?) {
        val conf = settings?.toMutableMap() ?: mutableMapOf()

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
            Log.i("PrintService", "Starting renderPages")
            val futures = renderPages(proto, tmpfile, dpi, rotation, numPages, conf, type, context)

            Log.i("PrintService", "bindService")
            InnerPrinterManager.getInstance().bindService(context, object : InnerPrinterCallback() {
                override fun onConnected(printerService: SunmiPrinterService) {
                    Log.i("PrintService", "PrinterService connected")
                    when (proto) {
                        is StreamByteProtocol<*> -> {
                            proto.send(futures, bais, baos, conf, type)
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
                            proto.sendSunmi(printerService, futures, conf, type)
                            future.complete(null)
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