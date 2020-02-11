package eu.pretix.pretixprint.connections

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.bt.BtEvent
import eu.pretix.pretixprint.bt.BtService
import eu.pretix.pretixprint.bt.State
import eu.pretix.pretixprint.byteprotocols.ESCPOS
import eu.pretix.pretixprint.byteprotocols.FGL
import eu.pretix.pretixprint.byteprotocols.SLCS
import eu.pretix.pretixprint.renderers.renderPages
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File
import java.io.IOException


class BluetoothConnection : ConnectionType {
    override val identifier = "bluetooth_printer"
    override val nameResource = R.string.connection_type_bluetooth
    override val inputType = ConnectionType.Input.PLAIN_BYTES

    var currentState: State = State.Initial
    lateinit var escpos: ByteArray
    var context: Context? = null

    override fun allowedForUsecase(type: String): Boolean {
        return true
    }

    override fun print(tmpfile: File, numPages: Int, context: Context, type: String, settings: Map<String, String>?) {
        val conf = settings ?: emptyMap()
        this.context = context
        fun getSetting(key: String, def: String): String {
            return conf!![key] ?: context.defaultSharedPreferences.getString(key, def)!!
        }
        val mode = getSetting("hardware_${type}printer_mode", "FGL")
        try {
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(getSetting("hardware_${type}printer_ip", ""))
            escpos = tmpfile.readBytes()

            if (mode == "FGL") {
                val proto = FGL()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(getSetting("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                // TODO: proto.send(futures, istream, ostream)
            } else if (mode == "SLCS") {
                val proto = SLCS()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(getSetting("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                // TODO: proto.send(futures, istream, ostream)
            } else if (mode == "ESC/POS") {
                val proto = ESCPOS()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(getSetting("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                // TODO: proto.send(futures, istream, ostream)
            }

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
                if (escpos.isNotEmpty()) {
                    BtService.send(context!!, escpos)
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
        if (currentState == State.Paired) {

        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConnectedEvent(event: BtService.ConnectedEvent) {
        goToState(if (event.isConnected) State.Paired else State.Idle)
    }
}