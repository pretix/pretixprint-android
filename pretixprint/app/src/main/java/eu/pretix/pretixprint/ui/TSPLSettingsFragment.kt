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
import eu.pretix.pretixprint.Sensor
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
        ))
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
        ))
                ?: prefs.getString("hardware_${useCase}printer_maxwidth", proto.defaultMaxWidth.toString())
        view.findViewById<TextInputEditText>(R.id.teMaxWidth).setText(currentLabelWidth)

        // Max Height/Length Setting
        val currentMaxLength = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_maxlength"
        ))
                ?: prefs.getString("hardware_${useCase}printer_maxlength", proto.defaultMaxLength.toString())
        view.findViewById<TextInputEditText>(R.id.teMaxLength).setText(currentMaxLength)

        // Speed Setting
        val currentSpeed = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_speed"
        ))
                ?: prefs.getString("hardware_${useCase}printer_speed", proto.defaultSpeed.toString())
        view.findViewById<TextInputEditText>(R.id.teSpeed).setText(currentSpeed)

        // Density Setting
        val currentDensity = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_density"
        ))
                ?: prefs.getString("hardware_${useCase}printer_density", proto.defaultDensity.toString())
        view.findViewById<TextInputEditText>(R.id.teDensity).setText(currentDensity)

        // Sensor Setting
        val sensorAdapter = ArrayAdapter(requireContext(), R.layout.list_item, Sensor.values().map {
            it.toString()
        })
        (view.findViewById<TextInputLayout>(R.id.tilSensor).editText as? AutoCompleteTextView)?.setAdapter(sensorAdapter)
        val chosenSensor = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_sensor"
        )) ?: prefs.getString("hardware_${useCase}printer_sensor", proto.defaultSensor.toString())
        if (chosenSensor?.isNotEmpty() == true) {
            val chosenLabel = Sensor.values().find { it.sensor == Integer.valueOf(chosenSensor) }!!.toString()
            (view.findViewById<TextInputLayout>(R.id.tilSensor).editText as? AutoCompleteTextView)?.setText(chosenLabel, false)
        }

        // Sensor Height Setting
        val currentSensorHeight = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_sensor_height"
        ))
                ?: prefs.getString("hardware_${useCase}printer_sensor_height", proto.defaultSensorHeight.toString())
        view.findViewById<TextInputEditText>(R.id.teSensorHeight).setText(currentSensorHeight)

        // Sensor Offset Setting
        val currentSensorOffset = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_sensor_offset"
        ))
                ?: prefs.getString("hardware_${useCase}printer_sensor_offset", proto.defaultSensorOffset.toString())
        view.findViewById<TextInputEditText>(R.id.teSensorOffset).setText(currentSensorOffset)

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
            val sensor = view.findViewById<TextInputLayout>(R.id.tilSensor).editText?.text.toString()
            val sensorHeight = view.findViewById<TextInputEditText>(R.id.teSensorHeight).text.toString()
            val sensorOffset = view.findViewById<TextInputEditText>(R.id.teSensorOffset).text.toString()

            val doubleRegex = Regex("^(\\d+\\.?\\d*)?\$")
            val intRegex = Regex("^(\\d+)?\$")

            if (TextUtils.isEmpty(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_required)
            } else if (!dpi.matches(intRegex)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_invalid)
            } else if (TextUtils.isEmpty(maxWidth)) {
                view.findViewById<TextInputEditText>(R.id.teMaxWidth).error = getString(R.string.err_field_required)
            } else if (!maxWidth.matches(intRegex)) {
                view.findViewById<TextInputEditText>(R.id.teMaxWidth).error = getString(R.string.err_field_invalid)
            } else if (!maxLength.matches(intRegex)) {
                view.findViewById<TextInputEditText>(R.id.teMaxLength).error = getString(R.string.err_field_invalid)
            } else if (!speed.matches(intRegex)) {
                view.findViewById<TextInputEditText>(R.id.teSpeed).error = getString(R.string.err_field_invalid)
            } else if (!density.matches(intRegex)) {
                view.findViewById<TextInputEditText>(R.id.teDensity).error = getString(R.string.err_field_invalid)
            } else if (!sensorHeight.matches(doubleRegex)) {
                view.findViewById<TextInputEditText>(R.id.teSensorHeight).error = getString(R.string.err_field_invalid)
            } else if (!sensorOffset.matches(doubleRegex)) {
                view.findViewById<TextInputEditText>(R.id.teSensorOffset).error = getString(R.string.err_field_invalid)
            } else {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = null
                view.findViewById<TextInputEditText>(R.id.teMaxWidth).error = null
                view.findViewById<TextInputEditText>(R.id.teMaxLength).error = null
                view.findViewById<TextInputEditText>(R.id.teSpeed).error = null
                view.findViewById<TextInputEditText>(R.id.teDensity).error = null
                view.findViewById<AutoCompleteTextView>(R.id.teSensor).error = null
                view.findViewById<TextInputEditText>(R.id.teSensorHeight).error = null
                view.findViewById<TextInputEditText>(R.id.teSensorOffset).error = null

                val mappedRotation = Rotation.values().find { it.toString() == rotation }!!.degrees
                val mappedSensor = Sensor.values().find { it.toString() == sensor }!!.sensor

                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_rotation", mappedRotation.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_dpi", dpi)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_maxwidth", maxWidth)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_maxlength", maxLength)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_speed", speed)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_density", density)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_sensor", mappedSensor.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_sensor_height", sensorHeight)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_sensor_offset", sensorOffset)

                (activity as PrinterSetupActivity).startFinalPage()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolChoice()
    }
}