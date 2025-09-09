package eu.pretix.pretixprint.ui

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.preference.EditTextPreference

class ProtectedEditTextPreference(context: Context, attrs: AttributeSet) :
    EditTextPreference(context, attrs) {

    var earlyClickListener: OnPreferenceClickListener? = null

    init {
        // Input Type für numerische PIN-Eingabe setzen
        setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }
    }

    fun setEarlyPreferenceClickListener(onPreferenceClickListener: OnPreferenceClickListener) {
        earlyClickListener = onPreferenceClickListener
    }

    override fun onClick() {
        if (earlyClickListener != null && earlyClickListener!!.onPreferenceClick(this)) {
            return
        }

        super.onClick()
    }

    fun showDialog() {
        preferenceManager.showDialog(this)
    }
}