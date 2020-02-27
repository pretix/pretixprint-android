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
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R
import org.jetbrains.anko.support.v4.*

class CUPSSettingsFragment : SetupFragment() {
    companion object {
        val TAG = "CUPSSettingsFragment"
        val SERVICE_TYPE = "_ipp._tcp."
    }

    private var services = emptyList<NsdServiceInfo>().toMutableList()
    private lateinit var nsdManager: NsdManager
    private lateinit var MODES: Array<String>
    private var pgResolve: ProgressDialog? = null

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
                view?.findViewById<TextInputEditText>(R.id.teIP)?.setText(service?.host?.hostAddress)
                view?.findViewById<TextInputEditText>(R.id.tePort)?.setText(service?.port.toString())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (service?.attributes?.containsKey("rp") == true) {
                        var rp = String(service.attributes!!["rp"]!!)
                        if (rp.indexOf("printers/") === 0) {
                            rp = rp.substring(9)
                        }
                        view?.findViewById<TextInputEditText>(R.id.teQueue)?.setText(rp)
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
                view?.findViewById<Button>(R.id.btnAuto)?.isEnabled = services.isNotEmpty()
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
                view?.findViewById<Button>(R.id.btnAuto)?.isEnabled = services.isNotEmpty()
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
            runOnUiThread {
                view?.findViewById<Button>(R.id.btnAuto)?.isEnabled = services.isNotEmpty()
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
        nsdManager = activity!!.getSystemService(Context.NSD_SERVICE) as NsdManager
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

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_cups_settings, container, false)

        val currentIP = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_ip"
        ) as String?) ?: defaultSharedPreferences.getString("hardware_${useCase}printer_ip", "")
        view.findViewById<TextInputEditText>(R.id.teIP).setText(currentIP)

        val currentPort = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_port"
        ) as String?)
                ?: defaultSharedPreferences.getString("hardware_${useCase}printer_port", "631")
        view.findViewById<TextInputEditText>(R.id.tePort).setText(currentPort)

        val currentName = ((activity as PrinterSetupActivity).settingsStagingArea.get(
                "hardware_${useCase}printer_printername"
        ) as String?)
                ?: defaultSharedPreferences.getString("hardware_${useCase}printer_printername", "")
        view.findViewById<TextInputEditText>(R.id.teQueue).setText(currentName)

        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            (activity as PrinterSetupActivity).startConnectionChoice()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            val ip = view.findViewById<TextInputEditText>(R.id.teIP).text.toString()
            val port = view.findViewById<TextInputEditText>(R.id.tePort).text.toString()
            val queue = view.findViewById<TextInputEditText>(R.id.teQueue).text.toString()
            if (TextUtils.isEmpty(ip)) {
                view.findViewById<TextInputEditText>(R.id.teIP).error = getString(R.string.err_field_required)
            } else if (TextUtils.isEmpty(port)) {
                view.findViewById<TextInputEditText>(R.id.tePort).error = getString(R.string.err_field_required)
                view.findViewById<TextInputEditText>(R.id.teIP).error = null
            } else if (!TextUtils.isDigitsOnly(port)) {
                view.findViewById<TextInputEditText>(R.id.tePort).error = getString(R.string.err_field_invalid)
                view.findViewById<TextInputEditText>(R.id.teIP).error = null
            } else if (TextUtils.isEmpty(queue)) {
                view.findViewById<TextInputEditText>(R.id.teQueue).error = getString(R.string.err_field_invalid)
                view.findViewById<TextInputEditText>(R.id.teIP).error = null
                view.findViewById<TextInputEditText>(R.id.tePort).error = null
            } else {
                view.findViewById<TextInputEditText>(R.id.teIP).error = null
                view.findViewById<TextInputEditText>(R.id.tePort).error = null
                view.findViewById<TextInputEditText>(R.id.teQueue).error = null
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_ip",
                        ip)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_port",
                        port)
                (activity as PrinterSetupActivity).settingsStagingArea.put("hardware_${useCase}printer_printername",
                        queue)
                (activity as PrinterSetupActivity).startFinalPage()
            }
        }


        view.findViewById<Button>(R.id.btnAuto).setOnClickListener {
            val services = ArrayList(this.services)
            selector(getString(R.string.headline_found_network_printers), services.map { it.serviceName }) { dialogInterface, i ->
                selectService(services[i])
            }
        }
        return view
    }
}
