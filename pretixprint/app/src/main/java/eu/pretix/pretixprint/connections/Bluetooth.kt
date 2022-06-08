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
import io.sentry.Sentry
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeoutException

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
        val address = conf.get("hardware_${type}printer_ip") ?: ""
        val dpi = Integer.valueOf(conf.get("hardware_${type}printer_dpi") ?: proto.defaultDPI.toString()).toFloat()

        Sentry.configureScope { scope ->
            scope.setTag("printer.mode", mode)
            scope.setTag("printer.type", type)
            scope.setContexts("printer.ip", address)
            scope.setContexts("printer.dpi", dpi)
        }

        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)

        try {
            val futures = renderPages(proto, tmpfile, dpi, numPages, conf, type)

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
                    } catch (e: IOException) {
                        e.printStackTrace()
                        throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw PrintException(context.applicationContext.getString(R.string.err_generic, e.message));
                    }

                    val ostream = fallbackSocket.outputStream
                    val istream = fallbackSocket.inputStream

                    try {
                        proto.send(futures, istream, ostream, conf, type)
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
        } catch (e: TimeoutException) {
            e.printStackTrace()
            throw PrintException("Rendering timeout, thread may have crashed")
        } catch (e: IOException) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_generic, e.message))
        }
    }
}