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
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.ui.SettingsActivity
import org.jetbrains.anko.ctx
import org.jetbrains.anko.defaultSharedPreferences
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java8.util.concurrent.CompletableFuture
import java.util.concurrent.Executors


class PrintService : IntentService("PrintService") {
    protected var threadPool = Executors.newCachedThreadPool()

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
        val type = getType(intent.action)
        //val renderer = prefs.getString("hardware_${type}printer_mode", if (type == "receipt") { "ESCPOS" } else { "WYSIWYG"})
        val renderer = if (type == "receipt") {
            "ESCPOS"
        } else {
            "WYSIWYG"
        }
        val connection = prefs.getString("hardware_${type}printer_connection", "network_printer")
        val mode = prefs.getString("hardware_${type}printer_mode", "")

        val pages = emptyList<CompletableFuture<File>>().toMutableList()
        var tmpfile: File?

        val dataInputStream = ctx.contentResolver.openInputStream(intent.clipData.getItemAt(0).uri)
        val jsonData = JSONObject(dataInputStream.bufferedReader().use { it.readText() })
        val positions = jsonData.getJSONArray("positions")

        when (renderer) {
            "ESCPOS" -> {
                tmpfile = File.createTempFile("print_" + jsonData.getString("receipt_id"), "escpos", this.cacheDir)

                // prefs.getInt can't parse preference-Strings to Int - so we have to work around this
                // Unfortunately, we also cannot make the @array/receipt_cpl a integer-array, String-entries and Integer-values are not supported by the Preference-Model, either.
                tmpfile.writeBytes(ESCPOSRenderer(jsonData, prefs.getString("hardware_receiptprinter_cpl", "32").toInt(), this).render())
            }
            else -> {
                try {
                    for (i in 0..(positions.length() - 1)) {
                        val future = CompletableFuture<File>()
                        threadPool.submit {
                            val position = positions.getJSONObject(i)
                            val layout = position.getJSONArray("__layout");

                            val _tmpfile = File.createTempFile("print_$i", "pdf", ctx.cacheDir)
                            if (position.has("__file_index")) {
                                val fileIndex = position.getInt("__file_index")

                                val bgInputStream = this.contentResolver.openInputStream(intent.clipData.getItemAt(fileIndex).uri)
                                bgInputStream.use {
                                    WYSIWYGRenderer(layout, jsonData, i, it, this).writePDF(_tmpfile)
                                }
                            } else {
                                WYSIWYGRenderer(layout, jsonData, i, null, this).writePDF(_tmpfile)
                            }
                            future.complete(_tmpfile)
                        }
                        pages.add(future)
                    }

                    tmpfile = File.createTempFile("print", "pdf", this.cacheDir)
                    val document = Document()
                    val copy = PdfCopy(document, FileOutputStream(tmpfile))
                    document.open()
                    for (page in pages) {
                        val pagedoc = PdfReader(page.get().absolutePath)
                        copy.addDocument(pagedoc)
                        pagedoc.close()
                    }
                    document.close()
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
                NetworkConnection().print(tmpfile, pages.size, this, type, null)
            }
            "bluetooth_printer" -> {
                BluetoothPrintService(this, type).print(tmpfile, pages.size)
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
