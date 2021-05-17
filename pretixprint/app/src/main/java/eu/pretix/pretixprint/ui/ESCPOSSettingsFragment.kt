package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.FGL
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

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val wap = view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).text.toString()
            if (TextUtils.isEmpty(wap)) {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(wap)) {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = getString(R.string.err_field_invalid)
            } else {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = null
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
