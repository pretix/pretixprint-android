package eu.pretix.pretixprint.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.ui.BluetoothDeviceManager.BluetoothDevicePickResultHandler
import eu.pretix.pretixprint.ui.BluetoothDevicePicker.Companion.ACTION_DEVICE_SELECTED
import eu.pretix.pretixprint.ui.BluetoothDevicePicker.Companion.ACTION_LAUNCH
import eu.pretix.pretixprint.ui.BluetoothDevicePicker.Companion.EXTRA_FILTER_TYPE
import eu.pretix.pretixprint.ui.BluetoothDevicePicker.Companion.EXTRA_NEED_AUTH
import eu.pretix.pretixprint.ui.BluetoothDevicePicker.Companion.FILTER_TYPE_ALL


class BluetoothSettingsFragment : SetupFragment() {
    companion object {
        /**
         * https://stackoverflow.com/q/71552331
         * some manufacturers butchered the upgrade from A11 to A12
         * where the pure BLUETOOTH permission should be no longer needed
         * well, not for those:
         */
        val BROKEN_PERMISSION_MANUFACTURERS = listOf(
            "xiaomi", "oppo"
        )
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val view = inflater.inflate(R.layout.fragment_bluetooth_settings, container, false)

        val deviceManager = BluetoothDeviceManager(this.requireContext())

        val teMAC = view.findViewById<TextInputEditText>(R.id.teMAC)

        view.findViewById<Button>(R.id.btnAuto).setOnClickListener {
            deviceManager.pickDevice(object : BluetoothDevicePickResultHandler {
                override fun onDevicePicked(device: BluetoothDevice?) {
                    teMAC.setText(device?.address, TextView.BufferType.EDITABLE)

                    // fill cache with uuids for this device
                    // helps with never/freshly connected BLE devices on Android 12+
                    try {
                        device?.fetchUuidsWithSdp()
                    } catch (_: Exception) {
                    }
                }
            })
        }

        val currentIP = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_ip"
        ) as String?) ?: prefs.getString("hardware_${useCase}printer_ip", "")
        teMAC.setText(currentIP)

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val mac = teMAC.text.toString()
            if (TextUtils.isEmpty(mac)) {
                teMAC.error = getString(R.string.err_field_required)
            } else {
                teMAC.error = null
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_ip",
                        mac)
                (activity as PrinterSetupActivity).startProtocolChoice()
            }
        }

        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { isGranted: Map<String, Boolean> ->
                if (isGranted.values.contains(false)) {
                    back()
                }
            }

        val perms = mutableListOf<String>()
        var perm : String?
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R || Build.MANUFACTURER.lowercase() in BROKEN_PERMISSION_MANUFACTURERS) {
            perm = Manifest.permission.BLUETOOTH
            if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                perms.add(perm)
            }
        }
        perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_CONNECT
            } else {
                Manifest.permission.BLUETOOTH_ADMIN
            }
        if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
            perms.add(perm)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perm = Manifest.permission.BLUETOOTH_SCAN
            if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                perms.add(perm)
            }
        }
        if (perms.isNotEmpty()) {
            requestPermissionLauncher.launch(perms.toTypedArray())
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
