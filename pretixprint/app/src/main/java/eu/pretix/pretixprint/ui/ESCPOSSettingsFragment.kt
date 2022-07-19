package eu.pretix.pretixprint.ui

import android.os.Build
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
import eu.pretix.pretixprint.BuildConfig
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.print.ESCPOSRenderer
import org.jetbrains.anko.support.v4.defaultSharedPreferences

class ESCPOSSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_escpos_settings, container, false)
        val currentWaitAfterPage = (activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_waitafterpage"
        ) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_waitafterpage", "100")
        view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).setText(currentWaitAfterPage)

        val dialectAdapter = ArrayAdapter(requireContext(), R.layout.list_item, ESCPOSRenderer.Companion.Dialect.values().map {
            it.description
        })
        (view.findViewById<TextInputLayout>(R.id.tilDialect).editText as? AutoCompleteTextView)?.setAdapter(dialectAdapter)

        val defaultDialect = if ((activity as PrinterSetupActivity).settingsStagingArea.get("hardware_${useCase}printer_connection") == "sunmi")
            "Sunmi"
        else
            "EpsonDefault"
        val chosenDialectId = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_dialect"
        ))
                ?: defaultSharedPreferences.getString("hardware_${useCase}printer_dialect", defaultDialect)
        if (chosenDialectId?.isNotEmpty() == true) {
            val chosenDialect = ESCPOSRenderer.Companion.Dialect.values().find { it.name == chosenDialectId }!!.description
            (view.findViewById<TextInputLayout>(R.id.tilDialect).editText as? AutoCompleteTextView)?.setText(chosenDialect, false)
        }

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val wap = view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).text.toString()
            val dialect = view.findViewById<TextInputLayout>(R.id.tilDialect).editText?.text.toString()
            if (TextUtils.isEmpty(dialect)) {
                view.findViewById<TextInputEditText>(R.id.tilDialect).error = getString(R.string.err_field_required)
            } else if (TextUtils.isEmpty(wap)) {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(wap)) {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = getString(R.string.err_field_invalid)
            } else {
                val mappedDialect = ESCPOSRenderer.Companion.Dialect.values().find { it.description == dialect }!!.name
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = null
                view.findViewById<TextInputLayout>(R.id.tilDialect).error = null
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_waitafterpage",
                        wap)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_dialect",
                        mappedDialect)
                (activity as PrinterSetupActivity).startFinalPage()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolChoice()
    }
}
