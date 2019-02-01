package eu.pretix.pretixprint.print

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.ResultReceiver
import com.itextpdf.text.Document
import com.itextpdf.text.pdf.PdfCopy
import com.itextpdf.text.pdf.PdfReader
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.fgl.FGLNetworkPrinter
import eu.pretix.pretixprint.ui.SettingsActivity
import org.cups4j.CupsClient
import org.cups4j.CupsPrinter
import org.cups4j.PrintJob
import org.jetbrains.anko.ctx
import org.jetbrains.anko.defaultSharedPreferences
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL


class PrintService : IntentService("PrintService") {
    companion object {
        val CHANNEL_ID = "pretixprint_print_channel"
        val ONGOING_NOTIFICATION_ID = 42
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_print)
            val description = getString(R.string.notification_channel_print_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            val notificationManager = getSystemService<NotificationManager>(NotificationManager::class.java)
            notificationManager!!.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        createNotificationChannel()
        val notificationIntent = Intent(this, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        val notification = if (SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle(getText(R.string.print_notification))
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.drawable.ic_stat_print)
                    .build()
        } else {
            Notification.Builder(this)
                    .setContentTitle(getText(R.string.print_notification))
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.drawable.ic_stat_print)
                    .build()
        }
        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }

    private fun print(intent: Intent, rr: ResultReceiver?) {
        val prefs = ctx.defaultSharedPreferences

        val pages = emptyList<File>().toMutableList()
        var tmpfile: File?
        try {
            val dataInputStream = ctx.contentResolver.openInputStream(intent.clipData.getItemAt(0).uri)
            val jsonData = JSONObject(dataInputStream.bufferedReader().use { it.readText() })

            val positions = jsonData.getJSONArray("positions")
            for (i in 0..(positions.length() - 1)) {
                val position = positions.getJSONObject(i)
                val layout = position.getJSONArray("__layout");

                val _tmpfile = File.createTempFile("print_$i", "pdf", ctx.cacheDir)
                if (position.has("__file_index")) {
                    val fileIndex = position.getInt("__file_index");

                    val bgInputStream = ctx.contentResolver.openInputStream(intent.clipData.getItemAt(fileIndex).uri)
                    bgInputStream.use {
                        Renderer(layout, jsonData, i, it, ctx).writePDF(_tmpfile)
                    }
                } else {
                    Renderer(layout, jsonData, i, null, ctx).writePDF(_tmpfile)
                }
                pages.add(_tmpfile)
            }

            tmpfile = File.createTempFile("print", "pdf", ctx.cacheDir)
            val document = Document()
            val copy = PdfCopy(document, FileOutputStream(tmpfile))
            document.open()
            for (page in pages) {
                val pagedoc = PdfReader(page.absolutePath)
                copy.addDocument(pagedoc)
                pagedoc.close()
            }
            document.close()
        } catch (e: IOException) {
            e.printStackTrace()
            throw PrintException(getString(R.string.err_files_io, e.message));
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintException(getString(R.string.err_files_generic, e.message));
        }


        val mode = prefs.getString("hardware_ticketprinter_mode", "CUPS/IPP")
        if (mode == "FGL") {
            try {
                FGLNetworkPrinter(
                        prefs.getString("hardware_ticketprinter_ip", "127.0.0.1"),
                        Integer.valueOf(prefs.getString("hardware_ticketprinter_port", "9100"))
                ).printPDF(tmpfile)
            } catch (e: IOException) {
                e.printStackTrace()
                throw PrintException(getString(R.string.err_job_io, e.message));
            }
        } else if (mode == "CUPS/IPS") {
            var cp: CupsPrinter?

            try {
                cp = getPrinter(
                        prefs.getString("hardware_ticketprinter_ip", "127.0.0.1"),
                        prefs.getString("hardware_ticketprinter_port", "631"),
                        prefs.getString("hardware_ticketprinter_printername", "PATicket")
                )
            } catch (e: IOException) {
                e.printStackTrace()
                throw PrintException(getString(R.string.err_cups_io, e.message));
            }
            if (cp == null) {
                throw PrintException(getString(R.string.err_no_printer_found))
            }
            try {
                val pj = PrintJob.Builder(tmpfile.inputStream()).build()
                cp.print(pj)
            } catch (e: IOException) {
                e.printStackTrace()
                throw PrintException(getString(R.string.err_job_io, e.message));
            }
        }
    }

    override fun onHandleIntent(intent: Intent) {
        var rr: ResultReceiver? = null
        if (intent.hasExtra("resultreceiver")) {
            rr = intent.getParcelableExtra("resultreceiver") as ResultReceiver
        }

        startForegroundNotification()
        try {
            print(intent, rr)
            if (rr != null) {
                val b = Bundle()
                rr.send(0, b)
            }
        } catch (e: PrintException) {
            if (rr != null) {
                val b = Bundle()
                b.putString("message", e.message)
                rr.send(1, b)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (rr != null) {
                val b = Bundle()
                b.putString("message", getString(R.string.err_generic, e.message))
                rr.send(1, b)
            }
        }
        stopForeground(true)
    }
}