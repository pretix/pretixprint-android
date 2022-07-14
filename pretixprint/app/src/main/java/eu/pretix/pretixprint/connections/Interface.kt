package eu.pretix.pretixprint.connections

import android.content.Context
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File

interface ConnectionType {
    enum class Input {
        PDF,
        PLAIN_BYTES
    }

    val identifier: String
    val nameResource: Int
    val inputType: Input

    fun allowedForUsecase(type: String): Boolean
    fun print(tmpfile: File, numPages: Int, context: Context, useCase: String, settings: Map<String, String>? = null)

    fun isConfiguredFor(context: Context, type: String): Boolean {
        return !context.defaultSharedPreferences.getString("hardware_${type}printer_ip", "").isNullOrEmpty()
    }
}