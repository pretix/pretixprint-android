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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.Rotation
import eu.pretix.pretixprint.byteprotocols.BrotherRaster

class BrotherRasterSettingsFragment : SetupFragment() {

    private fun translatedLabelName(label: BrotherRaster.Label): String {
        var suffix = ""
        if (label.continuous) {
            suffix = getString(R.string.label_continuous)
        }
        if (label.twoColor) {
            if (suffix != "") {
                suffix += ", " + getString(R.string.label_two_color)
            } else {
                suffix = getString(R.string.label_two_color)
            }
        }
        return label.size() + (if (suffix != "") " (${suffix})" else "")
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val view = inflater.inflate(R.layout.fragment_brotherraster_settings, container, false)
        val proto = BrotherRaster()

        val labelAdapter = ArrayAdapter(requireContext(), R.layout.list_item, BrotherRaster.Label.values().map {
            translatedLabelName(it)
        })
        (view.findViewById<TextInputLayout>(R.id.tilLabel).editText as? AutoCompleteTextView)?.setAdapter(labelAdapter)

        val chosenLabelId = ((activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_label"
        )) ?: prefs.getString("hardware_${useCase}printer_label", "")
        if (chosenLabelId?.isNotEmpty() == true) {
            val chosenLabel = translatedLabelName(BrotherRaster.Label.values().find { it.name == chosenLabelId }!!)
            (view.findViewById<TextInputLayout>(R.id.tilLabel).editText as? AutoCompleteTextView)?.setText(chosenLabel, false)
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

        val currentQuality = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_quality"
        )?.toBoolean() ) ?: prefs.getString("hardware_${useCase}printer_quality", "false")!!.toBoolean()
        view.findViewById<SwitchMaterial>(R.id.swQuality).isChecked = currentQuality

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val label = view.findViewById<TextInputLayout>(R.id.tilLabel).editText?.text.toString()
            val quality = view.findViewById<SwitchMaterial>(R.id.swQuality).isChecked
            val rotation = view.findViewById<TextInputLayout>(R.id.tilRotation).editText?.text.toString()
            if (TextUtils.isEmpty(label)) {
                view.findViewById<TextInputLayout>(R.id.tilLabel).error = getString(R.string.err_field_required)
            } else {
                val mappedLabel = BrotherRaster.Label.values().find { translatedLabelName(it) == label }!!.name
                val mappedRotation = Rotation.values().find { it.toString() == rotation }!!.degrees

                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_rotation", mappedRotation.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_quality", quality.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_label",
                    mappedLabel)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_dpi",
                    proto.defaultDPI.toString())
                (activity as PrinterSetupActivity).startFinalPage()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolChoice()
    }
}
