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
        if (rot == 90 || rot == 180) {
            mediaSize = MediaSize.UNKNOWN_LANDSCAPE
        } else if (ps.width > ps.height) {
            mediaSize = MediaSize.UNKNOWN_LANDSCAPE
        }
        pdf.close()
        `in`.close()

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