package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R
import org.jetbrains.anko.support.v4.defaultSharedPreferences

class NetworkSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_network_settings, container, false)

        val currentIP = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_ip"
        ) as String?) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_ip", "")
        view.findViewById<TextInputEditText>(R.id.teIP).setText(currentIP)

        val currentPort = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_port"
        ) as String?) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_port", "910")
        view.findViewById<TextInputEditText>(R.id.tePort).setText(currentPort)

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            (activity as PrinterSetupActivity).startConnectionChoice()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val ip = view.findViewById<TextInputEditText>(R.id.teIP).text.toString()
            val port = view.findViewById<TextInputEditText>(R.id.tePort).text.toString()
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
}
