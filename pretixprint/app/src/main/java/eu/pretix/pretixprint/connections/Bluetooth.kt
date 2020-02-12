package eu.pretix.pretixprint.connections

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.ESCPOS
import eu.pretix.pretixprint.byteprotocols.FGL
import eu.pretix.pretixprint.byteprotocols.SLCS
import eu.pretix.pretixprint.renderers.renderPages
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File
import java.io.IOException

class BluetoothConnection : ConnectionType {
    override val identifier = "bluetooth_printer"
    override val nameResource = R.string.connection_type_bluetooth
    override val inputType = ConnectionType.Input.PLAIN_BYTES

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
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(getSetting("hardware_${type}printer_ip", ""))

        // Yes, unfortunately this is necessary when using Services/IntentServices to connect to BT devices.
        val socket = device.createInsecureRfcommSocketToServiceRecord(device.uuids.first().uuid)
        val clazz = socket.remoteDevice.javaClass
        val paramTypes = arrayOf<Class<*>>(Integer.TYPE)
        val m = clazz.getMethod("createRfcommSocket", *paramTypes)
        val fallbackSocket = m.invoke(socket.remoteDevice, Integer.valueOf(1)) as BluetoothSocket
        try {
            fallbackSocket.connect()
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_files_generic, e.message));
        }

        val ostream = fallbackSocket.outputStream
        val istream = fallbackSocket.inputStream
        try {
            escpos = tmpfile.readBytes()

            if (mode == "FGL") {
                val proto = FGL()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(getSetting("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                proto.send(futures, istream, ostream)
            } else if (mode == "SLCS") {
                val proto = SLCS()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(getSetting("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                proto.send(futures, istream, ostream)
            } else if (mode == "ESC/POS") {
                val proto = ESCPOS()
                val futures = renderPages(proto, tmpfile, Integer.valueOf(getSetting("hardware_${type}printer_dpi", "200")).toFloat(), numPages)
                proto.send(futures, istream, ostream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_files_io, e.message));
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_files_generic, e.message));
        } finally {
            istream.close()
            ostream.close()
            socket.close()
        }
    }
}