package eu.pretix.pretixprint.ui

import android.app.ProgressDialog
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.print.getPrinter
import kotlinx.android.synthetic.main.activity_find.*
import org.cups4j.CupsPrinter
import org.cups4j.PrintJob
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.progressDialog
import org.jetbrains.anko.toast
import java.io.IOException


class ServiceAdapter(val items: List<NsdServiceInfo>, val context: FindPrinterActivity) : RecyclerView.Adapter<ViewHolder>() {
    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_networkservice, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvName.text = items.get(position).serviceName
        holder.itemView.setOnClickListener {
            context.selectService(items.get(position))
        }
    }
}

class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val tvName = view.findViewById<TextView>(R.id.textView)
}

class FindPrinterActivity : AppCompatActivity() {
    companion object {
        val EXTRA_TYPE = "TYPE"
        val TAG = "FindPrinterActivity"
        val SERVICE_TYPE = "_ipp._tcp."
    }

    private var services = emptyList<NsdServiceInfo>().toMutableList()
    private lateinit var nsdManager: NsdManager
    private var type = "ticket"
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
            runOnUiThread {
                progressBar.visibility = View.VISIBLE
            }
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
                recyclerView.adapter?.notifyDataSetChanged()
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
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
            runOnUiThread {
                progressBar.visibility = View.INVISIBLE
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

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        nsdManager = (getSystemService(Context.NSD_SERVICE) as NsdManager)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ServiceAdapter(services, this)
        type = intent.extras.getString(EXTRA_TYPE, "ticket")
        editText_ip.setText(defaultSharedPreferences.getString("hardware_${type}printer_ip", ""))
        editText_port.setText(defaultSharedPreferences.getString("hardware_${type}printer_port", ""))
        editText_printer.setText(defaultSharedPreferences.getString("hardware_${type}printer_printername", ""))

        button2.setOnClickListener {
            if (validate()) {
                testPrinter()
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_find_printer, menu)
        return super.onCreateOptionsMenu(menu)
    }

    fun testPrinter() {
        pgTest = progressDialog(R.string.testing) {
            setCancelable(false)
            isIndeterminate = true
        }
        doAsync {
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
                    val pj = PrintJob.Builder(assets.open("demopage_8in_3.25in.pdf")).build()
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
    }

    fun validate(): Boolean {
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
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> {
                if (!validate()) {
                    return true
                }

                defaultSharedPreferences.edit()
                        .putString("hardware_${type}printer_ip", editText_ip.text.toString())
                        .putString("hardware_${type}printer_port", editText_port.text.toString())
                        .putString("hardware_${type}printer_printername", editText_printer.text.toString())
                        .apply()
                finish()
                return true
            }
            R.id.action_close -> {
                finish()
                return true
            }
            android.R.id.home -> {
                NavUtils.navigateUpFromSameTask(this)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}