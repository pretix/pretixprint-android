package eu.pretix.pretixprint.print

import android.content.Context
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.socket.FGLNetworkPrinter
import eu.pretix.pretixprint.socket.PlaintextNetworkPrinter
import eu.pretix.pretixprint.socket.SLCSNetworkPrinter
import org.cups4j.CupsPrinter
import org.cups4j.PrintJob
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File
import java.io.IOException

class NetworkPrintService(context: Context, type: String = "ticket", mode: String = "CUPS/IPP") : PrintServiceTransport(context, type) {
    val mode = mode

    override fun print(tmpfile: File, numPages: Int) {
        val prefs = context.defaultSharedPreferences

        if (mode == "FGL") {
            try {
                FGLNetworkPrinter(
                        prefs.getString("hardware_${type}printer_ip", "127.0.0.1"),
                        Integer.valueOf(prefs.getString("hardware_${type}printer_port", "9100")),
                        Integer.valueOf(prefs.getString("hardware_${type}printer_dpi", "200"))
                ).printPDF(tmpfile, numPages)
            } catch (e: IOException) {
                e.printStackTrace()
                throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message));
            }
        } else if (mode == "SLCS") {
            try {
                SLCSNetworkPrinter(
                        prefs.getString("hardware_${type}printer_ip", "127.0.0.1"),
                        Integer.valueOf(prefs.getString("hardware_${type}printer_port", "9100")),
                        Integer.valueOf(prefs.getString("hardware_${type}printer_dpi", "200"))
                ).printPDF(tmpfile, numPages)
            } catch (e: IOException) {
                e.printStackTrace()
                throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message));
            }
        } else if (mode == "CUPS/IPP") {
            var cp: CupsPrinter?

            try {
                cp = getPrinter(
                        prefs.getString("hardware_${type}printer_ip", "127.0.0.1"),
                        prefs.getString("hardware_${type}printer_port", "631"),
                        prefs.getString("hardware_${type}printer_printername", "PATicket")
                )
            } catch (e: IOException) {
                e.printStackTrace()
                throw PrintException(context.applicationContext.getString(R.string.err_cups_io, e.message));
            }
            if (cp == null) {
                throw PrintException(context.applicationContext.getString(R.string.err_no_printer_found))
            }
            try {
                val pj = PrintJob.Builder(tmpfile.inputStream()).build()
                cp.print(pj)
            } catch (e: IOException) {
                e.printStackTrace()
                throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message));
            }
        } else if (mode == "ESC/POS") {
            try {
                PlaintextNetworkPrinter(
                        prefs.getString("hardware_${type}printer_ip", "127.0.0.1"),
                        Integer.valueOf(prefs.getString("hardware_${type}printer_port", "9100")),
                        Integer.valueOf(prefs.getString("hardware_${type}printer_dpi", "200"))
                ).send(tmpfile)
            } catch (e: IOException) {
                e.printStackTrace()
                throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message));
            }
        }
    }
}