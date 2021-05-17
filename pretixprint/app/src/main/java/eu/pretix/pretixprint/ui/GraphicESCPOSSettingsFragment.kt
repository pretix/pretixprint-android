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

class GraphicESCPOSSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_graphicescpos_settings, container, false)
        val proto = FGL()
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

        val currentRotate90 = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_rotate90"
        )?.toBoolean() ) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_rotate90", "false")!!.toBoolean()
        view.findViewById<SwitchMaterial>(R.id.swRotate90).isChecked = currentRotate90

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val dpi = view.findViewById<TextInputEditText>(R.id.teDPI).text.toString()
            val wap = view.findViewById<TextInputEditText>(R.id.teWaitAfterPage).text.toString()
            val mw = view.findViewById<TextInputEditText>(R.id.teMaxWidth).text.toString()
            val rotate90 = view.findViewById<SwitchMaterial>(R.id.swRotate90).isChecked
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
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_dpi",
                        dpi)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_rotate90", rotate90.toString())
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
