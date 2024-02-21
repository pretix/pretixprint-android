package eu.pretix.pretixprint.connections

import android.content.Context
import androidx.preference.PreferenceManager
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
    fun print(tmpfile: File, numPages: Int, pagegroups: List<Int>, context: Context, useCase: String, settings: Map<String, String>? = null)

    fun isConfiguredFor(context: Context, type: String): Boolean {
        return !PreferenceManager.getDefaultSharedPreferences(context).getString("hardware_${type}printer_ip", "").isNullOrEmpty()
    }
}