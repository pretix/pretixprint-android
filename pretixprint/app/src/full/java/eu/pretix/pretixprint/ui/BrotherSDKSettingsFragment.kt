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
import com.google.android.material.textfield.TextInputLayout
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.BrotherSDK
import org.jetbrains.anko.support.v4.defaultSharedPreferences

class BrotherSDKSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_brothersdk_settings, container, false)
        val proto = BrotherSDK()

        // Printer model
        val printerModelAdapter = ArrayAdapter(requireContext(), R.layout.list_item, BrotherSDK.PrinterModel.values().map {
            it.modelName
        })
        (view.findViewById<TextInputLayout>(R.id.tilPrinterModel).editText as? AutoCompleteTextView)?.setAdapter(printerModelAdapter)

        val chosenPrinterModelId = ((activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_brothermodel"
        )) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_brothermodel", "")
        if (chosenPrinterModelId?.isNotEmpty() == true) {
            val chosenPrinterModel = BrotherSDK.PrinterModel.values().find { it.name == chosenPrinterModelId }!!
            (view.findViewById<TextInputLayout>(R.id.tilPrinterModel).editText as? AutoCompleteTextView)?.setText(chosenPrinterModel.modelName, false)
        }

        // Label size
        val labelSizeAdapter = ArrayAdapter(requireContext(), R.layout.list_item, BrotherSDK.Label.values().map {
            translatedLabelName(it)
        })
        (view.findViewById<TextInputLayout>(R.id.tilLabelSize).editText as? AutoCompleteTextView)?.setAdapter(labelSizeAdapter)

        val chosenLabelId = ((activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_label"
        )) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_label", "")
        if (chosenLabelId?.isNotEmpty() == true) {
            val chosenLabel = translatedLabelName(BrotherSDK.Label.values().find { it.name == chosenLabelId }!!)
            (view.findViewById<TextInputLayout>(R.id.tilLabelSize).editText as? AutoCompleteTextView)?.setText(chosenLabel, false)
        }

        // Resolution
        val resolutionAdapter = ArrayAdapter(requireContext(), R.layout.list_item, BrotherSDK.Resolution.values().map {
            it.sdkResolution
        })
        (view.findViewById<TextInputLayout>(R.id.tilResolution).editText as? AutoCompleteTextView)?.setAdapter(resolutionAdapter)

        val chosenResolutionId = ((activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_brotherresolution"
        )) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_brotherresolution", "")
        if (chosenResolutionId?.isNotEmpty() == true) {
            val chosenResolution = BrotherSDK.Resolution.values().find { it.name == chosenResolutionId }!!
            (view.findViewById<TextInputLayout>(R.id.tilResolution).editText as? AutoCompleteTextView)?.setText(chosenResolution.text, false)
        }

        // Switches
        val currentAutoCut = ((activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_autocut"
        )?.toBoolean() ) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_autocut", "true")!!.toBoolean()
        view.findViewById<SwitchMaterial>(R.id.swAutocut).isChecked = currentAutoCut

        val currentRotate90 = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_rotate90"
        )?.toBoolean() ) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_rotate90", "false")!!.toBoolean()
        view.findViewById<SwitchMaterial>(R.id.swRotate90).isChecked = currentRotate90

        val currentQuality = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_quality"
        )?.toBoolean() ) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_quality", "false")!!.toBoolean()
        view.findViewById<SwitchMaterial>(R.id.swQuality).isChecked = currentQuality

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }

        // Next button
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val printer = view.findViewById<TextInputLayout>(R.id.tilPrinterModel).editText?.text.toString()
            val label = view.findViewById<TextInputLayout>(R.id.tilLabelSize).editText?.text.toString()
            val resolution = view.findViewById<TextInputLayout>(R.id.tilResolution).editText?.text.toString()
            val autoCut = view.findViewById<SwitchMaterial>(R.id.swAutocut).isChecked
            val quality = view.findViewById<SwitchMaterial>(R.id.swQuality).isChecked
            val rotate90 = view.findViewById<SwitchMaterial>(R.id.swRotate90).isChecked

            // Check if required fields have been set to a value
            var fieldsSet = true
            if (TextUtils.isEmpty(printer)) {
                view.findViewById<TextInputLayout>(R.id.tilPrinterModel).error = getString(R.string.err_field_required)
                fieldsSet = false
            }
            if (TextUtils.isEmpty(label)) {
                view.findViewById<TextInputLayout>(R.id.tilLabelSize).error = getString(R.string.err_field_required)
                fieldsSet = false
            }
            if (TextUtils.isEmpty(resolution)) {
                view.findViewById<TextInputLayout>(R.id.tilResolution).error = getString(R.string.err_field_required)
                fieldsSet = false
            }
            // Only continue to next page if all required fields have been set
            if (fieldsSet) {
                val mappedModel = BrotherSDK.PrinterModel.values().find { it.modelName == printer }!!.name
                val mappedLabel = BrotherSDK.Label.values().find { translatedLabelName(it) == label }!!.name
                val mappedResolution = BrotherSDK.Resolution.values().find { it.text == resolution }!!.name

                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_brothermodel",
                    mappedModel)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_label",
                    mappedLabel)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_brotherresolution",
                    mappedResolution)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_autocut", autoCut.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_rotate90", rotate90.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_quality", quality.toString())

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

    private fun translatedLabelName(label: BrotherSDK.Label): String {
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
}
