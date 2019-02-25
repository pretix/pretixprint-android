package eu.pretix.pretixprint.ui

import android.app.Activity.RESULT_OK
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.bt.*
import kotlinx.android.synthetic.main.activity_find_bluetooth.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import org.jetbrains.anko.support.v4.progressDialog

class BluetoothServiceAdapter(val items: Array<BluetoothDevice>, val fragment: FindBluetoothPrinterFragment) : RecyclerView.Adapter<ViewHolder>() {
    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(fragment.context).inflate(R.layout.item_networkservice, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvName.text = "%s (%s)".format(items[position].name, items[position].address)
        holder.itemView.setOnClickListener {
            fragment.onDevicePicked(items[position])
        }
    }
}

class FindBluetoothPrinterFragment(type: String) : PrinterFragment(type, "bluetooth_printer") {
    var lastEvent: BtEvent? = null
    var currentState: State = State.Initial
        private set

    companion object {
        private const val REQUEST_CODE_ENABLE_BLUETOOTH = 101
    }

    private var pgTest: ProgressDialog? = null

    override fun validate(): Boolean {
        if (TextUtils.isEmpty(editText_mac.text)) {
            editText_mac.error = getString(R.string.err_field_required)
            return false
        }
        if (TextUtils.isEmpty(editText_printer.text)) {
            editText_printer.error = getString(R.string.err_field_required)
            return false
        }
        return true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater!!.inflate(R.layout.activity_find_bluetooth, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        editText_mac.setText(defaultSharedPreferences.getString("hardware_${getType()}printer_ip", ""))
        editText_printer.setText(defaultSharedPreferences.getString("hardware_${getType()}printer_printername", ""))

        button2.setOnClickListener {
            if (validate()) {
                testPrinter()
            }
        }

        when(currentState) {
            State.Paired -> activity!!.applicationContext.stopService(Intent(activity!!.applicationContext, BtService::class.java))
            else -> queuePairedDevices()
        }

        EventBus.getDefault().register(this)
        goToState(State.Idle)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_OK) {
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
        when(state) {
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

    fun ensureBluetoothEnabled() : Boolean {
        if (!(BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false)) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_CODE_ENABLE_BLUETOOTH);
            return false
        }
        return true
    }

    fun queuePairedDevices() {
        if (ensureBluetoothEnabled()) {
            val devices = BluetoothAdapter.getDefaultAdapter().bondedDevices.toTypedArray()
            recyclerView.layoutManager = LinearLayoutManager(this.context)
            recyclerView.adapter = BluetoothServiceAdapter(devices, this)
        }
    }

    fun onDevicePicked(device: BluetoothDevice) {
        if (ensureBluetoothEnabled()) {
            BtService.connect(activity!!.applicationContext, device.address)
            editText_mac.setText(device.address, TextView.BufferType.EDITABLE)
            editText_printer.setText(device.name, TextView.BufferType.EDITABLE)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBtEvent(event: BtEvent) {
        if (currentState == State.Paired){

        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConnectedEvent(event: BtService.ConnectedEvent) {
        goToState(if (event.isConnected) State.Paired else State.Idle)
    }


    fun testPrinter() {
        pgTest = progressDialog(R.string.testing) {
            setCancelable(false)
            isIndeterminate = true
        }
        doAsync {
        }
    }

    override fun savePrefs() {
        defaultSharedPreferences.edit()
                .putString("hardware_${getType()}printer_ip", editText_mac.text.toString())
                .putString("hardware_${getType()}printer_printername", editText_printer.text.toString())
                .putString("hardware_${getType()}printer_connection", getConnection())
                .apply()
    }
}