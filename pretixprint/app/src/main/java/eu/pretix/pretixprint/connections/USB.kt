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
import eu.pretix.pretixprint.byteprotocols.CustomByteProtocol
import eu.pretix.pretixprint.byteprotocols.PrintError
import eu.pretix.pretixprint.byteprotocols.StreamByteProtocol
import eu.pretix.pretixprint.byteprotocols.getProtoClass
import eu.pretix.pretixprint.renderers.renderPages
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import kotlin.experimental.and


class USBConnection : ConnectionType {
    override val identifier = "usb"
    override val nameResource = R.string.connection_type_usb
    override val inputType = ConnectionType.Input.PLAIN_BYTES
    private val ACTION_USB_PERMISSION = "eu.pretix.pretixprint.connections.USB_PERMISSION"
    private val DEFAULT_READ_TIMEOUT_MS = 30000
    private val DEFAULT_WRITE_TIMEOUT_MS = 2000


    protected inner class UsbSerialInputStream(private val mUsbConnection: UsbDeviceConnection,
                                               private val mUsbEndpoint: UsbEndpoint,
                                               writeTmoutMs: Int
    ) : InputStream() {
        /*
        The stream classes are from https://github.com/illarionov/RtkGps

        Copyright (c) 2013, Alexey Illarionov. All rights reserved.

        Redistribution and use in source and binary forms, with or without
        modification, are permitted provided that the following conditions
        are met:
        1. Redistributions of source code must retain the above copyright
        notice, this list of conditions and the following disclaimer.
        2. Redistributions in binary form must reproduce the above copyright
        notice, this list of conditions and the following disclaimer in the
        documentation and/or other materials provided with the distribution.

        THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
        ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
        IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
        ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
        FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
        DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
        OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
        HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
        LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
        OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
        SUCH DAMAGE.
         */
        private var mTimeout = DEFAULT_READ_TIMEOUT_MS
        private var rcvPkt: ByteArray? = null

        init {
            mTimeout = writeTmoutMs
            rcvPkt = ByteArray(mUsbEndpoint.maxPacketSize)
        }

        constructor(connection: UsbDeviceConnection,
                    bulkOutEndpoint: UsbEndpoint) : this(connection, bulkOutEndpoint, DEFAULT_READ_TIMEOUT_MS) {
        }

        @Throws(IOException::class)
        override fun read(): Int {
            synchronized(this) {
                val rcvd = read(rcvPkt, 0, 1)
                if (rcvd == 0) throw IOException("timeout")
                return (rcvPkt!![0] and 0xff.toByte()).toInt()
            }
        }

        @Throws(IOException::class)
        override fun read(buffer: ByteArray?, offset: Int, count: Int): Int {
            val rcvd: Int

            synchronized(this) {
                if (offset == 0) {
                    rcvd = mUsbConnection.bulkTransfer(mUsbEndpoint, buffer,
                            count, mTimeout)
                    if (rcvd < 0) throw IOException("bulkTransfer() error")
                    //if (D) Log.d(TAG, "Received " + rcvd + " bytes aligned");
                    return rcvd
                } else {
                    rcvd = mUsbConnection.bulkTransfer(mUsbEndpoint,
                            rcvPkt,
                            Math.min(count, rcvPkt!!.size),
                            mTimeout)
                    if (rcvd < 0)
                        throw IOException("bulkTransfer() error")
                    else if (rcvd > 0) {
                        System.arraycopy(rcvPkt!!, 0, buffer!!, offset, rcvd)
                    }
                    return rcvd
                }
            }
        }
    }

    protected inner class UsbSerialOutputStream @JvmOverloads constructor(private val mUsbConnection: UsbDeviceConnection,
                                                                          private val mUsbEndpoint: UsbEndpoint,
                                                                          writeTmoutMs: Int = DEFAULT_WRITE_TIMEOUT_MS
    ) : OutputStream() {
        private var mTimeout = DEFAULT_WRITE_TIMEOUT_MS
        private var sndPkt: ByteArray? = null

        init {
            mTimeout = writeTmoutMs
            sndPkt = ByteArray(mUsbEndpoint.maxPacketSize)
        }

        @Throws(IOException::class)
        override fun write(arg0: Int) {
            write(byteArrayOf(arg0.toByte()))
        }

        @Throws(IOException::class)
        override fun write(buffer: ByteArray, offset: Int, count: Int) {
            var offset = offset
            var count = count
            synchronized(this) {
                while (count > 0) {
                    /* XXX: timeout */
                    val length = if (count > sndPkt!!.size) sndPkt!!.size else count
                    System.arraycopy(buffer, offset, sndPkt!!, 0, length)
                    val snd = mUsbConnection.bulkTransfer(mUsbEndpoint, sndPkt, length, mTimeout)
                    if (snd < 0) throw IOException("bulkTransfer() failed")
                    Log.i("SNDUSB", "package $offset ($length): ${sndPkt!!.toString(Charset.defaultCharset())}|")
                    count -= snd
                    offset += snd
                }
            }
        }
    }


    override fun allowedForUsecase(type: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun print(tmpfile: File, numPages: Int, context: Context, type: String, settings: Map<String, String>?) {
        val conf = settings ?: emptyMap()
        fun getSetting(key: String, def: String): String {
            return conf!![key] ?: context.defaultSharedPreferences.getString(key, def)!!
        }

        val mode = getSetting("hardware_${type}printer_mode", "FGL")
        val serial = getSetting("hardware_${type}printer_ip", "0")
        val proto = getProtoClass(mode)

        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = mutableMapOf<String, UsbDevice>()

        manager.deviceList.forEach() {
            try {
                if (it.value.serialNumber == serial) {
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
                        synchronized(this) {
                            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                val intf = device!!.getInterface(0)  // TODO: does this work for all supported devices?
                                var endpoint_out: UsbEndpoint? = null
                                var endpoint_in: UsbEndpoint? = null
                                for (epid in 0 until intf.endpointCount) {
                                    val endpoint = intf.getEndpoint(epid)
                                    if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_OUT) {
                                        endpoint_out = endpoint
                                    }
                                    if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_IN) {
                                        endpoint_in = endpoint
                                    }
                                }
                                val conn = manager.openDevice(device)
                                        ?: throw PrintException(context.getString(R.string.err_usb_connection))
                                conn.claimInterface(intf, true)
                                if (endpoint_in == null || endpoint_out == null) {
                                    throw PrintException(context.getString(R.string.err_usb_connection))
                                }

                                try {
                                    val futures = renderPages(proto, tmpfile, Integer.valueOf(getSetting("hardware_${type}printer_dpi", proto.defaultDPI.toString())).toFloat(), numPages)
                                    when (proto) {
                                        is StreamByteProtocol<*> -> {
                                            val istream = UsbSerialInputStream(conn, endpoint_in)
                                            val ostream = UsbSerialOutputStream(conn, endpoint_out)

                                            try {
                                                proto.send(futures, istream, ostream)
                                            } finally {
                                                istream.close()
                                                ostream.close()
                                                conn.releaseInterface(intf)
                                                conn.close()
                                            }
                                        }

                                        is CustomByteProtocol<*> -> {
                                            proto.sendUSB(manager, device, futures, conf, type, context)
                                        }
                                    }
                                } catch (e: PrintError) {
                                    e.printStackTrace()
                                    err = PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
                                    return@synchronized
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                    err = PrintException(context.applicationContext.getString(R.string.err_job_io, e.message))
                                    return@synchronized
                                }
                            } else {
                                err = PrintException(context.getString(R.string.err_usb_permission_denied))
                                return
                            }
                        }
                    }
                } finally {
                    done = true
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(recv, filter)
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), 0)
        manager.requestPermission(devices.values.first(), permissionIntent)
        while (!done && err == null && System.currentTimeMillis() - start < 30000) {
            // Wait for callback to be called
            Thread.sleep(50)
        }
        if (err != null) {
            throw err!!
        }
        Thread.sleep(1000)
        Log.i("SNDUSB", "END OF MAIN THREAD")
    }
}
