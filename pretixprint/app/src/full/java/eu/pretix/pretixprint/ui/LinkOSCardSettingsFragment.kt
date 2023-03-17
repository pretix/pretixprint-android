package eu.pretix.pretixprint.ui

import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.preference.PreferenceManager
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.zebra.sdk.common.card.enumerations.CardDestination
import com.zebra.sdk.common.card.enumerations.CardSource
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.Rotation
import eu.pretix.pretixprint.byteprotocols.LinkOSCard


class LinkOSCardSettingsFragment : SetupFragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val view = inflater.inflate(R.layout.fragment_linkoscard_settings, container, false)
        val proto = LinkOSCard()

        val currentDoubleSided = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_doublesided"
        )?.toBoolean() ) ?: prefs.getString("hardware_${useCase}printer_doublesided", "false")!!.toBoolean()
        view.findViewById<SwitchMaterial>(R.id.swDoubleSided).isChecked = currentDoubleSided

        val currentCardSource = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_cardsource"
        ) as String?) ?: prefs.getString("hardware_${useCase}printer_cardsource", "AutoDetect")
        view.findViewById<TextInputLayout>(R.id.tilCardSource).editText?.setText(currentCardSource)

        val currentCardDestination = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_carddestination"
        ) as String?) ?: prefs.getString("hardware_${useCase}printer_carddestination", "Eject")
        view.findViewById<TextInputLayout>(R.id.tilCardDestination).editText?.setText(currentCardDestination)

        val currentDPI = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_dpi"
        ) as String?) ?: prefs.getString("hardware_${useCase}printer_dpi", proto.defaultDPI.toString())
        view.findViewById<TextInputEditText>(R.id.teDPI).setText(currentDPI)

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

        val cardSourcesAdapter = ArrayAdapter(requireContext(), R.layout.list_item, CardSource.values())
        (view.findViewById<TextInputLayout>(R.id.tilCardSource).editText as? AutoCompleteTextView)?.setAdapter(cardSourcesAdapter)

        val cardDestinationAdapter = ArrayAdapter(requireContext(), R.layout.list_item, CardDestination.values())
        (view.findViewById<TextInputLayout>(R.id.tilCardDestination).editText as? AutoCompleteTextView)?.setAdapter(cardDestinationAdapter)

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val dpi = view.findViewById<TextInputEditText>(R.id.teDPI).text.toString()
            val rotation = view.findViewById<TextInputLayout>(R.id.tilRotation).editText?.text.toString()
            if (TextUtils.isEmpty(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_required)
            } else if (!TextUtils.isDigitsOnly(dpi)) {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = getString(R.string.err_field_invalid)
            } else {
                view.findViewById<TextInputEditText>(R.id.teDPI).error = null

                val doubleSided = view.findViewById<SwitchMaterial>(R.id.swDoubleSided).isChecked
                val cardSource = view.findViewById<TextInputLayout>(R.id.tilCardSource).editText?.text.toString()
                val cardDestination = view.findViewById<TextInputLayout>(R.id.tilCardDestination).editText?.text.toString()
                val mappedRotation = Rotation.values().find { it.toString() == rotation }!!.degrees

                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_rotation", mappedRotation.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_dpi", dpi)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_doublesided", doubleSided.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_cardsource", cardSource)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_carddestination", cardDestination)
                (activity as PrinterSetupActivity).startFinalPage()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolChoice()
    }
}
