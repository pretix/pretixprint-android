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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.Rotation

class SunmiPrinterXLabelsSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val view = inflater.inflate(R.layout.fragment_sunmiprinterxlabels_settings, container, false)

        val currentRollWidth = (activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_sunmiprinterxlabels_roll_width"
        ) ?: prefs.getString("hardware_${useCase}printer_sunmiprinterxlabels_roll_width", "")
        view.findViewById<TextInputEditText>(R.id.teRollWidth).setText(currentRollWidth)

        val currentLabelWidth = (activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_sunmiprinterxlabels_label_width"
        ) ?: prefs.getString("hardware_${useCase}printer_sunmiprinterxlabels_label_width", "")
        view.findViewById<TextInputEditText>(R.id.teLabelWidth).setText(currentLabelWidth)

        val currentLabelHeight = (activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_sunmiprinterxlabels_label_height"
        ) ?: prefs.getString("hardware_${useCase}printer_sunmiprinterxlabels_label_height", "")
        view.findViewById<TextInputEditText>(R.id.teLabelHeight).setText(currentLabelHeight)

        val printWidthAdapter = ArrayAdapter(requireContext(), R.layout.list_item, PrintWidth.values().map {
            it.toString()
        })
        (view.findViewById<TextInputLayout>(R.id.tilPrintWidth).editText as? AutoCompleteTextView)?.setAdapter(printWidthAdapter)
        val chosenPrintWidth = ((activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_sunmiprinterxlabels_print_width"
        )) ?: prefs.getString("hardware_${useCase}printer_sunmiprinterxlabels_print_width", "58")
        if (chosenPrintWidth?.isNotEmpty() == true) {
            val chosenLabel = PrintWidth.values().find { it.paperWidthMm == Integer.valueOf(chosenPrintWidth) }!!.toString()
            (view.findViewById<TextInputLayout>(R.id.tilPrintWidth).editText as? AutoCompleteTextView)?.setText(chosenLabel, false)
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

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            var noErrors = true

            val teRollWidth = view.findViewById<TextInputEditText>(R.id.teRollWidth)
            val teLabelWidth = view.findViewById<TextInputEditText>(R.id.teLabelWidth)
            val teLabelHeight = view.findViewById<TextInputEditText>(R.id.teLabelHeight)

            val printWidth = view.findViewById<TextInputLayout>(R.id.tilPrintWidth).editText?.text.toString()
            val mappedPrintWidth = PrintWidth.values().find {it.toString() == printWidth}!!.paperWidthMm
            val rollWidth = teRollWidth.text.toString()
            val labelWidth = teLabelWidth.text.toString()
            val labelHeight = teLabelHeight.text.toString()
            val rotation = view.findViewById<TextInputLayout>(R.id.tilRotation).editText?.text.toString()
            val mappedRotation = Rotation.values().find { it.toString() == rotation }!!.degrees

            // Validation of entered data
            if (TextUtils.isEmpty(rollWidth)) {
                teRollWidth.error = getString(R.string.err_field_required)
                noErrors = false
            } else if (!TextUtils.isDigitsOnly(rollWidth)) {
                teRollWidth.error = getString(R.string.err_field_invalid)
            } else if (Integer.parseInt(rollWidth) < 30) {
                teRollWidth.error = getString(R.string.err_field_sunmiprinterxlabels_roll_width_small)
                noErrors = false
            } else if (Integer.parseInt(rollWidth) > mappedPrintWidth) {
                teRollWidth.error = getString(R.string.err_field_sunmiprinterxlabels_roll_width_large)
                noErrors = false
            } else {
                teRollWidth.error = null
            }

            if (TextUtils.isEmpty(labelWidth)) {
                teLabelWidth.error = getString(R.string.err_field_required)
                noErrors = false
            } else if (!TextUtils.isDigitsOnly(labelWidth)) {
                teLabelWidth.error = getString(R.string.err_field_invalid)
            } else if (Integer.parseInt(labelWidth) < 30-3) {
                teLabelWidth.error = getString(R.string.err_field_sunmiprinterxlabels_label_width_small)
                noErrors = false
            } else if (Integer.parseInt(labelWidth) > Integer.parseInt(rollWidth)) {
                teLabelWidth.error = getString(R.string.err_field_sunmiprinterxlabels_label_width_large)
                noErrors = false
            } else {
                teLabelWidth.error = null
            }

            if (TextUtils.isEmpty(labelHeight)) {
                teLabelHeight.error = getString(R.string.err_field_required)
                noErrors = false
            } else if (!TextUtils.isDigitsOnly(labelHeight)) {
                teLabelHeight.error = getString(R.string.err_field_invalid)
            } else if (Integer.parseInt(labelHeight) < 20) {
                teLabelHeight.error = getString(R.string.err_field_sunmiprinterxlabels_label_height_small)
                noErrors = false
            } else {
                teLabelHeight.error = null
            }

            if (noErrors) {

                val totalPrinterMargin = if (mappedPrintWidth == 58) 10 else 8
                val totalLinerMargin = Integer.parseInt(rollWidth) - Integer.parseInt(labelWidth)

                val totalHorizontalMargin = maxOf(totalPrinterMargin, totalLinerMargin)

                val designWidth : Int
                val designHeight : Int

                if ((mappedRotation == 90) or (mappedRotation == 270)) {
                    designWidth = Integer.parseInt(labelHeight)
                    designHeight = Integer.parseInt(rollWidth) - totalHorizontalMargin

                } else {
                    designWidth = Integer.parseInt(rollWidth) -  totalHorizontalMargin
                    designHeight = Integer.parseInt(labelHeight)
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.title_design_dimensions))
                    .setMessage(getString(R.string.dialog_calculated_dimensions, designWidth, designHeight))
                    .setPositiveButton(getString(R.string.btn_ok)) { dialog, which -> }
                    .show()

                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_sunmiprinterxlabels_print_width", mappedPrintWidth.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_sunmiprinterxlabels_roll_width", rollWidth)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_sunmiprinterxlabels_label_width", labelWidth)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_sunmiprinterxlabels_label_height", labelHeight)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_rotation", mappedRotation.toString())
                (activity as PrinterSetupActivity).startFinalPage()
            }


        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolChoice(true)
    }
}

enum class PrintWidth(val paperWidthMm: Int, val printableWidthMm: Int, val printableWidthPx: Int) {
    mm58(paperWidthMm = 58, printableWidthMm = 48, printableWidthPx = 384),
    mm80(paperWidthMm = 80, printableWidthMm = 72, printableWidthPx = 576);

    override fun toString(): String {
        return "$paperWidthMm mm"
    }
}
