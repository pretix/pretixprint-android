package eu.pretix.pretixprint.connections

import android.text.InputType

interface ConnectionType {
    enum class Input {
        PDF,
        PLAIN_BYTES
    }

    val identifier: String
    val nameResource: Int
    val inputType: Input

    fun allowedForUsecase(type: String): Boolean
}