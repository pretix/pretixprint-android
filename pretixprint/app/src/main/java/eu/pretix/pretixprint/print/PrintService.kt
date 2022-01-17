package eu.pretix.pretixprint.print

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import com.lowagie.text.Document
import com.lowagie.text.pdf.PdfDocument
import com.lowagie.text.pdf.PdfCopy
import com.lowagie.text.pdf.PdfReader
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.BluetoothConnection
import eu.pretix.pretixprint.connections.CUPSConnection
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.connections.USBConnection
import eu.pretix.pretixprint.ui.SettingsActivity
import java8.util.concurrent.CompletableFuture
import org.jetbrains.anko.ctx
import org.jetbrains.anko.defaultSharedPreferences
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


abstract class AbstractPrintService(name: String) : IntentService(name) {

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
        val type = getType(intent.action!!)
        //val renderer = prefs.getString("hardware_${type}printer_mode", if (type == "receipt") { "ESCPOS" } else { "WYSIWYG"})
        val renderer = if (type == "receipt") {
            "ESCPOS"
        } else {
            "WYSIWYG"
        }
        val connection = prefs.getString("hardware_${type}printer_connection", "network_printer")
        val mode = prefs.getString("hardware_${type}printer_mode", "")

        val pages = emptyList<CompletableFuture<File?>>().toMutableList()
        var tmpfile: File?
        var pagenum = 0

        val dataInputStream = ctx.contentResolver.openInputStream(intent.clipData!!.getItemAt(0).uri)
        val jsonData = JSONObject(dataInputStream!!.bufferedReader().use { it.readText() })
        val positions = jsonData.getJSONArray("positions")

        when (renderer) {
            "ESCPOS" -> {
                tmpfile = File.createTempFile("print_" + jsonData.getString("receipt_id"), ".escpos", this.cacheDir)

                // prefs.getInt can't parse preference-Strings to Int - so we have to work around this
                // Unfortunately, we also cannot make the @array/receipt_cpl a integer-array, String-entries and Integer-values are not supported by the Preference-Model, either.
                tmpfile.writeBytes(ESCPOSRenderer(jsonData, prefs.getString("hardware_receiptprinter_cpl", "32")!!.toInt(), this).render())
                pagenum = 1
            }
            else -> {
                try {
                    for (i in 0 until positions.length()) {
                        val future = CompletableFuture<File?>()
                        future.completeAsync {
                            Log.i("PrintService", "Page $i: Starting render thread")
                            val position = positions.getJSONObject(i)
                            val layout = position.getJSONArray("__layout")

                            val _tmpfile = File.createTempFile("print_$i", ".pdf", ctx.cacheDir)

                            val imageMap = mutableMapOf<String, InputStream?>()
                            if (position.has("__image_map")) {
                                val im = position.getJSONObject("__image_map")
                                for (k in im.keys()) {
                                    imageMap[k] = this.contentResolver.openInputStream(intent.clipData!!.getItemAt(im.getInt(k)).uri)
                                }
                            }

                            try {
                                if (position.has("__file_index")) {
                                    val fileIndex = position.getInt("__file_index")

                                    val bgInputStream = this.contentResolver.openInputStream(intent.clipData!!.getItemAt(fileIndex).uri)
                                    bgInputStream.use {
                                        Log.i("PrintService", "Page $i: Starting WYSIWYG renderer")
                                        WYSIWYGRenderer(layout, jsonData, i, it, this, imageMap).writePDF(_tmpfile)
                                    }
                                } else {
                                    Log.i("PrintService", "Page $i: Starting WYSIWYG renderer")
                                    WYSIWYGRenderer(layout, jsonData, i, null, this, imageMap).writePDF(_tmpfile)
                                }
                            } finally {
                                try {
                                    for (stream in imageMap.values) {
                                        stream?.close()
                                    }
                                } catch (e: java.lang.Exception) {
                                    // pass
                                }
                            }
                            Log.i("PrintService", "Page $i: Completing future")
                            _tmpfile
                        }
                        pagenum += 1
                        pages.add(future)
                    }

                    tmpfile = File.createTempFile("print_", ".pdf", this.cacheDir)
                    Log.i("PrintService", "Writing to tmpfile $tmpfile")
                    var doc : Document? = null
                    var copy : PdfCopy? = null
                    for (page in pages) {
                        val pf = page.get(60, TimeUnit.SECONDS) ?: throw java.lang.Exception("Rendering failed")
                        val pagedoc = PdfReader(pf.absolutePath)
                        if (copy == null) {
                            doc = Document(pagedoc.getPageSizeWithRotation(1))
                            copy = PdfCopy(doc, FileOutputStream(tmpfile))
                            doc.open()
                        }
                        for (i in 0 until pagedoc.numberOfPages) {
                            copy.addPage(copy.getImportedPage(pagedoc, i + 1))
                        }
                        pagedoc.close()
                    }
                    doc?.close()
                } catch (e: TimeoutException) {
                    e.printStackTrace()
                    throw PrintException("Rendering timeout, thread may have crashed")
                } catch (e: IOException) {
                    e.printStackTrace()
                    throw PrintException(getString(R.string.err_files_io, e.message))
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw PrintException(getString(R.string.err_files_generic, e.message))
                }
            }
        }

        when (connection) {
            "network_printer" -> {
                if (mode == "CUPS/IPP") {
                    // Backwards compatibility
                    CUPSConnection().print(tmpfile, pagenum, this, type, null)
                }
                NetworkConnection().print(tmpfile, pagenum, this, type, null)
            }
            "cups" -> {
                CUPSConnection().print(tmpfile, pagenum, this, type, null)
            }
            "bluetooth_printer" -> {
                BluetoothConnection().print(tmpfile, pagenum, this, type, null)
            }
            "usb" -> {
                if (SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    USBConnection().print(tmpfile, pagenum, this, type, null)
                } else {
                    throw PrintException("USB not supported on this Android version.")
                }
            }
        }

        cleanupOldFiles()
    }

    fun cleanupOldFiles() {
        for (file in this.cacheDir.listFiles { file, s -> s.startsWith("print_") }) {
            if (System.currentTimeMillis() - file.lastModified() > 3600 * 1000) {
                file.delete()
            }
        }
    }

    private fun getType(intentAction: String): String {
        return when (intentAction) {
            "eu.pretix.pretixpos.print.PRINT_TICKET" -> {
                "ticket"
            }
            "eu.pretix.pretixpos.print.PRINT_BADGE" -> {
                "badge"
            }
            "eu.pretix.pretixpos.print.PRINT_RECEIPT" -> {
                "receipt"
            }
            else -> {
                "ticket"
            }
        }
    }


    override fun onHandleIntent(intent: Intent?) {
        var rr: ResultReceiver? = null
        if (intent!!.hasExtra("resultreceiver")) {
            rr = intent.getParcelableExtra<ResultReceiver>("resultreceiver")!! as ResultReceiver
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

// These services all do the same, but separating has the advantage of being able to run a
// ticket print and a receipt print at the same time
class TicketPrintService : AbstractPrintService("TicketPrintService")
class ReceiptPrintService : AbstractPrintService("ReceiptPrintService")
class BadgePrintService : AbstractPrintService("BadgePrintService")

// Kept for legacy compatibility
class PrintService : AbstractPrintService("PrintService")
