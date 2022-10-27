package eu.pretix.pretixprint.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.support.v4.selector
import org.jetbrains.anko.support.v4.toast

class USBSettingsFragment : SetupFragment() {
    private val ACTION_USB_PERMISSION = "eu.pretix.pretixprint.settings.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
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

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_usb_settings, container, false)

        val currentSerial = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_ip"
        ) as String?) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_ip", "")
        view.findViewById<TextInputEditText>(R.id.teSerial).setText(currentSerial)

        val currentCompat = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_usbcompat"
        )?.toBoolean() ) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_usbcompat", "false")!!.toBoolean()
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
            val manager = activity!!.getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = manager.deviceList.values.toList()
            selector(getString(R.string.headline_found_usb_devices), deviceList.map { "${it.manufacturerName} ${it.productName} (${String.format("%04x", it.vendorId)}:${String.format("%04x", it.productId)})" }) { dialogInterface, i ->
                val permissionIntent = PendingIntent.getBroadcast(activity, 0, Intent(ACTION_USB_PERMISSION),
                        if (Build.VERSION.SDK_INT >= 23) { PendingIntent.FLAG_IMMUTABLE } else { 0 })
                manager.requestPermission(deviceList[i], permissionIntent)
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        context?.registerReceiver(usbReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        context?.unregisterReceiver(usbReceiver)
    }

    override fun back() {
        (activity as PrinterSetupActivity).startConnectionChoice()
    }
}
