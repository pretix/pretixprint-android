package eu.pretix.pretixprint.ui

import android.content.Context
import android.preference.EditTextPreference
import android.util.AttributeSet

class ProtectedEditTextPreference(context: Context, attrs: AttributeSet) :
    EditTextPreference(context, attrs) {

    var earlyClickListener: OnPreferenceClickListener? = null

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
        super.showDialog(null)
    }
}