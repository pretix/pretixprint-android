package eu.pretix.pretixprint.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R
import splitties.toast.toast

class USBSettingsFragment : SetupFragment() {
    private val ACTION_USB_PERMISSION = "eu.pretix.pretixprint.settings.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device!!.serialNumber != null && device!!.serialNumber != "null") {
                            view?.findViewById<TextInputEditText>(R.id.teSerial)?.setText(device!!.serialNumber)
                        } else {
                            view?.findViewById<TextInputEditText>(R.id.teSerial)?.setText("${Integer.toHexString(device!!.vendorId)}:${Integer.toHexString(device!!.productId)}")
                        }
                    } else {
                        toast(R.string.err_usb_permission_denied)
                    }
                }
            }
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val view = inflater.inflate(R.layout.fragment_usb_settings, container, false)

        val currentSerial = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_ip"
        ) as String?) ?: prefs.getString("hardware_${useCase}printer_ip", "")
        view.findViewById<TextInputEditText>(R.id.teSerial).setText(currentSerial)

        val currentCompat = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_usbcompat"
        )?.toBoolean() ) ?: prefs.getString("hardware_${useCase}printer_usbcompat", "false")!!.toBoolean()
        view.findViewById<SwitchMaterial>(R.id.swCompat).isChecked = currentCompat

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val serial = view.findViewById<TextInputEditText>(R.id.teSerial).text.toString()
            val compat = view.findViewById<SwitchMaterial>(R.id.swCompat).isChecked
            if (TextUtils.isEmpty(serial)) {
                view.findViewById<TextInputEditText>(R.id.teSerial).error = getString(R.string.err_field_required)
            } else {
                view.findViewById<TextInputEditText>(R.id.teSerial).error = null
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_usbcompat", compat.toString())
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_ip",
                        serial)
                (activity as PrinterSetupActivity).startProtocolChoice()
            }
        }
        view.findViewById<Button>(R.id.btnAuto).setOnClickListener {
            val manager = requireActivity().getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = manager.deviceList.values.toList()
            val deviceNames = deviceList.map { "${it.manufacturerName} ${it.productName} (${String.format("%04x", it.vendorId)}:${String.format("%04x", it.productId)})" }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.headline_found_usb_devices)
                .setItems(deviceNames.toTypedArray()) { _, i ->
                    val intent = Intent(ACTION_USB_PERMISSION)
                    intent.setPackage(requireContext().packageName)
                    val permissionIntent = PendingIntentCompat.getBroadcast(requireContext(), 0, intent, 0, true)
                    manager.requestPermission(deviceList[i], permissionIntent)
                }
                .create()
                .show()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(requireContext(), usbReceiver, filter, RECEIVER_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(usbReceiver)
    }

    override fun back() {
        (activity as PrinterSetupActivity).startConnectionChoice()
    }
}
