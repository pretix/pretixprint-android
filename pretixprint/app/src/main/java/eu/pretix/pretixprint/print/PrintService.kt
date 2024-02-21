package eu.pretix.pretixprint.print

import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.lowagie.text.Document
import com.lowagie.text.pdf.PdfCopy
import com.lowagie.text.pdf.PdfReader
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.*
import eu.pretix.pretixprint.ui.SettingsActivity
import eu.pretix.pretixprint.ui.SystemPrintActivity
import io.sentry.Sentry
import java8.util.concurrent.CompletableFuture
import org.json.JSONObject
import java.io.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


abstract class AbstractPrintService(name: String) : IntentService(name) {

    companion object {
        val CHANNEL_ID = "pretixprint_print_channel"
        val ONGOING_NOTIFICATION_ID = 42
        val ACTION_STOP_SERVICE = "action_stop_service"
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
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                if (Build.VERSION.SDK_INT >= 23) { PendingIntent.FLAG_IMMUTABLE } else { 0 })
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getText(R.string.print_notification))
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.drawable.ic_stat_print)
                    .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)
    }

    private fun logException(e: Throwable) {
        try {
            val log = File.createTempFile("error_", ".log", this.cacheDir)
            val fw = FileWriter(log)
            val pw = PrintWriter(fw)
            e.printStackTrace(pw)
            pw.flush()
            fw.close()
        } catch (ee: Throwable) {
            Sentry.captureException(ee)
            ee.printStackTrace()
        }
    }

    private fun print(intent: Intent, rr: ResultReceiver?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val type = getType(intent.action!!)

        val connection = prefs.getString("hardware_${type}printer_connection", "network_printer")
        val mode = prefs.getString("hardware_${type}printer_mode", "")
        val renderer = if (type == "receipt") {
            mode
        } else {
            "WYSIWYG"
        }
        Log.i("PrintService", "[$type] Starting print job mode=$mode connection=$connection renderer=$renderer")

        Sentry.configureScope { scope ->
            scope.setTag("type", type)
            scope.setTag("renderer", renderer!!)
            scope.setTag("connection", connection!!)
            scope.setTag("printer.mode", mode!!)
        }

        val conn = when (connection) {
            "network_printer" -> if (mode == "CUPS/IPP") {
                    // Backwards compatibility
                    CUPSConnection()
                } else {
                    NetworkConnection()
                }
            "cups" -> CUPSConnection()
            "bluetooth_printer" -> BluetoothConnection()
            "usb" -> if (SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    USBConnection()
                } else {
                    throw PrintException("USB print is not supported on this Android version.")
                }
            "sunmi" -> SunmiInternalConnection()
            "imin" -> IMinInternalConnection()
            "system" -> SystemConnection()
            else -> null
        }
        if (conn == null || !conn.isConfiguredFor(this, type)) {
            throw PrintException("Printer is not configured for type $type")
        }

        val pages = emptyList<CompletableFuture<File?>>().toMutableList()
        var tmpfile: File?
        var pagenum = 0
        var pagegroups = emptyList<Int>().toMutableList()

        val dataInputStream = applicationContext.contentResolver.openInputStream(intent.clipData!!.getItemAt(0).uri)
        val jsonData = JSONObject(dataInputStream!!.bufferedReader().use { it.readText() })
        val positions = jsonData.getJSONArray("positions")

        when (renderer) {
            "ESC/POS",
            "ePOSPrintXML",
            "StarPRNT" -> {
                tmpfile = File.createTempFile("print_" + jsonData.getString("receipt_id") + "_", ".escpos", this.cacheDir)

                var dialect = ESCPOSRenderer.Companion.Dialect.values().find {
                    it.name == prefs.getString("hardware_${type}printer_dialect", "")
                } ?: ESCPOSRenderer.Companion.Dialect.EpsonDefault

                if (renderer == "StarPRNT") {
                    dialect = ESCPOSRenderer.Companion.Dialect.StarPRNT
                }

                // prefs.getInt can't parse preference-Strings to Int - so we have to work around this
                // Unfortunately, we also cannot make the @array/receipt_cpl a integer-array, String-entries and Integer-values are not supported by the Preference-Model, either.
                tmpfile.writeBytes(ESCPOSRenderer(dialect, jsonData, prefs.getString("hardware_receiptprinter_cpl", "32")!!.toInt(), this).render())
                pagenum = 1
                pagegroups.add(1)
            }
            else -> {
                try {
                    for (i in 0 until positions.length()) {
                        val future = CompletableFuture<File?>()
                        future.completeAsync {
                            Log.i("PrintService", "[$type] Page $i: Starting render thread")
                            val position = positions.getJSONObject(i)
                            val layout = position.getJSONArray("__layout")

                            val _tmpfile = File.createTempFile("page_$i", ".pdf", applicationContext.cacheDir)

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
                                        Log.i("PrintService", "[$type] Page $i: Starting WYSIWYG renderer")
                                        WYSIWYGRenderer(layout, jsonData, i, it, this, imageMap).writePDF(_tmpfile)
                                    }
                                } else {
                                    Log.i("PrintService", "[$type] Page $i: Starting WYSIWYG renderer")
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
                            Log.i("PrintService", "[$type] Page $i: Completing rendering future")
                            _tmpfile
                        }
                        pages.add(future)
                    }

                    tmpfile = File.createTempFile("print_", ".pdf", this.cacheDir)
                    Log.i("PrintService", "[$type] Writing to tmpfile $tmpfile")
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
                        pagenum += pagedoc.numberOfPages
                        pagegroups.add(pagedoc.numberOfPages)
                        pf.deleteOnExit()
                        pagedoc.close()
                    }
                    Log.i("PrintService", "[$type] Built combined PDF file")
                    doc?.close()
                } catch (e: TimeoutException) {
                    e.printStackTrace()
                    logException(e)
                    throw PrintException("Rendering timeout, thread may have crashed")
                } catch (e: IOException) {
                    e.printStackTrace()
                    logException(e)
                    throw PrintException(getString(R.string.err_files_io, e.message))
                } catch (e: Exception) {
                    e.printStackTrace()
                    logException(e)
                    throw PrintException(getString(R.string.err_files_generic, e.message))
                }
            }
        }

        Log.i("PrintService", "[$type] Starting connection adapter")
        if (connection == "system") {
            // printManager.print is only allowed to be called by activities
            // so lets move the call into it's own activity and try to get the user
            // to open a notification to launch it

            val notificationManagerCompat = NotificationManagerCompat.from(this)
            notificationManagerCompat.cancel(ONGOING_NOTIFICATION_ID)

            val dialogIntent = Intent(this, SystemPrintActivity::class.java)
            dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            dialogIntent.putExtra(SystemPrintActivity.INTENT_EXTRA_CALLER, this::class.java)
            dialogIntent.putExtra(SystemPrintActivity.INTENT_EXTRA_FILE, tmpfile)
            dialogIntent.putExtra(SystemPrintActivity.INTENT_EXTRA_PAGENUM, pagenum)
            //dialogIntent.putExtra(SystemPrintActivity.INTENT_EXTRA_PAGEGROUPS, pagegroups)
            dialogIntent.putExtra(SystemPrintActivity.INTENT_EXTRA_TYPE, type)
            val pendingIntent = PendingIntent.getActivity(this, 0, dialogIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or if (Build.VERSION.SDK_INT >= 23) { PendingIntent.FLAG_IMMUTABLE } else { 0 })

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(getText(R.string.print_now_notification))
                    .setSmallIcon(R.drawable.ic_stat_print)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setFullScreenIntent(pendingIntent, true)
                    .build()

            startForeground(ONGOING_NOTIFICATION_ID, notification)
        } else {
            conn.print(tmpfile, pagenum, pagegroups, this, type, null)
        }

        Log.i("PrintService", "[$type] Cleaning up old files")
        cleanupOldFiles()
        Log.i("PrintService", "[$type] Job done")
    }

    fun cleanupOldFiles() {
        for (file in this.cacheDir.listFiles { file, s -> s.startsWith("print_")  || s.startsWith("page_") || s.startsWith("error_")}!!) {
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

        if (intent.action == ACTION_STOP_SERVICE) {
            stopForeground(true)
            stopSelf()
            return
        }

        try {
            print(intent, rr)
            if (rr != null) {
                val b = Bundle()
                rr.send(0, b)
            }
        } catch (e: PrintException) {
            logException(e)
            if (rr != null) {
                val b = Bundle()
                b.putString("message", e.message)
                rr.send(1, b)
            }
        } catch (e: Exception) {
            logException(e)
            e.printStackTrace()
            if (rr != null) {
                val b = Bundle()
                b.putString("message", getString(R.string.err_generic, e.message))
                rr.send(1, b)
            }
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val type = getType(intent.action!!)
        val connection = prefs.getString("hardware_${type}printer_connection", "network_printer")
        if (connection == "system") {
            // stop the foreground service, but keep the notification
            if (SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                stopForeground(false)
            }
        } else {
            stopForeground(true)
        }
    }
}

// These services all do the same, but separating has the advantage of being able to run a
// ticket print and a receipt print at the same time
class TicketPrintService : AbstractPrintService("TicketPrintService")
class ReceiptPrintService : AbstractPrintService("ReceiptPrintService")
class BadgePrintService : AbstractPrintService("BadgePrintService")

// Kept for legacy compatibility
class PrintService : AbstractPrintService("PrintService")
