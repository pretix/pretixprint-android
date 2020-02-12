package eu.pretix.pretixprint.ui

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.bt.BtEvent
import eu.pretix.pretixprint.bt.BtService
import eu.pretix.pretixprint.bt.State
import kotlinx.android.synthetic.main.activity_find_bluetooth.*
import kotlinx.android.synthetic.main.fragment_bluetooth_settings.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.support.v4.selector

class BluetoothSettingsFragment : SetupFragment() {
    companion object {
        private const val REQUEST_CODE_ENABLE_BLUETOOTH = 101
    }

    var lastEvent: BtEvent? = null
    var currentState: State = State.Initial
        private set
    var devices: Array<BluetoothDevice> = emptyArray()


    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_bluetooth_settings, container, false)

        EventBus.getDefault().register(this)
        goToState(State.Idle)

        view.findViewById<Button>(R.id.btnAuto).setOnClickListener {
            val devices = this.devices
            selector(getString(R.string.headline_found_bluetooth_printers), devices.map {
                "%s (%s)".format(it.name, it.address)
            }) { dialogInterface, i ->
                onDevicePicked(devices[i])
            }
        }

        val currentIP = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_ip"
        ) as String?) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_ip", "")
        view.findViewById<TextInputEditText>(R.id.teMAC).setText(currentIP)

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            (activity as PrinterSetupActivity).startConnectionChoice()
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

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        when (currentState) {
            State.Paired -> activity!!.applicationContext.stopService(Intent(activity!!.applicationContext, BtService::class.java))
            else -> queuePairedDevices()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                queuePairedDevices()
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (lastEvent != null) {
            onBtEvent(lastEvent!!)
            lastEvent = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        activity!!.applicationContext.stopService(Intent(activity!!.applicationContext, BtService::class.java))
    }

    private fun goToState(state: State) {
        if (currentState != state) {
            currentState = state
            applyState(currentState)
        }
    }

    private fun applyState(state: State) {
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (state) {
            State.Idle -> {
                Log.d("ESCPOSPRINT", "Idle")
            }
            State.Paired -> {
                Log.d("ESCPOSPRINT", "Paired")
            }
            State.Setup -> {
                Log.d("ESCPOSPRINT", "Setup")
            }
        }
    }

    private fun ensureBluetoothEnabled(): Boolean {
        if (BluetoothAdapter.getDefaultAdapter()?.isEnabled != true) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_CODE_ENABLE_BLUETOOTH);
            return false
        }
        return true
    }

    private fun queuePairedDevices() {
        if (ensureBluetoothEnabled()) {
            devices = BluetoothAdapter.getDefaultAdapter().bondedDevices.toTypedArray()
        }
        view?.findViewById<Button>(R.id.btnAuto)?.isEnabled = devices.isNotEmpty()
    }

    private fun onDevicePicked(device: BluetoothDevice) {
        if (ensureBluetoothEnabled()) {
            BtService.connect(activity!!.applicationContext, device.address)
            teMAC.setText(device.address, TextView.BufferType.EDITABLE)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBtEvent(event: BtEvent) {
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConnectedEvent(event: BtService.ConnectedEvent) {
        goToState(if (event.isConnected) State.Paired else State.Idle)
    }
}
