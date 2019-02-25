package eu.pretix.pretixprint.print

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.ui.SettingsActivity
import org.jetbrains.anko.ctx
import org.jetbrains.anko.defaultSharedPreferences
import org.json.JSONObject
import java.io.IOException
import android.os.*
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
    lateinit var escpos : List<Byte>

    override fun print(tmpfile: File) {
        val prefs = context.defaultSharedPreferences

        throw PrintException(context.applicationContext.getString(R.string.err_cups_io, "Test"))
        /*
        try {
            val dataInputStream = context.contentResolver.openInputStream(intent.clipData.getItemAt(0).uri)
            val jsonData = JSONObject(dataInputStream.bufferedReader().use { it.readText() })

            val layout = jsonData.getJSONArray("__layout")
            val positions = jsonData.getJSONArray("positions")

            escpos = ESCPOSRenderer(layout, positions, ctx).render()

            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(prefs.getString("hardware_receiptprinter_ip", null))

            EventBus.getDefault().register(this)
            goToState(State.Idle)

            BtService.connect(this.baseContext, device.address)
        } catch (e: IOException) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_files_io, e.message));
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_files_generic, e.message));
        }
        */
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
                //BtService.send(this.baseContext, escpos)
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