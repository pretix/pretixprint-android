package eu.pretix.pretixprint.connections

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.*
import eu.pretix.pretixprint.print.lockManager
import eu.pretix.pretixprint.renderers.renderPages
import io.sentry.Sentry
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import kotlin.math.min

/*
With inspiration and parts taken from

https://github.com/DantSu/ESCPOS-ThermalPrinter-Android

Copyright (c) 2019 Franck ALARY

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */


object UsbDeviceHelper {
    /**
     * Find the correct USB interface for printing
     *
     * @param usbDevice USB device
     * @return correct USB interface for printing, null if not found
     */
    fun findPrinterInterface(usbDevice: UsbDevice?): UsbInterface? {
        if (usbDevice == null) {
            return null
        }
        val interfacesCount = usbDevice.interfaceCount
        for (i in 0 until interfacesCount) {
            val usbInterface = usbDevice.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return usbInterface
            }
        }
        return usbDevice.getInterface(0)
    }

    /**
     * Find the USB endpoint for device input
     *
     * @param usbInterface USB interface
     * @return Input endpoint or null if not found
     */
    fun findEndpointIn(usbInterface: UsbInterface?): UsbEndpoint? {
        if (usbInterface != null) {
            val endpointsCount = usbInterface.endpointCount
            for (i in 0 until endpointsCount) {
                val endpoint = usbInterface.getEndpoint(i)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_OUT) {
                    return endpoint
                }
            }
        }
        return null
    }

    /**
     * Find the USB endpoint for device output
     *
     * @param usbInterface USB interface
     * @return Output endpoint or null if not found
     */
    fun findEndpointOut(usbInterface: UsbInterface?): UsbEndpoint? {
        if (usbInterface != null) {
            val endpointsCount = usbInterface.endpointCount
            for (i in 0 until endpointsCount) {
                val endpoint = usbInterface.getEndpoint(i)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_IN) {
                    return endpoint
                }
            }
        }
        return null
    }
}


class UsbOutputStream(usbManager: UsbManager, usbDevice: UsbDevice, val compat: Boolean) : OutputStream() {
    companion object {
        /**
         * @see android.hardware.usb.UsbRequest#queue(ByteBuffer, int) buffer param description
         */
        const val MAX_USBFS_BUFFER_SIZE: Int = 16384
    }

    private var usbConnection: UsbDeviceConnection?
    private var usbInterface: UsbInterface?
    private var usbEndpoint: UsbEndpoint?

    @Throws(IOException::class)
    override fun write(i: Int) {
        this.write(byteArrayOf(i.toByte()))
    }

    @Throws(IOException::class)
    override fun write(bytes: ByteArray) {
        this.write(bytes, 0, bytes.size)
    }

    @Throws(IOException::class)
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (usbInterface == null || usbEndpoint == null || usbConnection == null) {
            throw IOException("Unable to connect to USB device.")
        }
        if (!usbConnection!!.claimInterface(usbInterface, true)) {
            throw IOException("Error during claim USB interface.")
        }
        val buffer = ByteBuffer.wrap(bytes.copyOfRange(offset, offset + length))


        if (compat) {
            var count = length
            var offset = offset
            while (count > 0) {
                val l = min(usbEndpoint!!.maxPacketSize, count)
                val snd = usbConnection!!.bulkTransfer(usbEndpoint, bytes, offset, l, 10000)
                if (snd < 0) {
                    throw IOException("Error sending USB data.")
                }
                count -= snd
                offset += snd
            }
        } else {
            val usbRequest = UsbRequest()
            try {
                usbRequest.initialize(usbConnection, usbEndpoint)
                if (length < MAX_USBFS_BUFFER_SIZE) {
                    if (!usbRequest.queue(buffer, length)) {
                        throw IOException("Error queueing USB request.")
                    }
                } else {
                    buffer.array().asIterable().chunked(MAX_USBFS_BUFFER_SIZE / 2).forEach {
                        if (!usbRequest.queue(ByteBuffer.wrap(it.toByteArray()), it.size)) {
                            throw IOException("Error queueing USB request.")
                        }
                    }
                }
                usbConnection!!.requestWait()
            } finally {
                usbRequest.close()
            }
        }
    }

    @Throws(IOException::class)
    override fun flush() {
    }

    @Throws(IOException::class)
    override fun close() {
        if (usbConnection != null) {
            usbConnection!!.close()
            usbInterface = null
            usbEndpoint = null
            usbConnection = null
        }
    }

    init {
        usbInterface = UsbDeviceHelper.findPrinterInterface(usbDevice)
        if (usbInterface == null) {
            throw IOException("Unable to find USB interface.")
        }
        usbEndpoint = UsbDeviceHelper.findEndpointIn(usbInterface)
        if (usbEndpoint == null) {
            throw IOException("Unable to find USB endpoint.")
        }
        usbConnection = usbManager.openDevice(usbDevice)
        if (usbConnection == null) {
            throw IOException("Unable to open USB connection.")
        }
    }
}


class UsbInputStream(usbManager: UsbManager, usbDevice: UsbDevice, val compat: Boolean) : InputStream() {
    private var usbConnection: UsbDeviceConnection?
    private var usbInterface: UsbInterface?
    private var usbEndpoint: UsbEndpoint?
    private var bufferArray = byteArrayOf()
    private var bufferOffset = 0
    private val readTimeout = 5000L

    override fun available(): Int {
        return bufferArray.size - bufferOffset
    }

    override fun read(): Int {
        if (bufferOffset >= bufferArray.size) {
            if (usbInterface == null || usbEndpoint == null || usbConnection == null) {
                throw IOException("Unable to connect to USB device.")
            }
            if (!usbConnection!!.claimInterface(usbInterface, true)) {
                throw IOException("Error during claim USB interface.")
            }

            if (compat) {
                val inbuf = ByteArray(usbEndpoint!!.maxPacketSize)
                val rcvd = usbConnection!!.bulkTransfer(usbEndpoint, inbuf, inbuf.size, readTimeout.toInt())
                bufferArray = inbuf.copyOfRange(0, rcvd)
                bufferOffset = 0
            } else {
                val usbRequest = UsbRequest()
                val buffer = ByteBuffer.allocate(usbEndpoint!!.maxPacketSize)
                try {
                    usbRequest.initialize(usbConnection, usbEndpoint)
                    if (!usbRequest.queue(buffer, buffer.capacity())) {
                        throw IOException("Error queueing USB request.")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            usbConnection!!.requestWait(readTimeout)
                        } catch (e: TimeoutException) {
                            return -1
                        }
                    } else {
                        usbConnection!!.requestWait()
                    }
                    bufferArray = buffer.array()
                    bufferOffset = 0
                } finally {
                    usbRequest.close()
                }
            }
        }
        if (bufferArray.isEmpty()) {
            return -1
        }
        val r = bufferArray[bufferOffset].toInt()
        bufferOffset += 1
        return r
    }

    @Throws(IOException::class)
    override fun close() {
        if (usbConnection != null) {
            usbConnection!!.close()
            usbInterface = null
            usbEndpoint = null
            usbConnection = null
        }
    }

    init {
        usbInterface = UsbDeviceHelper.findPrinterInterface(usbDevice)
        if (usbInterface == null) {
            throw IOException("Unable to find USB interface.")
        }
        usbEndpoint = UsbDeviceHelper.findEndpointOut(usbInterface)
        if (usbEndpoint == null) {
            throw IOException("Unable to find USB endpoint.")
        }
        usbConnection = usbManager.openDevice(usbDevice)
        if (usbConnection == null) {
            throw IOException("Unable to open USB connection.")
        }
    }
}


class USBConnection : ConnectionType {
    override val identifier = "usb"
    override val nameResource = R.string.connection_type_usb
    override val inputType = ConnectionType.Input.PLAIN_BYTES
    private val ACTION_USB_PERMISSION = "eu.pretix.pretixprint.connections.USB_PERMISSION"

    override fun allowedForUsecase(type: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun print(tmpfile: File, numPages: Int, context: Context, type: String, settings: Map<String, String>?) {
        val conf = settings?.toMutableMap() ?: mutableMapOf()
        for (entry in context.defaultSharedPreferences.all.iterator()) {
            if (!conf.containsKey(entry.key)) {
                conf[entry.key] = entry.value.toString()
            }
        }

        val mode = conf.get("hardware_${type}printer_mode") ?: "FGL"
        val serial = conf.get("hardware_${type}printer_ip") ?: "0"
        val compat = (conf.get("hardware_${type}printer_usbcompat") ?: "false") == "true"
        val proto = getProtoClass(mode)
        val dpi = Integer.valueOf(conf.get("hardware_${type}printer_dpi")
                ?: proto.defaultDPI.toString()).toFloat()

        Sentry.configureScope { scope ->
            scope.setTag("printer.mode", mode)
            scope.setTag("printer.type", type)
            scope.setContexts("printer.ip", serial)
            scope.setContexts("printer.usbcompat", compat)
            scope.setContexts("printer.dpi", dpi)
        }

        Log.i("PrintService", "Discovering USB device $serial compat=$compat")
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = mutableMapOf<String, UsbDevice>()

        manager.deviceList.forEach {
            try {
                if (it.value.serialNumber == serial) {
                    devices[it.key] = it.value
                } else if ("${Integer.toHexString(it.value.vendorId)}:${Integer.toHexString(it.value.productId)}" == serial) {
                    devices[it.key] = it.value
                } else if (it.value.deviceName == serial) {
                    // No longer used, but keep for backwards compatibility
                    devices[it.key] = it.value
                }
            } catch (e: SecurityException) {
                // On Android 10, USBDevices that have not expressively been granted access to
                // will raise an SecurityException upon accessing the Serial Number. We are just
                // ignoring those devices.
            }
        }
        if (devices.size != 1) {
            throw PrintException(context.getString(R.string.err_printer_not_found, serial))
        }
        var done = false
        val start = System.currentTimeMillis()
        var err: Exception? = null

        val recv = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    context.unregisterReceiver(this)
                    if (ACTION_USB_PERMISSION == intent.action) {
                        val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)!!
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.i("PrintService", "Found USB device")
                            try {
                                Log.i("PrintService", "Starting renderPages")
                                val futures = renderPages(proto, tmpfile, dpi, numPages, conf, type)
                                lockManager.withLock("$identifier:$serial") {
                                    when (proto) {
                                        is StreamByteProtocol<*> -> {
                                            val ostream = UsbOutputStream(manager, device, compat)
                                            val istream = UsbInputStream(manager, device, compat)

                                            try {
                                                Log.i("PrintService", "Start proto.send()")
                                                proto.send(futures, istream, ostream, conf, type)
                                                Log.i("PrintService", "Finished proto.send()")
                                            } finally {
                                                istream.close()
                                                ostream.close()
                                            }
                                        }

                                        is CustomByteProtocol<*> -> {
                                            Log.i("PrintService", "Start proto.sendUSB()")
                                            proto.sendUSB(manager, device, futures, conf, type, context)
                                            Log.i("PrintService", "Finished proto.sendUSB()")
                                        }
                                        
                                        is SunmiByteProtocol -> {
                                            throw PrintException("Unsupported combination")
                                        }
                                    }
                                }
                            } catch (e: TimeoutException) {
                                e.printStackTrace()
                                throw PrintException("Rendering timeout, thread may have crashed")
                            } catch (e: PrintError) {
                                e.printStackTrace()
                                err = PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
                            } catch (e: IOException) {
                                e.printStackTrace()
                                err = PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
                            }
                        } else {
                            err = PrintException(context.getString(R.string.err_usb_permission_denied))
                        }
                    }
                } finally {
                    done = true
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(recv, filter)
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION),
                if (Build.VERSION.SDK_INT >= 31) { PendingIntent.FLAG_MUTABLE } else { 0 })
        manager.requestPermission(devices.values.first(), permissionIntent)
        while (!done && err == null && System.currentTimeMillis() - start < 30000) {
            // Wait for callback to be called
            Thread.sleep(50)
        }
        if (err != null) {
            throw err!!
        }
        Thread.sleep(1000)
    }
}
