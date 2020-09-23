package eu.pretix.pretixprint.connections

import android.content.Context
import android.text.InputType
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
}