package eu.pretix.pretixprint.connections

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.CustomByteProtocol
import eu.pretix.pretixprint.byteprotocols.StreamByteProtocol
import eu.pretix.pretixprint.byteprotocols.SunmiByteProtocol
import eu.pretix.pretixprint.byteprotocols.getProtoClass
import eu.pretix.pretixprint.print.lockManager
import eu.pretix.pretixprint.renderers.renderPages
import io.sentry.Sentry
import java.io.File
import java.io.IOException
import java.util.*
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
        for (entry in PreferenceManager.getDefaultSharedPreferences(context).all.iterator()) {
            if (!conf.containsKey(entry.key)) {
                conf[entry.key] = entry.value.toString()
            }
        }

        val mode = conf.get("hardware_${type}printer_mode") ?: "FGL"
        val proto = getProtoClass(mode)
        val address = conf.get("hardware_${type}printer_ip") ?: ""
        val dpi = Integer.valueOf(conf.get("hardware_${type}printer_dpi") ?: proto.defaultDPI.toString()).toFloat()
        val rotation = Integer.valueOf(conf.get("hardware_${type}printer_rotation") ?: "0")

        Sentry.configureScope { scope ->
            scope.setTag("printer.mode", mode)
            scope.setTag("printer.type", type)
            scope.setContexts("printer.ip", address)
            scope.setContexts("printer.dpi", dpi)
            scope.setContexts("printer.rotation", rotation)
        }

        Log.i("PrintService", "[$type] Starting Bluetooth printing")
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device = adapter.getRemoteDevice(address)

        try {
            Log.i("PrintService", "[$type] Starting renderPages")
            val futures = renderPages(proto, tmpfile, dpi, rotation, numPages, conf, type)

            lockManager.withLock("$identifier:$address") {
                when (proto) {
                    is StreamByteProtocol<*> -> {
                        // Yes, unfortunately this is necessary when using Services/IntentServices to connect to BT devices.
                        device.fetchUuidsWithSdp()
                        val socket = if (Build.BRAND == "iMin" && address == "00:11:22:33:44:55") {
                            // Hardcoded UUID for virtual bluetooth interface of built-in printer in iMin Falcon 1
                            device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                        } else {
                            device.createInsecureRfcommSocketToServiceRecord(device.uuids.first().uuid)
                        }
                        val clazz = socket.remoteDevice.javaClass
                        val paramTypes = arrayOf<Class<*>>(Integer.TYPE)
                        val m = clazz.getMethod("createRfcommSocket", *paramTypes)
                        val fallbackSocket = m.invoke(socket.remoteDevice, Integer.valueOf(1)) as BluetoothSocket
                        var connFailure: Exception? = null
                        // sometimes a closed socket from the previous print is not fully gone yet
                        // therefore we have to try multiple times to get a working one
                        for (i in 0..5) {
                            try {
                                connFailure = null
                                Log.i("PrintService", "[$type] Start connection to $address, try $i")
                                adapter.cancelDiscovery()
                                fallbackSocket.connect()
                                break
                            } catch (e: Exception) {
                                connFailure = e
                                Thread.sleep(100L)
                            }
                        }
                        if (connFailure != null) {
                            connFailure.printStackTrace()
                            val err = if (connFailure is IOException) R.string.err_job_io else R.string.err_generic
                            throw PrintException(context.applicationContext.getString(err, connFailure.message))
                        }

                        val ostream = fallbackSocket.outputStream
                        val istream = fallbackSocket.inputStream

                        try {
                            Log.i("PrintService", "[$type] Start proto.send()")
                            proto.send(futures, istream, ostream, conf, type)
                            Log.i("PrintService", "[$type] Finished proto.send()")
                        } finally {
                            socket.close()
                        }
                    }

                    is CustomByteProtocol<*> -> {
                        Log.i("PrintService", "[$type] Start proto.sendBluetooth()")
                        proto.sendBluetooth(device.address, futures, conf, type, context)
                        Log.i("PrintService", "[$type] Finished proto.sendBluetooth()")
                    }
                    is SunmiByteProtocol -> {
                        throw PrintException("Unsupported combination")
                    }
                }
            }
        } catch (e: TimeoutException) {
            e.printStackTrace()
            throw PrintException("Rendering timeout, thread may have crashed")
        } catch (e: PrintException) {
            e.printStackTrace()
            throw e // doesn't need to be wrapped again
        } catch (e: IOException) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
        } catch (e: Exception) {
            e.printStackTrace()
            throw PrintException(context.applicationContext.getString(R.string.err_generic, e.message))
        }
    }
}