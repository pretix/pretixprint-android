package eu.pretix.pretixprint.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.preference.PreferenceManager
import com.github.razir.progressbutton.attachTextChangeAnimator
import com.github.razir.progressbutton.bindProgressButton
import com.github.razir.progressbutton.hideProgress
import com.github.razir.progressbutton.showProgress
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

    private var bluetoothEnablingLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val view = inflater.inflate(R.layout.fragment_bluetooth_settings, container, false)

        val deviceManager = BluetoothDeviceManager(requireContext())

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
        // gets enabled by onCreateViewWithBtPermission after we got bluetooth permissions
        view.findViewById<Button>(R.id.btnNext).isEnabled = false

        bluetoothEnablingLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            val bluetoothManager = requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            view.findViewById<View>(R.id.warningBluetoothOff).visibility = if (bluetoothAdapter.isEnabled) View.GONE else View.VISIBLE
        }

        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { isGranted: Map<String, Boolean> ->
                if (isGranted.values.contains(false)) {
                    back()
                    return@registerForActivityResult
                }
                onCreateViewWithBtPermission(view)
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
        } else {
            onCreateViewWithBtPermission(view)
        }

        return view
    }

    fun onCreateViewWithBtPermission(view: View) {
        val teMAC = view.findViewById<TextInputEditText>(R.id.teMAC)

        val bluetoothManager = requireActivity().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        view.findViewById<View>(R.id.warningBluetoothOff).visibility = if (bluetoothAdapter.isEnabled) View.GONE else View.VISIBLE
        view.findViewById<Button>(R.id.btnBluetoothActivate).setOnClickListener {
            if (!bluetoothAdapter.isEnabled) {
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                    bluetoothEnablingLauncher?.launch(this)
                }
            }
        }

        val btStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) {
                    return
                }
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                view.findViewById<View>(R.id.warningBluetoothOff).visibility = when (state) {
                    BluetoothAdapter.STATE_ON,
                    BluetoothAdapter.STATE_TURNING_ON -> View.GONE
                    BluetoothAdapter.STATE_OFF,
                    BluetoothAdapter.STATE_TURNING_OFF -> View.VISIBLE
                    else -> View.VISIBLE
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(requireContext(), btStateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        val next = view.findViewById<Button>(R.id.btnNext)
        this.bindProgressButton(next)
        next.attachTextChangeAnimator()
        next.setOnClickListener {
            val mac = teMAC.text.toString()
            if (TextUtils.isEmpty(mac)) {
                teMAC.error = getString(R.string.err_field_required)
            } else {
                teMAC.error = null
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_ip",
                    mac)

                // try to reach device:
                val device = bluetoothAdapter.getRemoteDevice(mac)
                if (device.uuids == null) {
                    device.fetchUuidsWithSdp()
                }
                var instantGoToNextStep = true
                if (device.bondState == BluetoothDevice.BOND_NONE) {
                    val btBondReceiver: BroadcastReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            if (activity == null) {
                                return
                            }
                            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                                return
                            }
                            val dev = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            if (dev?.address != device.address) {
                                // ignore broadcasts if device is not the selected
                                return
                            }
                            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                            // always forward to next step, ignore progress updates
                            when (state) {
                                BluetoothDevice.ERROR,
                                BluetoothDevice.BOND_NONE,
                                BluetoothDevice.BOND_BONDED -> {
                                    next.hideProgress(R.string.btn_next)
                                    (activity as PrinterSetupActivity).startProtocolChoice()
                                }
                                BluetoothDevice.BOND_BONDING -> {
                                    // ignore, we hopefully get another update
                                }
                            }
                        }
                    }
                    ContextCompat.registerReceiver(requireContext(), btBondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
                    val launched = device.createBond()
                    if (launched) {
                        instantGoToNextStep = false
                        next.showProgress {
                            buttonTextRes = R.string.pairing
                            progressColorRes = R.color.white
                        }
                    }
                }
                if (instantGoToNextStep) {
                    (activity as PrinterSetupActivity).startProtocolChoice()
                }
            }
        }
        next.isEnabled = true
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
            val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
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
