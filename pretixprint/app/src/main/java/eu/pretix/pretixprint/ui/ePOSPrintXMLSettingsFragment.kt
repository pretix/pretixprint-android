package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.preference.PreferenceManager
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R

class ePOSPrintXMLSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val view = inflater.inflate(R.layout.fragment_eposprintxml_settings, container, false)
        val currentWaitAfterPage = (activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_waitafterpage"
        ) ?: prefs.getString("hardware_${useCase}printer_waitafterpage", "100")
        view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).setText(currentWaitAfterPage)
        val currentDeviceId = (activity as PrinterSetupActivity).settingsStagingArea.get(
            "hardware_${useCase}printer_deviceId"
        ) ?: prefs.getString("hardware_${useCase}printer_deviceId", "local_printer")
        view.findViewById<TextInputEditText>(R.id.teDeviceId).setText(currentDeviceId)

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            var error = false
            val wap = view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).text.toString()
            if (TextUtils.isEmpty(wap)) {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = getString(R.string.err_field_required)
                error = true
            } else if (!TextUtils.isDigitsOnly(wap)) {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = getString(R.string.err_field_invalid)
                error = true
            } else {
                view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).error = null
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_waitafterpage",
                        wap)
            }

            val devid = view.findViewById<TextInputEditText>(R.id.teDeviceId).text.toString()
            if (TextUtils.isEmpty(devid)) {
                view.findViewById<TextInputEditText>(R.id.teDeviceId).error = getString(R.string.err_field_required)
                error = true
            } else {
                view.findViewById<TextInputEditText>(R.id.teDeviceId).error = null
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_deviceId",
                    devid)
            }

            if (!error) {
                (activity as PrinterSetupActivity).startFinalPage()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolChoice()
    }
}
