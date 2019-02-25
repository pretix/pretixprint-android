package eu.pretix.pretixprint.print

import android.content.Context
import java.io.File

abstract class PrintServiceTransport(context: Context, type: String) {
    val type = type
    val context = context

    abstract fun print(tmpfile: File)
}