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

class NetworkSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val view = inflater.inflate(R.layout.fragment_network_settings, container, false)

        val currentIP = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_ip"
        ) as String?) ?: prefs.getString("hardware_${useCase}printer_ip", "")
        view.findViewById<TextInputEditText>(R.id.teIP).setText(currentIP)

        val currentPort = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_port"
        ) as String?) ?: prefs.getString("hardware_${useCase}printer_port", "9100")
        view.findViewById<TextInputEditText>(R.id.tePort).setText(currentPort)

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val ip = view.findViewById<TextInputEditText>(R.id.teIP).text.toString().trim()
            val port = view.findViewById<TextInputEditText>(R.id.tePort).text.toString().trim()
            if (TextUtils.isEmpty(ip)) {
                view.findViewById<TextInputEditText>(R.id.teIP).error = getString(R.string.err_field_required)
            } else if (TextUtils.isEmpty(port)) {
                view.findViewById<TextInputEditText>(R.id.tePort).error = getString(R.string.err_field_required)
                view.findViewById<TextInputEditText>(R.id.teIP).error = null
            } else if (!TextUtils.isDigitsOnly(port)) {
                view.findViewById<TextInputEditText>(R.id.tePort).error = getString(R.string.err_field_invalid)
                view.findViewById<TextInputEditText>(R.id.teIP).error = null
            } else {
                view.findViewById<TextInputEditText>(R.id.teIP).error = null
                view.findViewById<TextInputEditText>(R.id.tePort).error = null
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_ip",
                        ip)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_port",
                        port)
                (activity as PrinterSetupActivity).startProtocolChoice()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startConnectionChoice()
    }
}
