package eu.pretix.pretixprint.system

import android.os.Build
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.text.TextUtils
import android.util.Log
import androidx.preference.PreferenceManager
import com.lowagie.text.pdf.PdfReader
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.BluetoothConnection
import eu.pretix.pretixprint.connections.CUPSConnection
import eu.pretix.pretixprint.connections.IMinInternalConnection
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.connections.SunmiInternalConnection
import eu.pretix.pretixprint.connections.SystemConnection
import eu.pretix.pretixprint.connections.USBConnection
import java8.util.concurrent.CompletableFuture
import java.io.File
import java.io.FileInputStream

class SystemPrintService : PrintService() {
    val types = listOf("ticket", "badge")

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return object : PrinterDiscoverySession() {
            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                val defaultSharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(this@SystemPrintService)

                for (type in types) {
                    val connection = defaultSharedPreferences.getString(
                        "hardware_${type}printer_connection",
                        ""
                    )
                    val printerSetUp = !TextUtils.isEmpty(connection) && connection != "system"
                    if (printerSetUp) {
                        val printerId = generatePrinterId(type)
                        val name = when (type) {
                            "ticket" -> getString(R.string.settings_label_ticketprinter)
                            "badge" -> getString(R.string.settings_label_badgeprinter)
                            else -> TODO("printer type not implemented")
                        }
                        val builder = PrinterInfo.Builder(printerId, name, PrinterInfo.STATUS_IDLE)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            builder.setHasCustomPrinterIcon(true)
                            builder.setIconResourceId(R.drawable.ic_print_logo)
                        }
                        val capBuilder = PrinterCapabilitiesInfo.Builder(printerId)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .addMediaSize(PrintAttributes.MediaSize.ISO_A6, true)
                            .addMediaSize(PrintAttributes.MediaSize.ISO_A5, false)
                            .addMediaSize(PrintAttributes.MediaSize.ISO_A4, false)
                            .addResolution(
                                PrintAttributes.Resolution("200dpi", "200dpi", 200, 200),
                                true
                            )
                            .addResolution(
                                PrintAttributes.Resolution("300dpi", "300dpi", 300, 300),
                                false
                            )
                            .setColorModes(
                                PrintAttributes.COLOR_MODE_MONOCHROME or PrintAttributes.COLOR_MODE_COLOR,
                                PrintAttributes.COLOR_MODE_COLOR
                            )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            capBuilder.setDuplexModes(
                                PrintAttributes.DUPLEX_MODE_NONE,
                                PrintAttributes.DUPLEX_MODE_NONE
                            )
                        }
                        builder.setCapabilities(capBuilder.build())
                        addPrinters(listOf(builder.build()))
                    }
                }
            }

            override fun onStopPrinterDiscovery() {
            }

            override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
            }

            override fun onStartPrinterStateTracking(printerId: PrinterId) {
            }

            override fun onStopPrinterStateTracking(printerId: PrinterId) {
            }

            override fun onDestroy() {
            }
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        // Not implemented
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        val defaultSharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this@SystemPrintService)
        val type = printJob.info.printerId!!.localId
        val connection = defaultSharedPreferences.getString(
            "hardware_${type}printer_connection",
            ""
        )
        val mode = defaultSharedPreferences.getString("hardware_${type}printer_mode", "")

        val conn = when (connection) {
            "network_printer" -> if (mode == "CUPS/IPP") {
                // Backwards compatibility
                CUPSConnection()
            } else {
                NetworkConnection()
            }

            "cups" -> CUPSConnection()
            "bluetooth_printer" -> BluetoothConnection()
            "usb" -> USBConnection()
            "sunmi" -> SunmiInternalConnection()
            "imin" -> IMinInternalConnection()
            "system" -> SystemConnection()
            else -> null
        }

        val tmpfile = File.createTempFile("print_", ".pdf", this.cacheDir)
        Log.i("PrintService", "[$type] Writing to tmpfile $tmpfile")

        assert(printJob.document.info.contentType == CONTENT_TYPE_DOCUMENT)
        // todo: convert photos to pdf

        val doc = ParcelFileDescriptor.AutoCloseInputStream(printJob.document.data)
        tmpfile.outputStream().use { outStream ->
            outStream.write(doc.readBytes())
        }
        val pagedoc = PdfReader(tmpfile.absolutePath)

        val future = CompletableFuture<Unit>()
        future.completeAsync {
            conn?.print(
                tmpfile,
                pagedoc.numberOfPages,
                listOf(pagedoc.numberOfPages),
                this,
                type,
                null
            )
        }
        future.get()
    }
}