package eu.pretix.pretixprint.connections

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.CustomByteProtocol
import eu.pretix.pretixprint.byteprotocols.StreamByteProtocol
import eu.pretix.pretixprint.byteprotocols.getProtoClass
import eu.pretix.pretixprint.renderers.renderPages
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File
import java.io.IOException

class BluetoothConnection : ConnectionType {
    override val identifier = "bluetooth_printer"
    override val nameResource = R.string.connection_type_bluetooth
    override val inputType = ConnectionType.Input.PLAIN_BYTES

    var context: Context? = null

    override fun allowedForUsecase(type: String): Boolean {
        return true
    }

    override fun print(tmpfile: File, numPages: Int, context: Context, type: String, settings: Map<String, String>?) {
        this.context = context

        val conf = settings?.toMutableMap() ?: mutableMapOf()
        for (entry in context.defaultSharedPreferences.all.iterator()) {
            if (!conf.containsKey(entry.key)) {
                conf[entry.key] = entry.value.toString()
            }
        }

        val mode = conf.get("hardware_${type}printer_mode") ?: "FGL"
        val proto = getProtoClass(mode)
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(conf.get("hardware_${type}printer_ip") ?: "")

        try {
            val futures = renderPages(proto, tmpfile, Integer.valueOf(conf.get("hardware_${type}printer_dpi") ?: proto.defaultDPI.toString()).toFloat(), numPages, conf, type)

            when (proto) {
                is StreamByteProtocol<*> -> {
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
                        proto.send(futures, istream, ostream)
                    } finally {
                        istream.close()
                        ostream.close()
                        socket.close()
                    }
                }

                is CustomByteProtocol<*> -> {
                    proto.sendBluetooth(device.address, futures, conf, type, context)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_files_io, e.message))
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_files_generic, e.message))
        }
    }
}