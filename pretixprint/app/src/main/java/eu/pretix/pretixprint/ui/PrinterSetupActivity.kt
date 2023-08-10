package eu.pretix.pretixprint.ui

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.ESCPOS
import eu.pretix.pretixprint.byteprotocols.GraphicESCPOS
import eu.pretix.pretixprint.byteprotocols.protocols
import eu.pretix.pretixprint.connections.*
import eu.pretix.pretixprint.print.ESCPOSRenderer
import java.lang.RuntimeException

class PrinterSetupActivity : AppCompatActivity() {
    companion object {
        val EXTRA_USECASE = "TYPE"
    }

    var settingsStagingArea = mutableMapOf<String, String>()
    val fragmentManager = supportFragmentManager
    lateinit var fragment: SetupFragment
    lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    lateinit var useCase: String

    fun mode(): String {
        return settingsStagingArea.get("hardware_${useCase}printer_connection") ?: ""
    }

    fun proto(): String {
        return settingsStagingArea.get("hardware_${useCase}printer_mode") ?: ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_printer_setup)

        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (!defaultSharedPreferences.getString("pref_pin", "").isNullOrBlank() &&
            (!intent.hasExtra("pin") ||
                defaultSharedPreferences.getString("pref_pin", "") != intent.getStringExtra("pin")!!)) {
            // Protect against external calls
            finish()
            return
        }

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    startConnectionSettings()
                }
            }

        useCase = intent.extras?.getString(EXTRA_USECASE) ?: ""
        startConnectionChoice()
    }

    fun startConnectionChoice() {
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragment = ChooseConnectionTypeFragment()
        fragment.useCase = useCase
        fragmentTransaction.replace(R.id.frame, fragment)
        fragmentTransaction.commit()
    }

    fun startConnectionSettings(is_back: Boolean = false) {
        if (is_back) {
            when (settingsStagingArea.get("hardware_${useCase}printer_connection") as String) {
                SunmiInternalConnection().identifier -> return startConnectionChoice()
                IMinInternalConnection().identifier -> return startConnectionChoice()
            }
        }
        val fragmentTransaction = fragmentManager.beginTransaction()
        val connection = settingsStagingArea.get("hardware_${useCase}printer_connection") as String
        if (connection == SunmiInternalConnection().identifier) {
            settingsStagingArea.put("hardware_${useCase}printer_ip", "")
            settingsStagingArea.put("hardware_${useCase}printer_printername", "")
            return startProtocolChoice()
        }
        if (connection == IMinInternalConnection().identifier) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val manager = getSystemService(Context.USB_SERVICE) as UsbManager
                val iminPrinter = manager.deviceList.values.find { it.vendorId == 0x0519 && it.productId == 0x2013 }
                if (iminPrinter != null && !manager.hasPermission(iminPrinter)) {
                    // result is not relevant, print calls also try to acquire permission again
                    val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(),
                        if (Build.VERSION.SDK_INT >= 31) { PendingIntent.FLAG_MUTABLE } else { 0 }
                    )
                    manager.requestPermission(iminPrinter, permissionIntent)
                }
            }
            if (useCase == "receipt") {
                settingsStagingArea.put("hardware_${useCase}printer_mode", ESCPOS().identifier)
            } else {
                settingsStagingArea.put("hardware_${useCase}printer_mode", GraphicESCPOS().identifier)
                settingsStagingArea.put("hardware_${useCase}printer_graphicescposcompat", "true")
                settingsStagingArea.put("hardware_${useCase}printer_rotation", "90")
                settingsStagingArea.put("hardware_${useCase}printer_maxwidth", "72")
                settingsStagingArea.put("hardware_${useCase}printer_dpi", "203")
            }
            settingsStagingArea.put("hardware_${useCase}printer_usbcompat", "true")
            settingsStagingArea.put("hardware_${useCase}printer_ip", "519:2013")
            settingsStagingArea.put("hardware_${useCase}printer_printername", "")
            settingsStagingArea.put("hardware_${useCase}printer_waitafterpage", "100")
            settingsStagingArea.put("hardware_${useCase}printer_dialect", ESCPOSRenderer.Companion.Dialect.IMin.name)
            return startFinalPage()
        }
        if (connection == SystemConnection().identifier) {
            settingsStagingArea.put("hardware_${useCase}printer_mode", "")
            settingsStagingArea.put("hardware_${useCase}printer_ip", "")
            settingsStagingArea.put("hardware_${useCase}printer_printername", "")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            return startFinalPage()
        }
        fragment = when (connection) {
            NetworkConnection().identifier -> NetworkSettingsFragment()
            BluetoothConnection().identifier -> BluetoothSettingsFragment()
            USBConnection().identifier -> USBSettingsFragment()
            IMinInternalConnection().identifier -> USBSettingsFragment()
            CUPSConnection().identifier -> CUPSSettingsFragment()
            else -> throw RuntimeException("Unknown connection type")
        }
        fragment.useCase = intent.extras?.getString(EXTRA_USECASE) ?: ""
        fragmentTransaction.replace(R.id.frame, fragment)
        fragmentTransaction.commit()
    }

    fun startProtocolChoice() {
        val fragmentTransaction = fragmentManager.beginTransaction()
        when (settingsStagingArea.get("hardware_${useCase}printer_connection") as String) {
            CUPSConnection().identifier -> {
                startFinalPage()
            }
            else -> {
                fragment = ChooseByteProtocolFragment()
                fragment.useCase = intent.extras?.getString(EXTRA_USECASE) ?: ""
                fragmentTransaction.replace(R.id.frame, fragment)
                fragmentTransaction.commit()
            }
        }
    }

    fun startProtocolSettings(is_back: Boolean = false) {
        // For proper backwards navigation from final page
        if (is_back) {
            when (settingsStagingArea.get("hardware_${useCase}printer_connection") as String) {
                CUPSConnection().identifier -> return startConnectionSettings()
                SystemConnection().identifier -> return startConnectionChoice()
                IMinInternalConnection().identifier -> return startConnectionChoice()
            }
        }


        var fragmentFound = false
        for (p in protocols) {
            if (settingsStagingArea.get("hardware_${useCase}printer_mode") as String == p.identifier) {
                val f = p.createSettingsFragment()
                if (f == null) {
                    // e.g. escpos for receipt printing
                    if (is_back) {
                        return startProtocolChoice()
                    } else {
                        return startFinalPage()
                    }
                } else {
                    fragment = f
                }
                fragmentFound = true
                break
            }
        }
        if (!fragmentFound) {
            throw RuntimeException("Unknown protocol type")
        }

        val fragmentTransaction = fragmentManager.beginTransaction()
        fragment.useCase = intent.extras?.getString(EXTRA_USECASE) ?: ""
        fragmentTransaction.replace(R.id.frame, fragment)
        fragmentTransaction.commit()
    }

    fun startFinalPage() {
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragment = FinishSettingsFragment()
        fragment.useCase = intent.extras?.getString(EXTRA_USECASE) ?: ""
        fragmentTransaction.replace(R.id.frame, fragment)
        fragmentTransaction.commit()
    }

    fun save() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        for (p in settingsStagingArea) {
            prefs.edit().putString(p.key, p.value).apply()
        }
    }

    override fun onBackPressed() {
        fragment.back()
    }
}

abstract class SetupFragment : Fragment() {
    var useCase: String = "unknown"

    abstract fun back()
}
