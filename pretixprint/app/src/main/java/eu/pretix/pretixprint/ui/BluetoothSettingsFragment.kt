package eu.pretix.pretixprint.ui

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.ui.BluetoothDeviceManager.BluetoothDevicePickResultHandler
import eu.pretix.pretixprint.ui.BluetoothDevicePicker.Companion.ACTION_DEVICE_SELECTED
import eu.pretix.pretixprint.ui.BluetoothDevicePicker.Companion.ACTION_LAUNCH
import eu.pretix.pretixprint.ui.BluetoothDevicePicker.Companion.EXTRA_FILTER_TYPE
import eu.pretix.pretixprint.ui.BluetoothDevicePicker.Companion.EXTRA_NEED_AUTH
import eu.pretix.pretixprint.ui.BluetoothDevicePicker.Companion.FILTER_TYPE_ALL
import kotlinx.android.synthetic.main.fragment_bluetooth_settings.*
import org.jetbrains.anko.support.v4.defaultSharedPreferences


class BluetoothSettingsFragment : SetupFragment() {
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_bluetooth_settings, container, false)

        val deviceManager = BluetoothDeviceManager(this.context!!)

        view.findViewById<Button>(R.id.btnAuto).setOnClickListener {
            deviceManager.pickDevice(object : BluetoothDevicePickResultHandler {
                override fun onDevicePicked(device: BluetoothDevice?) {
                    teMAC.setText(device?.address, TextView.BufferType.EDITABLE)
                }
            })
        }

        val currentIP = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_ip"
        ) as String?) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_ip", "")
        view.findViewById<TextInputEditText>(R.id.teMAC).setText(currentIP)

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val mac = view.findViewById<TextInputEditText>(R.id.teMAC).text.toString()
            if (TextUtils.isEmpty(mac)) {
                view.findViewById<TextInputEditText>(R.id.teMAC).error = getString(R.string.err_field_required)
            } else {
                view.findViewById<TextInputEditText>(R.id.teMAC).error = null
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_ip",
                        mac)
                (activity as PrinterSetupActivity).startProtocolChoice()
            }
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startConnectionChoice()
    }
}

class BluetoothDeviceManager(context: Context) : BluetoothDevicePicker {
    protected var context: Context = context

    fun pickDevice(handler: BluetoothDevicePickResultHandler) {
        context.registerReceiver(BluetoothDeviceManagerReceiver(handler), IntentFilter(ACTION_DEVICE_SELECTED))
        context.startActivity(Intent(ACTION_LAUNCH)
                .putExtra(EXTRA_NEED_AUTH, false)
                .putExtra(EXTRA_FILTER_TYPE, FILTER_TYPE_ALL)
                .setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
    }

    interface BluetoothDevicePickResultHandler {
        fun onDevicePicked(device: BluetoothDevice?)
    }

    private class BluetoothDeviceManagerReceiver(private val handler: BluetoothDevicePickResultHandler) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            context.unregisterReceiver(this)
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            handler.onDevicePicked(device)
        }

    }
}

interface BluetoothDevicePicker {
    companion object {
        const val EXTRA_NEED_AUTH = "android.bluetooth.devicepicker.extra.NEED_AUTH"
        const val EXTRA_FILTER_TYPE = "android.bluetooth.devicepicker.extra.FILTER_TYPE"
        const val EXTRA_LAUNCH_PACKAGE = "android.bluetooth.devicepicker.extra.LAUNCH_PACKAGE"
        const val EXTRA_LAUNCH_CLASS = "android.bluetooth.devicepicker.extra.DEVICE_PICKER_LAUNCH_CLASS"
        /**
         * Broadcast when one BT device is selected from BT device picker screen.
         * Selected [BluetoothDevice] is returned in extra data named
         * [BluetoothDevice.EXTRA_DEVICE].
         */
        const val ACTION_DEVICE_SELECTED = "android.bluetooth.devicepicker.action.DEVICE_SELECTED"
        /**
         * Broadcast when someone want to select one BT device from devices list.
         * This intent contains below extra data:
         * - [.EXTRA_NEED_AUTH] (boolean): if need authentication
         * - [.EXTRA_FILTER_TYPE] (int): what kinds of device should be
         * listed
         * - [.EXTRA_LAUNCH_PACKAGE] (string): where(which package) this
         * intent come from
         * - [.EXTRA_LAUNCH_CLASS] (string): where(which class) this intent
         * come from
         */
        const val ACTION_LAUNCH = "android.bluetooth.devicepicker.action.LAUNCH"
        /**
         * Ask device picker to show all kinds of BT devices
         */
        const val FILTER_TYPE_ALL = 0
        /**
         * Ask device picker to show BT devices that support AUDIO profiles
         */
        const val FILTER_TYPE_AUDIO = 1
        /**
         * Ask device picker to show BT devices that support Object Transfer
         */
        const val FILTER_TYPE_TRANSFER = 2
        /**
         * Ask device picker to show BT devices that support
         * Personal Area Networking User (PANU) profile
         */
        const val FILTER_TYPE_PANU = 3
        /**
         * Ask device picker to show BT devices that support Network Access Point (NAP) profile
         */
        const val FILTER_TYPE_NAP = 4
    }
}
