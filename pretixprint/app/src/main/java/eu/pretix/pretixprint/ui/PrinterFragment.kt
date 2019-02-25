package eu.pretix.pretixprint.ui

import androidx.fragment.app.Fragment

abstract class PrinterFragment : Fragment() {

    fun getType() : String {
        return arguments!!.getString("type")
    }

    fun getConnection() : String {
        return arguments!!.getString("connection")
    }

    abstract fun validate() : Boolean

    abstract fun savePrefs()
}