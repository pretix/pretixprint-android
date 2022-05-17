package eu.pretix.pretixprint.connections

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.*
import eu.pretix.pretixprint.print.lockManager
import eu.pretix.pretixprint.renderers.renderPages
import io.sentry.Sentry
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class NetworkConnection : ConnectionType {
    override val identifier = "network_printer"
    override val nameResource = R.string.connection_type_network
    override val inputType = ConnectionType.Input.PLAIN_BYTES

    override fun allowedForUsecase(type: String): Boolean {
        return true
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

        val mode = conf.get("hardware_${type}printer_mode") ?: "FGL"
        val proto = getProtoClass(mode)

        val ip = conf.get("hardware_${type}printer_ip") ?: "127.0.0.1"
        val port = Integer.valueOf(conf.get("hardware_${type}printer_port") ?: "9100")
        val dpi = Integer.valueOf(conf.get("hardware_${type}printer_dpi") ?: proto.defaultDPI.toString()).toFloat()
        val rotation = Integer.valueOf(conf.get("hardware_${type}printer_rotation") ?: "0")

        Sentry.configureScope { scope ->
            scope.setTag("printer.mode", mode)
            scope.setTag("printer.type", type)
            scope.setContexts("printer.ip", ip)
            scope.setContexts("printer.port", port)
            scope.setContexts("printer.dpi", dpi)
            scope.setContexts("printer.rotation", rotation)
        }

        val serverAddr = InetAddress.getByName(ip)

        try {
            Log.i("PrintService", "[$type] Starting renderPages")
            val futures = renderPages(proto, tmpfile, dpi, rotation, numPages, conf, type)
            lockManager.withLock("$identifier:${serverAddr.hostAddress}:$port") {
                when (proto) {
                    is StreamByteProtocol<*> -> {
                        Log.i("PrintService", "[$type] Start connection to ${serverAddr.hostAddress}:$port")
                        val socket = Socket(serverAddr, port)
                        val ostream = socket.getOutputStream()
                        val istream = socket.getInputStream()

                        try {
                            Log.i("PrintService", "[$type] Start proto.send()")
                            val wap = Integer.valueOf(conf.get("hardware_${type}printer_waitafterpage") ?: "2000").toLong()
                            proto.send(futures, pagegroups, istream, ostream, conf, type, wap)
                            Log.i("PrintService", "[$type] Finished proto.send()")
                            done()
                        } finally {
                            istream.close()
                            ostream.close()
                            socket.close()
                        }
                    }

                    is CustomByteProtocol<*> -> {
                        Log.i("PrintService", "[$type] Start proto.sendNetwork()")
                        proto.sendNetwork(serverAddr.hostAddress, port, futures, pagegroups, conf, type, context)
                        Log.i("PrintService", "[$type] Finished proto.sendNetwork()")
                        done()
                    }
                    is SunmiByteProtocol -> {
                        throw PrintException("Unsupported combination")
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
    }

    override suspend fun connectAsync(context: Context, type: String): StreamHolder = suspendCancellableCoroutine { cont ->
        val conf = PreferenceManager.getDefaultSharedPreferences(context)
        val serverAddr = InetAddress.getByName(conf.getString("hardware_${type}printer_ip", "127.0.0.1"))
        val port = Integer.valueOf(conf.getString("hardware_${type}printer_port", "9100")!!)

        try {
            val socket = Socket(serverAddr, port)
            cont.invokeOnCancellation { socket.close() }

            val istream = socket.getInputStream()
            val ostream = socket.getOutputStream()

            cont.resume(CloseableStreamHolder(istream, ostream, socket))
        } catch (e: IOException) {
            cont.resumeWithException(e)
        }
    }
}