package eu.pretix.pretixprint.print

import android.content.Context
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File

abstract class PrintServiceTransport(context: Context, type: String, var settings: Map<String, String>? = null) {
    val type = type
    val context = context

    abstract fun print(tmpfile: File, numPages: Int)

    fun getSetting(key: String, def: String=""): String {
        if (settings == null) {
            settings = emptyMap()
        }
        return settings!![key] ?: context.defaultSharedPreferences.getString(key, def)!!
    }

}