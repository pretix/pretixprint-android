package eu.pretix.pretixprint.connections

import android.content.Context
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.*
import eu.pretix.pretixprint.renderers.renderPages
import io.sentry.Sentry
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeoutException


class NetworkConnection : ConnectionType {
    override val identifier = "network_printer"
    override val nameResource = R.string.connection_type_network
    override val inputType = ConnectionType.Input.PLAIN_BYTES

    override fun allowedForUsecase(type: String): Boolean {
        return true
    }

    override fun print(tmpfile: File, numPages: Int, context: Context, type: String, settings: Map<String, String>?) {
        val conf = settings?.toMutableMap() ?: mutableMapOf()
        for (entry in context.defaultSharedPreferences.all.iterator()) {
            if (!conf.containsKey(entry.key)) {
                conf[entry.key] = entry.value.toString()
            }
        }

        val mode = conf.get("hardware_${type}printer_mode") ?: "FGL"

        val proto = getProtoClass(mode)

        val ip = conf.get("hardware_${type}printer_ip") ?: "127.0.0.1"
        val port = Integer.valueOf(conf.get("hardware_${type}printer_port") ?: "9100")
        val dpi = Integer.valueOf(conf.get("hardware_${type}printer_dpi") ?: proto.defaultDPI.toString()).toFloat()

        Sentry.configureScope { scope ->
            scope.setTag("printer.mode", mode)
            scope.setTag("printer.type", type)
            scope.setContexts("printer.ip", ip)
            scope.setContexts("printer.port", port)
            scope.setContexts("printer.dpi", dpi)
        }

        val serverAddr = InetAddress.getByName(ip)

        try {
            val futures = renderPages(proto, tmpfile, dpi, numPages, conf, type)
            when (proto) {
                is StreamByteProtocol<*> -> {
                    val socket = Socket(serverAddr, port)
                    val ostream = socket.getOutputStream()
                    val istream = socket.getInputStream()

                    try {
                        proto.send(futures, istream, ostream, conf, type)
                    } finally {
                        istream.close()
                        ostream.close()
                        socket.close()
                    }
                }

                is CustomByteProtocol<*> -> {
                    proto.sendNetwork(serverAddr.hostAddress, port, futures, conf, type, context)
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
}