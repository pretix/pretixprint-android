package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R
import org.jetbrains.anko.support.v4.defaultSharedPreferences

class FinishSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_finish_settings, container, false)

        view.findViewById<Button>(R.id.btnTestPage).setOnClickListener {
            // TODO
        }
        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            (activity as PrinterSetupActivity).startProtocolSettings(true)
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            (activity as PrinterSetupActivity).save()
            (activity as PrinterSetupActivity).finish()
        }

        return view
    }
}
