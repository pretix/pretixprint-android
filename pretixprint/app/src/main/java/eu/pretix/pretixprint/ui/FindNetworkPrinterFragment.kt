package eu.pretix.pretixprint.ui

import android.app.ProgressDialog
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.print.getPrinter
import kotlinx.android.synthetic.main.activity_find_network.*
import org.cups4j.CupsPrinter
import org.cups4j.PrintJob
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class FindNetworkPrinterFragment : PrinterFragment() {
    companion object {
        val TAG = "FindNWPrinterActivity"
        val SERVICE_TYPE = "_ipp._tcp."
    }

    private var services = emptyList<NsdServiceInfo>().toMutableList()
    private lateinit var nsdManager: NsdManager
    private lateinit var MODES: Array<String>
    private var pgResolve: ProgressDialog? = null
    private var pgTest: ProgressDialog? = null

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(service: NsdServiceInfo?, errorCode: Int) {
            Log.d(TAG, "Service resolve failed. Error: $errorCode Service: $service")
            runOnUiThread {
                pgResolve?.dismiss()
                toast(R.string.err_resolv_failed).show()
            }
        }

        override fun onServiceResolved(service: NsdServiceInfo?) {
            runOnUiThread {
                pgResolve?.dismiss()

                Log.d(TAG, "Service resolved. Service: $service")
                editText_ip.setText(service?.host?.getHostAddress(), TextView.BufferType.EDITABLE)
                editText_port.setText(service?.port.toString(), TextView.BufferType.EDITABLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (service?.attributes?.containsKey("rp") == true) {
                        var rp = String(service.attributes!!["rp"]!!)
                        if (rp.indexOf("printers/") === 0) {
                            rp = rp.substring(9)
                        }
                        editText_printer.setText(rp)
                    }
                }
            }
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceType == SERVICE_TYPE) {
                Log.d(TAG, "Service discovery success: $service")
                synchronized(services) {
                    var new = true
                    for (serv in services) {
                        if (serv.serviceName == service.serviceName) {
                            new = false
                            break
                        }
                    }
                    if (new) {
                        services.add(service)
                    }
                }
            } else {
                Log.d(TAG, "Unknown service type: ${service.serviceType}")
            }
            runOnUiThread {
                btnAuto.isEnabled = services.isNotEmpty()
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            // When the network service is no longer available.
            // Internal bookkeeping code goes here.
            Log.e(TAG, "Service lost: $service")
            synchronized(services) {
                for (serv in services) {
                    if (serv.serviceName == service.serviceName) {
                        services.remove(serv)
                    }
                }
            }
            runOnUiThread {
                btnAuto.isEnabled = services.isNotEmpty()
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
            runOnUiThread {
                btnAuto.isEnabled = services.isNotEmpty()
            }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code:$errorCode")
            // TODO: Toast/Snackbar
            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code:$errorCode")
            // TODO: Toast/Snackbar
            nsdManager.stopServiceDiscovery(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments!!.putString("connection", "network_printer")
        nsdManager = activity!!.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater!!.inflate(R.layout.activity_find_network, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (getType() == "receipt") {
            MODES = arrayOf("ESC/POS")
        } else {
            MODES = arrayOf("CUPS/IPP", "FGL", "SLCS")
        }

        editText_ip.setText(defaultSharedPreferences.getString("hardware_${getType()}printer_ip", ""))
        editText_port.setText(defaultSharedPreferences.getString("hardware_${getType()}printer_port", ""))
        editText_dpi.setText(defaultSharedPreferences.getString("hardware_${getType()}printer_dpi", ""))
        editText_printer.setText(defaultSharedPreferences.getString("hardware_${getType()}printer_printername", ""))

        ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_item,
                android.R.id.text1,
                MODES
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spinner_mode.adapter = adapter
            spinner_mode.setSelection(MODES.indexOf(defaultSharedPreferences.getString("hardware_${getType()}printer_mode", "CUPS/IPP")))
        }


        button2.setOnClickListener {
            if (validate()) {
                testPrinter()
            }
        }

        btnAuto.setOnClickListener {
            val services = ArrayList(this.services)
            selector(getString(R.string.headline_found_network_printers), services.map { it.serviceName }) { dialogInterface, i ->
                selectService(services[i])
            }
        }
    }

    fun selectService(service: NsdServiceInfo) {
        try {
            nsdManager.resolveService(service, resolveListener)
            runOnUiThread {
                pgResolve = progressDialog(R.string.resolving) {
                    isIndeterminate = true
                    setCancelable(false)
                }
            }
        } catch (e: IllegalArgumentException) {

        }
    }

    override fun onPause() {
        nsdManager.stopServiceDiscovery(discoveryListener)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun testPrinter() {
        val mode = MODES[spinner_mode.selectedItemPosition]
        pgTest = progressDialog(R.string.testing) {
            setCancelable(false)
            isIndeterminate = true
        }
        doAsync {
            when (mode) {
                "FGL", "SLCS" -> {
                    try {

                        val file = File(ctx.cacheDir, "demopage.pdf")
                        if (file.exists()) {
                            file.delete()
                        }
                        val asset = ctx.assets.open("demopage_8in_3.25in.pdf")
                        val output = FileOutputStream(file)
                        val buffer = ByteArray(1024)
                        var size = asset.read(buffer)
                        while (size != -1) {
                            output.write(buffer, 0, size)
                            size = asset.read(buffer)
                        }
                        asset.close()
                        output.close()
                        file.delete()

                        runOnUiThread {
                            pgTest?.dismiss()
                            toast(R.string.test_success)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        runOnUiThread {
                            pgTest?.dismiss()
                            toast(getString(R.string.err_job_io, e.message))
                        }
                        return@doAsync
                    }
                }
                "CUPS/IPP" -> {
                    var cp: CupsPrinter? = null
                    try {
                        cp = getPrinter(
                                editText_ip.text.toString(),
                                editText_port.text.toString(),
                                editText_printer.text.toString()
                        )
                    } catch (e: IOException) {
                        e.printStackTrace()
                        runOnUiThread {
                            pgTest?.dismiss()
                            toast(getString(R.string.err_cups_io, e.message));
                        }
                        return@doAsync
                    }
                    if (cp == null) {
                        runOnUiThread {
                            pgTest?.dismiss()
                            toast(getString(R.string.err_printer_not_found, editText_printer.text.toString()))
                        }
                    } else {
                        try {
                            val pj = PrintJob.Builder(ctx.assets.open("demopage_8in_3.25in.pdf")).build()
                            cp.print(pj)
                            runOnUiThread {
                                pgTest?.dismiss()
                                toast(R.string.test_success)
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                            runOnUiThread {
                                pgTest?.dismiss()
                                toast(getString(R.string.err_job_io, e.message));
                            }
                        }
                    }
                }
                "ESC/POS" -> {
                    try {
                        val file = File(ctx.cacheDir, "demopage.txt")
                        if (file.exists()) {
                            file.delete()
                        }
                        val asset = ctx.assets.open("demopage.txt")
                        val output = FileOutputStream(file)
                        val buffer = ByteArray(1024)
                        var size = asset.read(buffer)
                        while (size != -1) {
                            output.write(buffer, 0, size)
                            size = asset.read(buffer)
                        }
                        asset.close()
                        output.close()

                        file.delete()

                        runOnUiThread {
                            pgTest?.dismiss()
                            toast(R.string.test_success)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        runOnUiThread {
                            pgTest?.dismiss()
                            toast(getString(R.string.err_job_io, e.message))
                        }
                        return@doAsync
                    }
                }
                else -> {
                    toast("Connection test unsupported")
                }
            }
        }
    }

    override fun validate(): Boolean {
        if (TextUtils.isEmpty(editText_ip.text)) {
            editText_ip.error = getString(R.string.err_field_required)
            return false
        }
        if (TextUtils.isEmpty(editText_port.text)) {
            editText_port.error = getString(R.string.err_field_required)
            return false
        }
        if (TextUtils.isEmpty(editText_printer.text)) {
            editText_printer.error = getString(R.string.err_field_required)
            return false
        }
        val mode = MODES[spinner_mode.selectedItemPosition]
        if (mode == "FGL" || mode == "SLCS") {
            if (TextUtils.isEmpty(editText_dpi.text)) {
                editText_dpi.error = getString(R.string.err_field_required)
                return false
            }
        }
        return true
    }

    override fun savePrefs() {
        defaultSharedPreferences.edit()
                .putString("hardware_${getType()}printer_ip", editText_ip.text.toString())
                .putString("hardware_${getType()}printer_port", editText_port.text.toString())
                .putString("hardware_${getType()}printer_dpi", if (editText_dpi.text.toString().isNotEmpty()) editText_dpi.text.toString() else "0")
                .putString("hardware_${getType()}printer_printername", editText_printer.text.toString())
                .putString("hardware_${getType()}printer_mode", MODES[spinner_mode.selectedItemPosition])
                .putString("hardware_${getType()}printer_connection", getConnection())
                .apply()
    }
}