package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.BrotherRaster
import org.jetbrains.anko.support.v4.defaultSharedPreferences

class BrotherRasterSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_brotherraster_settings, container, false)

        val labelAdapter = ArrayAdapter(requireContext(), R.layout.list_item, BrotherRaster.Label.values())
        (view.findViewById<TextInputLayout>(R.id.tilLabel).editText as? AutoCompleteTextView)?.setAdapter(labelAdapter)

        val chosenLabelId = ((activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_label"
        )) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_label", "")
        if (chosenLabelId?.isNotEmpty() == true) {
            val chosenLabel = BrotherRaster.Label.values().find { it.name == chosenLabelId }.toString()
            (view.findViewById<TextInputLayout>(R.id.tilLabel).editText as? AutoCompleteTextView)?.setText(chosenLabel, false)
        }

        val currentWaitAfterPage = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_waitafterpage"
        )) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_waitafterpage", "2000")
        view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).setText(currentWaitAfterPage)

        val currentRotate90 = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_rotate90"
        )?.toBoolean() ) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_rotate90", "false")!!.toBoolean()
        view.findViewById<SwitchMaterial>(R.id.swRotate90).isChecked = currentRotate90

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val wap = view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).text.toString()
            val label = view.findViewById<TextInputLayout>(R.id.tilLabel).editText?.text.toString()
            val rotate90 = view.findViewById<SwitchMaterial>(R.id.swRotate90).isChecked
            if (TextUtils.isEmpty(wap)) {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = getString(R.string.err_field_required)
            } else if (TextUtils.isEmpty(label)) {
                view.findViewById<TextInputEditText>(R.id.tilLabel).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(wap)) {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = getString(R.string.err_field_invalid)
            } else {
                val mappedLabel = BrotherRaster.Label.values().find { it.toString() == label }!!.name

                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_rotate90", rotate90.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_label",
                    mappedLabel)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_waitafterpage",
                        wap)
                (activity as PrinterSetupActivity).startFinalPage()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolChoice()
    }
}
