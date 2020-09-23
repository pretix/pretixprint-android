package eu.pretix.pretixprint.connections

import android.content.Context
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.*
import eu.pretix.pretixprint.renderers.renderPages
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Socket


class NetworkConnection : ConnectionType {
    override val identifier = "network_printer"
    override val nameResource = R.string.connection_type_network
    override val inputType = ConnectionType.Input.PLAIN_BYTES

    override fun allowedForUsecase(type: String): Boolean {
        return true
    }

    override fun print(tmpfile: File, numPages: Int, context: Context, type: String, settings: Map<String, String>?) {
        val conf = settings ?: emptyMap()
        fun getSetting(key: String, def: String): String {
            return conf!![key] ?: context.defaultSharedPreferences.getString(key, def)!!
        }
        val mode = getSetting("hardware_${type}printer_mode", "FGL")
        val serverAddr = InetAddress.getByName(getSetting("hardware_${type}printer_ip", "127.0.0.1"))
        val port = Integer.valueOf(getSetting("hardware_${type}printer_port", "9100"))
        val socket = Socket(serverAddr, port)
        val ostream = socket.getOutputStream()
        val istream = socket.getInputStream()
        try {
            if (mode == "FGL") {
                val proto = FGL()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(getSetting("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                proto.send(futures, istream, ostream)
            } else if (mode == "SLCS") {
                val proto = SLCS()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(getSetting("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                proto.send(futures, istream, ostream)
            } else if (mode == "ESC/POS") {
                val proto = ESCPOS()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(getSetting("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                proto.send(futures, istream, ostream)
            }
        } catch (e: PrintError) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
        } catch (e: IOException) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
        } finally {
            istream.close()
            ostream.close()
            socket.close()
        }
    }
}