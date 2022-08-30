package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.PNG
import org.jetbrains.anko.support.v4.defaultSharedPreferences

class PNGSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_png_settings, container, false)
        val proto = PNG()

        val currentDPI = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_dpi"
        ) as String?)
                ?: defaultSharedPreferences.getString("hardware_${useCase}printer_dpi", proto.defaultDPI.toString())
        view.findViewById<TextInputEditText>(R.id.teDPI).setText(currentDPI)

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val dpi = view.findViewById<TextInputEditText>(R.id.teDPI).text.toString()
            if (TextUtils.isEmpty(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_invalid)
            } else {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = null
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_dpi",
                        dpi)
                (activity as PrinterSetupActivity).startFinalPage()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolChoice()
    }
}
