package eu.pretix.pretixprint.print

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.cups4j.CupsClient
import org.cups4j.CupsPrinter
import org.cups4j.PrintJob
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import java.net.URL
import org.json.JSONObject
import java.io.*


class PrintBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {

        val pendingResult = goAsync()
        doAsync {
            val prefs = ctx.defaultSharedPreferences

            val cc = CupsClient(
                    URL(
                            "http://" +
                                    prefs.getString("hardware_ticketprinter_ip", "127.0.0.1") +
                                    ":" +
                                    prefs.getString("hardware_ticketprinter_port", "631")
                    )
            )
            var cp: CupsPrinter? = null
            for (printer in cc.printers) {
                if (printer.name == prefs.getString("hardware_ticketprinter_printername", "PATicket")) {
                    cp = printer
                }
            }
            if (cp == null) {
                cp = cc.defaultPrinter
            }

            if (cp != null && intent != null) {
                val dataInputStream = ctx.contentResolver.openInputStream(intent.clipData.getItemAt(0).uri)
                val bgInputStream = ctx.contentResolver.openInputStream(intent.clipData.getItemAt(1).uri)

                val jsonData = JSONObject(dataInputStream.bufferedReader().use { it.readText() })

                val tmpfile = File.createTempFile("print", "pdf", ctx.cacheDir)
                bgInputStream.use {
                    Renderer(jsonData.getJSONArray("__layout"), jsonData, it, ctx).writePDF(tmpfile)
                }
                val pj = PrintJob.Builder(tmpfile.inputStream()).build()
                cp.print(pj)
            }
            pendingResult.finish()
        }

    }

}