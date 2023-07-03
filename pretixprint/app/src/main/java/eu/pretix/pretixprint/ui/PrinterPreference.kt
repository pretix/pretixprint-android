package eu.pretix.pretixprint.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.VISIBLE
import android.widget.ImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import eu.pretix.pretixprint.R

class PrinterPreference(context: Context, attrs: AttributeSet, defStyleAttr: Int) :
    Preference(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0)

    private val mLayoutResId: Int = R.layout.preference_two_target
    private val mWidgetLayoutResId = R.layout.preference_more_button

    private var button: ImageButton? = null
    private var divider: View? = null

    var setOnMenuItemClickListener = fun(_: MenuItem): Boolean { return false }
    var moreVisibility = VISIBLE
        set(value) {
            field = value
            divider?.visibility = moreVisibility
            button?.visibility = moreVisibility
        }


    init {
        layoutResource = mLayoutResId
        widgetLayoutResource = mWidgetLayoutResId
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        divider = holder.itemView.findViewById(R.id.two_target_divider)
        button = holder.itemView.findViewById(R.id.iBMore)
        val popup = PopupMenu(context, button!!)
        val inflater: MenuInflater = popup.menuInflater
        inflater.inflate(R.menu.menu_printer_operations, popup.menu)
        popup.setOnMenuItemClickListener(setOnMenuItemClickListener)
        button?.setOnClickListener {
            popup.show()
        }
        divider?.visibility = moreVisibility
        button?.visibility = moreVisibility
        super.onBindViewHolder(holder)
    }
}