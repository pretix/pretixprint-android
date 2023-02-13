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
import eu.pretix.pretixprint.Rotation
import eu.pretix.pretixprint.byteprotocols.GraphicESCPOS
import org.jetbrains.anko.support.v4.defaultSharedPreferences

class GraphicESCPOSSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_graphicescpos_settings, container, false)
        val proto = GraphicESCPOS()
        val currentDPI = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_dpi"
        ) as String?)
                ?: defaultSharedPreferences.getString("hardware_${useCase}printer_dpi", proto.defaultDPI.toString())
        view.findViewById<TextInputEditText>(R.id.teDPI).setText(currentDPI)

        val currentMaxWidth = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_maxwidth"
        ) as String?)
                ?: defaultSharedPreferences.getString("hardware_${useCase}printer_maxwidth", "72")
        view.findViewById<TextInputEditText>(R.id.teMaxWidth).setText(currentMaxWidth)

        val currentWaitAfterPage = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_waitafterpage"
        ) as String?)
                ?: defaultSharedPreferences.getString("hardware_${useCase}printer_waitafterpage", "2000")
        view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).setText(currentWaitAfterPage)

        val rotationAdapter = ArrayAdapter(requireContext(), R.layout.list_item, Rotation.values().map {
            it.toString()
        })
        (view.findViewById<TextInputLayout>(R.id.tilRotation).editText as? AutoCompleteTextView)?.setAdapter(rotationAdapter)
        val chosenRotation = ((activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_rotation"
        )) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_rotation", "0")
        if (chosenRotation?.isNotEmpty() == true) {
            val chosenLabel = Rotation.values().find { it.degrees == Integer.valueOf(chosenRotation) }!!.toString()
            (view.findViewById<TextInputLayout>(R.id.tilRotation).editText as? AutoCompleteTextView)?.setText(chosenLabel, false)
        }

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val dpi = view.findViewById<TextInputEditText>(R.id.teDPI).text.toString()
            val wap = view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).text.toString()
            val mw = view.findViewById<TextInputEditText>(R.id.teMaxWidth).text.toString()
            val rotation = view.findViewById<TextInputLayout>(R.id.tilRotation).editText?.text.toString()
            if (TextUtils.isEmpty(mw)) {
                view.findViewById<TextInputEditText>(R.id.teMaxWidth).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(mw)) {
                view.findViewById<TextInputEditText>(R.id.teMaxWidth).error = getString(R.string.err_field_invalid)
            } else if (TextUtils.isEmpty(wap)) {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(wap)) {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = getString(R.string.err_field_invalid)
            } else if (TextUtils.isEmpty(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_invalid)
            } else {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = null
                val mappedRotation = Rotation.values().find { it.toString() == rotation }!!.degrees

                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_rotation", mappedRotation.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_dpi",
                        dpi)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_maxwidth",
                        mw)
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
