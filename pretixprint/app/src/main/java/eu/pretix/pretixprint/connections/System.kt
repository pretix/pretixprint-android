package eu.pretix.pretixprint.connections

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import android.print.PrintAttributes.*
import com.lowagie.text.pdf.PdfReader
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

class SystemConnection : ConnectionType {
    override val identifier = "system"
    override val nameResource =  R.string.connection_type_system
    override val inputType = ConnectionType.Input.PDF

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }

    override fun print(
        tmpfile: File,
        numPages: Int,
        context: Context,
        useCase: String,
        settings: Map<String, String>?
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = tmpfile.nameWithoutExtension

        val `in` = FileInputStream(tmpfile)
        val pdf = PdfReader(`in`)
        var mediaSize : MediaSize = MediaSize.UNKNOWN_PORTRAIT
        val ps = pdf.getPageSize(1)
        val rot = pdf.getPageRotation(1)
        var isLandscape = false
        if ((ps.width > ps.height && (rot == 0 || rot == 180)) || (ps.height > ps.width && (rot == 90 || rot == 270))) {
            isLandscape = true
            mediaSize = MediaSize.UNKNOWN_LANDSCAPE
        }
        pdf.close()
        `in`.close()

        // try to match known paper sizes to the size of this pdf
        getAllPredefinedSizes().find {
            val mS = if (isLandscape) it.asLandscape() else it
            val heightMils = (ps.height * 1000.0 / 72.0).roundToInt()
            val widthMils = (ps.width * 1000.0 / 72.0).roundToInt()
            val clearance = 20

            mS.heightMils in (heightMils - clearance)..(heightMils + clearance) &&
                mS.widthMils in (widthMils - clearance)..(widthMils + clearance)
        }?.let {
            mediaSize = if (isLandscape) it.asLandscape() else it
        }

        val pa = Builder()
            .setMediaSize(mediaSize)
            .build()

        val pj = printManager.print(jobName, SystemPrintDocumentAdapter(tmpfile, numPages), pa)

        if (pj.isBlocked || pj.isCancelled || pj.isFailed) {
            val msg = when {
                pj.isBlocked -> "blocked"
                pj.isCancelled -> "cancelled"
                pj.isFailed -> "failed"
                else -> "unknown"
            }
            throw PrintException(context.applicationContext.getString(R.string.err_job_io, msg))
        }
    }

    // Card sizes
    /** CR79 media size: 52 mm x 84 mm (2.051" x 3.303")  */
    val CR79 = MediaSize("CR79", "CR79", 2051, 3303)

    /** CR80 media size: 54 mm x 86 mm (2.125" x 3.375")  */
    val CR80 = MediaSize("CR80", "CR80", 2125, 3375)

    /** CR100 media size: 67 mm x 99 mm (2.63" x 3.88")  */
    val CR100 = MediaSize("CR100", "CR100", 2630, 3880)

    // MediaSize.getAllPredefinedSizes() is not accessible, so we have to mirror the result here :|
    private fun getAllPredefinedSizes(): List<MediaSize> = arrayListOf(
        // ISO sizes
        MediaSize.ISO_A0,
        MediaSize.ISO_A1,
        MediaSize.ISO_A2,
        MediaSize.ISO_A3,
        MediaSize.ISO_A4,
        MediaSize.ISO_A5,
        MediaSize.ISO_A6,
        MediaSize.ISO_A7,
        MediaSize.ISO_A8,
        MediaSize.ISO_A9,
        MediaSize.ISO_A10,
        MediaSize.ISO_B0,
        MediaSize.ISO_B1,
        MediaSize.ISO_B2,
        MediaSize.ISO_B3,
        MediaSize.ISO_B4,
        MediaSize.ISO_B5,
        MediaSize.ISO_B6,
        MediaSize.ISO_B7,
        MediaSize.ISO_B8,
        MediaSize.ISO_B9,
        MediaSize.ISO_B10,
        MediaSize.ISO_C0,
        MediaSize.ISO_C1,
        MediaSize.ISO_C2,
        MediaSize.ISO_C3,
        MediaSize.ISO_C4,
        MediaSize.ISO_C5,
        MediaSize.ISO_C6,
        MediaSize.ISO_C7,
        MediaSize.ISO_C8,
        MediaSize.ISO_C9,
        MediaSize.ISO_C10,

        // North America
        MediaSize.NA_LETTER,
        MediaSize.NA_GOVT_LETTER,
        MediaSize.NA_LEGAL,
        MediaSize.NA_JUNIOR_LEGAL,
        MediaSize.NA_LEDGER,
        MediaSize.NA_TABLOID,
        MediaSize.NA_INDEX_3X5,
        MediaSize.NA_INDEX_4X6,
        MediaSize.NA_INDEX_5X8,
        MediaSize.NA_MONARCH,
        MediaSize.NA_QUARTO,
        MediaSize.NA_FOOLSCAP,

        // Chinese
        MediaSize.ROC_8K,
        MediaSize.ROC_16K,
        MediaSize.PRC_1,
        MediaSize.PRC_2,
        MediaSize.PRC_3,
        MediaSize.PRC_4,
        MediaSize.PRC_5,
        MediaSize.PRC_6,
        MediaSize.PRC_7,
        MediaSize.PRC_8,
        MediaSize.PRC_9,
        MediaSize.PRC_10,
        MediaSize.PRC_16K,
        MediaSize.OM_PA_KAI,
        MediaSize.OM_DAI_PA_KAI,
        MediaSize.OM_JUURO_KU_KAI,

        // Japanese
        MediaSize.JIS_B10,
        MediaSize.JIS_B9,
        MediaSize.JIS_B8,
        MediaSize.JIS_B7,
        MediaSize.JIS_B6,
        MediaSize.JIS_B5,
        MediaSize.JIS_B4,
        MediaSize.JIS_B3,
        MediaSize.JIS_B2,
        MediaSize.JIS_B1,
        MediaSize.JIS_B0,
        MediaSize.JIS_EXEC,
        MediaSize.JPN_CHOU4,
        MediaSize.JPN_CHOU3,
        MediaSize.JPN_CHOU2,
        MediaSize.JPN_HAGAKI,
        MediaSize.JPN_OUFUKU,
        MediaSize.JPN_KAHU,
        MediaSize.JPN_KAKU2,
        MediaSize.JPN_YOU4,
        // MediaSize.JPN_OE_PHOTO_L, // requires api 31

        // Card sizes
        CR79,
        CR80,
        CR100,
    )

}

class SystemPrintDocumentAdapter(var tmpfile: File, var numPages: Int) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(tmpfile.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(numPages)
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }

        val out = FileOutputStream(destination!!.fileDescriptor)
        val `in` = FileInputStream(tmpfile)
        val buffer = ByteArray(1024)
        var size = `in`.read(buffer)
        while (size != -1) {
            out.write(buffer, 0, size)
            size = `in`.read(buffer)
        }
        `in`.close()
        out.close()

        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }
}