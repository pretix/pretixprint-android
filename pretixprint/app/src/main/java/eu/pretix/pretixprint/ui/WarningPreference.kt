package eu.pretix.pretixprint.ui

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import eu.pretix.pretixprint.R

class WarningPreference(context: Context, attrs: AttributeSet, defStyleAttr: Int) :
    Preference(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private val mLayoutResId: Int = R.layout.preference_warning

    init {
        layoutResource = mLayoutResId
    }
}