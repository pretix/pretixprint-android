package eu.pretix.pretixprint.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.text.startsWith
import kotlin.text.trim


interface ScanReceiver {
    fun scanResult(result: String)
}

class HardwareScanner(val receiver: ScanReceiver) {

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.hasExtra("com.symbol.datawedge.data_string")) {
                // Zebra DataWedge
                receiver.scanResult(intent.getStringExtra("com.symbol.datawedge.data_string")!!)
            } else if (intent.hasExtra("SCAN_BARCODE1")) {
                // NewLand
                val barcode = intent.getStringExtra("SCAN_BARCODE1")!!.trim()
                receiver.scanResult(barcode)
            } else if (intent.hasExtra("EXTRA_BARCODE_DECODING_DATA")) {
                // Bluebird
                val barcode = String(intent.getByteArrayExtra("EXTRA_BARCODE_DECODING_DATA")!!).trim()
                receiver.scanResult(barcode)
            } else if (intent.hasExtra("barocode")) {
                // Intent receiver for LECOM-manufactured hardware scanners
                val barcode = intent?.getByteArrayExtra("barocode")!! // sic!
                val barocodelen = intent?.getIntExtra("length", 0) ?: 0
                val barcodeStr = String(barcode, 0, barocodelen)
                receiver.scanResult(barcodeStr)
            } else if (intent.hasExtra("decode_rslt")) {
                // Honeywell
                val barcode = intent.getStringExtra("decode_rslt")!!.trim()
                receiver.scanResult(barcode)
            } else if (intent.hasExtra("data")) {
                // Sunmi
                val barcode = intent.getStringExtra("data")!!.trim()
                receiver.scanResult(barcode)
            } else if (intent.hasExtra("scannerdata")) {
                // SEUIC AUTOID
                val barcode = intent.getStringExtra("scannerdata")!!.trim()
                receiver.scanResult(barcode)
            }
        }
    }

    fun start(ctx: Context) {
        val filter = IntentFilter()

        // LECOM
        // Active by default
        filter.addAction("scan.rcv.message")

        // Zebra DataWedge
        // Needs manual configuration in DataWedge
        filter.addAction("eu.pretix.SCAN")

        // Bluebird
        // Active by default
        filter.addAction("kr.co.bluebird.android.bbapi.action.BARCODE_CALLBACK_DECODING_DATA")

        // NewLand
        // Configure broadcast in Quick Setting > Scan Setting > Output Mode > Output via API
        filter.addAction("nlscan.action.SCANNER_RESULT")

        // Honeywell
        // Configure via Settings > Scan Settings > Internal Scanner > Default Profile > Data
        // Processing Settings > Scan to Intent
        filter.addAction("com.honeywell.intent.action.SCAN_RESULT")

        // SEUIC AUTOID, also known as Concept FuturePAD
        // Configure via Scan Tool > Settings > Barcode Send Model > Broadcast
        filter.addAction("com.android.server.scannerservice.broadcast")

        // Sunmi, e.g. L2s
        // Active by default
        // Configure via Settings > System > Scanner Setting > Data Output Mode > Output via Broadcast
        filter.addAction("com.android.scanner.ACTION_DATA_CODE_RECEIVED")
        filter.addAction("com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED")

        ContextCompat.registerReceiver(ctx, scanReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun stop(ctx: Context) {
        try {
            ctx.unregisterReceiver(scanReceiver)
        } catch (exception: Exception) {
            // Scanner has probably been already stopped elsewhere.
        }
    }
}

fun defaultToScanner(): Boolean {
    Log.i("HardwareScanner", "Detecting brand='${Build.BRAND}' model='${Build.MODEL}'")
    return when (Build.BRAND) {
        "Zebra" -> Build.MODEL.startsWith("TC") || Build.MODEL.startsWith("M") || Build.MODEL.startsWith("CC6") || Build.MODEL.startsWith("EC")
        "Bluebird" -> Build.MODEL.startsWith("EF")
        "NewLand" -> Build.MODEL.startsWith("NQ")
        "Newland" -> Build.MODEL.startsWith("NLS-NQ")
        "Honeywell" -> Build.MODEL.startsWith("EDA")
        "SUNMI" -> Build.MODEL.startsWith("L")
        "SEUIC" -> Build.MODEL.startsWith("AUTOID Pad Air")
        else -> false
    }
}
