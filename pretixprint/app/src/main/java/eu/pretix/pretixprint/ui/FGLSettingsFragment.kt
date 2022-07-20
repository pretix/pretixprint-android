package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.FGL
import org.jetbrains.anko.support.v4.defaultSharedPreferences

class FGLSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_fgl_settings, container, false)
        val proto = FGL()

        val pathAdapter = ArrayAdapter(requireContext(), R.layout.list_item, FGL.Ticketpath.values().map {
            it.id.toString()
        })
        (view.findViewById<TextInputLayout>(R.id.tilPath).editText as? AutoCompleteTextView)?.setAdapter(pathAdapter)

        val chosenPathId = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_path"
        )) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_path", "1")
        if (chosenPathId?.isNotEmpty() == true) {
            val chosenPath = FGL.Ticketpath.values().find { it.id.toString() == chosenPathId }!!.id.toString()
            (view.findViewById<TextInputLayout>(R.id.tilPath).editText as? AutoCompleteTextView)?.setText(chosenPath, false)
        }

        val currentDPI = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_dpi"
        ) as String?) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_dpi", proto.defaultDPI.toString())
        view.findViewById<TextInputEditText>(R.id.teDPI).setText(currentDPI)

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val path = view.findViewById<TextInputLayout>(R.id.tilPath).editText?.text.toString()
            val dpi = view.findViewById<TextInputEditText>(R.id.teDPI).text.toString()
            if (TextUtils.isEmpty(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_invalid)
            } else if (TextUtils.isEmpty(path)) {
                view.findViewById<TextInputEditText>(R.id.tilPath).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(path)) {
                view.findViewById<TextInputEditText>(R.id.tilPath).error = getString(R.string.err_field_invalid)
            } else {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = null
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_dpi",
                        dpi)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_path",
                        path)
                (activity as PrinterSetupActivity).startFinalPage()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolChoice()
    }
}
