package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.preference.PreferenceManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.Rotation
import eu.pretix.pretixprint.byteprotocols.FGL

class FGLSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val view = inflater.inflate(R.layout.fragment_fgl_settings, container, false)
        val proto = FGL()

        val pathAdapter = ArrayAdapter(requireContext(), R.layout.list_item, FGL.Ticketpath.values().map {
            it.id.toString()
        })
        (view.findViewById<TextInputLayout>(R.id.tilPath).editText as? AutoCompleteTextView)?.setAdapter(pathAdapter)

        val chosenPathId = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_path"
        )) ?: prefs.getString("hardware_${useCase}printer_path", "1")
        if (chosenPathId?.isNotEmpty() == true) {
            val chosenPath = FGL.Ticketpath.values().find { it.id.toString() == chosenPathId }!!.id.toString()
            (view.findViewById<TextInputLayout>(R.id.tilPath).editText as? AutoCompleteTextView)?.setText(chosenPath, false)
        }

        val cutModeAdapter = ArrayAdapter(requireContext(), R.layout.list_item, FGL.CutMode.values().map {
            getString(it.stringId)
        })
        (view.findViewById<TextInputLayout>(R.id.tilCutMode).editText as? AutoCompleteTextView)?.setAdapter(cutModeAdapter)
        val chosenCutModeId = ((activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_cutmode"
        )) ?: prefs.getString("hardware_${useCase}printer_cutmode", "job")
        if (chosenCutModeId?.isNotEmpty() == true) {
            val chosenCutMode = getString(FGL.CutMode.values().find { it.id == chosenCutModeId }!!.stringId)
            (view.findViewById<TextInputLayout>(R.id.tilCutMode).editText as? AutoCompleteTextView)?.setText(chosenCutMode, false)
        }

        val rotationAdapter = ArrayAdapter(requireContext(), R.layout.list_item, Rotation.values().map {
            it.toString()
        })
        (view.findViewById<TextInputLayout>(R.id.tilRotation).editText as? AutoCompleteTextView)?.setAdapter(rotationAdapter)
        val chosenRotation = ((activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_rotation"
        )) ?: prefs.getString("hardware_${useCase}printer_rotation", "0")
        if (chosenRotation?.isNotEmpty() == true) {
            val chosenLabel = Rotation.values().find { it.degrees == Integer.valueOf(chosenRotation) }!!.toString()
            (view.findViewById<TextInputLayout>(R.id.tilRotation).editText as? AutoCompleteTextView)?.setText(chosenLabel, false)
        }

        val currentDPI = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_dpi"
        ) as String?) ?: prefs.getString("hardware_${useCase}printer_dpi", proto.defaultDPI.toString())
        view.findViewById<TextInputEditText>(R.id.teDPI).setText(currentDPI)

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val path = view.findViewById<TextInputLayout>(R.id.tilPath).editText?.text.toString()
            val cutmode = view.findViewById<TextInputLayout>(R.id.tilCutMode).editText?.text.toString()
            val dpi = view.findViewById<TextInputEditText>(R.id.teDPI).text.toString()
            val rotation = view.findViewById<TextInputLayout>(R.id.tilRotation).editText?.text.toString()
            if (TextUtils.isEmpty(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_invalid)
            } else if (TextUtils.isEmpty(path)) {
                view.findViewById<TextInputEditText>(R.id.tilPath).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(path)) {
                view.findViewById<TextInputEditText>(R.id.tilPath).error = getString(R.string.err_field_invalid)
            } else if (!FGL.CutMode.values().any { getString(it.stringId) == cutmode }) {
                view.findViewById<TextInputEditText>(R.id.tilPath).error = getString(R.string.err_field_invalid)
            } else {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = null
                val mappedRotation = Rotation.values().find { it.toString() == rotation }!!.degrees

                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_rotation", mappedRotation.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_dpi",
                        dpi)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_path",
                        path)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_cutmode",
                        FGL.CutMode.values().find { getString(it.stringId) == cutmode }!!.id)
                (activity as PrinterSetupActivity).startFinalPage()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolChoice(true)
    }
}
