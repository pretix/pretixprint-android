package eu.pretix.pretixprint.print

import android.bluetooth.BluetoothAdapter
import android.content.Context
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import org.jetbrains.anko.defaultSharedPreferences
import java.io.IOException
import android.util.Log
import eu.pretix.pretixprint.bt.BtEvent
import eu.pretix.pretixprint.bt.BtService
import eu.pretix.pretixprint.bt.State
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

class BluetoothPrintService(context: Context, type: String = "receipt") : PrintServiceTransport(context, type) {
    var lastEvent : BtEvent? = null
    var currentState : State = State.Initial
        private set
    lateinit var escpos : ByteArray

    override fun print(tmpfile: File) {
        val prefs = context.defaultSharedPreferences

        try {
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(prefs.getString("hardware_${type}printer_ip", null))
            escpos = tmpfile.readBytes()

            EventBus.getDefault().register(this)
            goToState(State.Idle)

            BtService.connect(context, device.address)
        } catch (e: IOException) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_files_io, e.message));
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_files_generic, e.message));
        }
    }

    fun testPrinter(tmpfile: File, printerMAC: String) {
        val prefs = context.defaultSharedPreferences
        val editor = prefs.edit()
        val oldMAC = prefs.getString("hardware_${type}printer_ip", null)

        editor.putString("hardware_${type}printer_ip", printerMAC)
        editor.apply()

        print(tmpfile)

        editor.putString("hardware_${type}printer_ip", oldMAC)
        editor.apply()
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
                if (escpos.isNotEmpty()) {
                    BtService.send(context, escpos)
                    escpos = ByteArray(0)
                }
            }
            State.Setup -> {
                Log.d("ESCPOSPRINT", "Setup")
            }
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
}