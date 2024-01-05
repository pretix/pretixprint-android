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
import eu.pretix.pretixprint.byteprotocols.TSPL

class TSPLSettingsFragment : SetupFragment() {
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val view = inflater.inflate(R.layout.fragment_tspl_settings, container, false)
        val proto = TSPL()

        // DPI Setting
        val currentDPI = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_dpi"
        ) as String?)
                ?: prefs.getString("hardware_${useCase}printer_dpi", proto.defaultDPI.toString())
        view.findViewById<TextInputEditText>(R.id.teDPI).setText(currentDPI)


        // Rotation Setting
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

        // Max Width Setting
        val currentLabelWidth = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_maxwidth"
        ) as String?)
                ?: prefs.getString("hardware_${useCase}printer_dpi", proto.defaultMaxWidth.toString())
        view.findViewById<TextInputEditText>(R.id.teMaxWidth).setText(currentLabelWidth)

        // Max Height/Length Setting
        val currentMaxLength = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_maxlength"
        ) as String?)
                ?: prefs.getString("hardware_${useCase}printer_maxlength", proto.defaultMaxLength.toString())
        view.findViewById<TextInputEditText>(R.id.teMaxLength).setText(currentMaxLength)

        // Speed Setting
        val currentSpeed = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_speed"
        ) as String?)
                ?: prefs.getString("hardware_${useCase}printer_speed", proto.defaultSpeed.toString())
        view.findViewById<TextInputEditText>(R.id.teSpeed).setText(currentSpeed)

        // Density Setting
        val currentDensity = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_density"
        ) as String?)
                ?: prefs.getString("hardware_${useCase}printer_density", proto.defaultDensity.toString())
        view.findViewById<TextInputEditText>(R.id.teDensity).setText(currentDensity)


        // Back Button
        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val dpi = view.findViewById<TextInputEditText>(R.id.teDPI).text.toString()
            val rotation = view.findViewById<TextInputLayout>(R.id.tilRotation).editText?.text.toString()
            val maxWidth = view.findViewById<TextInputEditText>(R.id.teMaxWidth).text.toString()
            val maxLength = view.findViewById<TextInputEditText>(R.id.teMaxLength).text.toString()
            val speed = view.findViewById<TextInputEditText>(R.id.teSpeed).text.toString()
            val density = view.findViewById<TextInputEditText>(R.id.teDensity).text.toString()


            if (TextUtils.isEmpty(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_invalid)
            } else if (TextUtils.isEmpty(maxWidth)) {
                view.findViewById<TextInputEditText>(R.id.teMaxWidth).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(maxWidth)) {
                view.findViewById<TextInputEditText>(R.id.teMaxWidth).error = getString(R.string.err_field_invalid)
            } else if (TextUtils.isEmpty(maxLength)) {
                view.findViewById<TextInputEditText>(R.id.teMaxLength).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(maxLength)) {
                view.findViewById<TextInputEditText>(R.id.teMaxLength).error = getString(R.string.err_field_invalid)
            } else if (TextUtils.isEmpty(speed)) {
                view.findViewById<TextInputEditText>(R.id.teSpeed).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(speed)) {
                view.findViewById<TextInputEditText>(R.id.teSpeed).error = getString(R.string.err_field_invalid)
            }  else if (TextUtils.isEmpty(density)) {
                view.findViewById<TextInputEditText>(R.id.teDensity).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(density)) {
                view.findViewById<TextInputEditText>(R.id.teDensity).error = getString(R.string.err_field_invalid)
            } else {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = null
                view.findViewById<TextInputEditText>(R.id.teMaxWidth).error = null
                view.findViewById<TextInputEditText>(R.id.teMaxLength).error = null
                view.findViewById<TextInputEditText>(R.id.teSpeed).error = null
                view.findViewById<TextInputEditText>(R.id.teDensity).error = null


                val mappedRotation = Rotation.values().find { it.toString() == rotation }!!.degrees

                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_rotation", mappedRotation.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_dpi", dpi)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_maxwidth", maxWidth)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_maxlength", maxLength)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_speed", speed)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_density", density)


                (activity as PrinterSetupActivity).startFinalPage()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolChoice()
    }
}