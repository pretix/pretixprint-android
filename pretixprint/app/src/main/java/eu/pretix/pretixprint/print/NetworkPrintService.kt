package eu.pretix.pretixprint.print

import android.content.Context
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.ESCPOS
import eu.pretix.pretixprint.byteprotocols.FGL
import eu.pretix.pretixprint.byteprotocols.PrintError
import eu.pretix.pretixprint.byteprotocols.SLCS
import eu.pretix.pretixprint.renderers.renderPages
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

class NetworkPrintService(context: Context, type: String = "ticket", mode: String = "CUPS/IPP", settings: Map<String, String>?=null) : PrintServiceTransport(context, type, settings) {
    val mode = mode

    override fun print(tmpfile: File, numPages: Int) {
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
            } else if (mode == "CUPS/IPP") {
                // TODO: needs to move elsewhere
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