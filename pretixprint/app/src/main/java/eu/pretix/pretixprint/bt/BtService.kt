package eu.pretix.pretixprint.bt

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.fasterxml.jackson.module.kotlin.*
import org.greenrobot.eventbus.EventBus
import org.jetbrains.anko.startService
import java.io.IOException
import java.util.*

class BtService : Service() {

    data class ConnectedEvent(val isConnected: Boolean)

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_COMMAND = "command"

        fun connect(caller: Context, deviceAddress: String) {
            caller.startService<BtService>(EXTRA_DEVICE_ADDRESS to deviceAddress)
        }

        fun send(caller: Context, command: List<Byte>) {
            caller.startService<BtService>(EXTRA_COMMAND to command.toByteArray())
        }
    }

    private val bluetoothAdapter by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var bluetoothSocket: BluetoothSocket? = null

    private val btEventThread = object: Thread() {
        override fun run() {
            if (bluetoothSocket != null) {
                val mapper = jacksonObjectMapper()
                try {
                    var message: String
                    bluetoothSocket!!.inputStream.bufferedReader().use { reader ->
                        while (!isInterrupted) {
                            message = reader.readLine()
                            Log.d("BtService", "Received: " + message)
                            //TODO: Maybe postSticky
                            EventBus.getDefault().post(mapper.readValue<BtEvent>(message))
                        }
                    }
                } catch(e: IOException) {
                    e.printStackTrace()
                    EventBus.getDefault().post(ConnectedEvent(false))
                    stopSelf()
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when {
                intent.hasExtra(EXTRA_COMMAND) -> send(intent.getByteArrayExtra(EXTRA_COMMAND))
                intent.hasExtra(EXTRA_DEVICE_ADDRESS) -> connect(intent.getStringExtra(EXTRA_DEVICE_ADDRESS))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        btEventThread.interrupt()
        bluetoothSocket?.close()
    }

    fun connect(deviceAddress: String) {
        Log.d("ESCPOSTEST", "connect()")
        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
        if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(sppUuid)
            try {
                if (!bluetoothSocket!!.isConnected) {
                    bluetoothSocket!!.connect()
                }
                btEventThread.start()
                EventBus.getDefault().post(ConnectedEvent(true))
            } catch(e: Exception) {
                e.printStackTrace()
                EventBus.getDefault().post(ConnectedEvent(false))
            }
        } else {
            EventBus.getDefault().post(ConnectedEvent(true))
        }
    }

    fun send(command: ByteArray) {
        Log.d("ESCPOSTEST", "send() $command")
        if (bluetoothSocket != null) {
            bluetoothSocket!!.outputStream.write(command)
        }
    }
}