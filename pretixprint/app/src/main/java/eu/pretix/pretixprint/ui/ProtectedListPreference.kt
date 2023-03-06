package eu.pretix.pretixprint.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference

class ProtectedListPreference(context: Context, attrs: AttributeSet):
    ListPreference(context, attrs) {

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
        preferenceManager.showDialog(this)
    }
}