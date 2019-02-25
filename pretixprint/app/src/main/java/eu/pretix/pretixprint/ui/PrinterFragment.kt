package eu.pretix.pretixprint.ui

import androidx.fragment.app.Fragment

abstract class PrinterFragment(type: String, connection: String) : Fragment() {
    private val type = type
    private val connection = connection

    fun getType() : String {
        return type
    }

    fun getConnection() : String {
        return connection
    }

    abstract fun validate() : Boolean

    abstract fun savePrefs()
}