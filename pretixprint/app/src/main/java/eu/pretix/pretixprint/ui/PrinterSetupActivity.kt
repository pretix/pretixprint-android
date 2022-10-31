package eu.pretix.pretixprint.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.protocols
import eu.pretix.pretixprint.connections.*
import org.jetbrains.anko.defaultSharedPreferences
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
            }
        }
        val fragmentTransaction = fragmentManager.beginTransaction()
        val connection = settingsStagingArea.get("hardware_${useCase}printer_connection") as String
        if (connection == SunmiInternalConnection().identifier) {
            settingsStagingArea.put("hardware_${useCase}printer_ip", "")
            settingsStagingArea.put("hardware_${useCase}printer_printername", "")
            return startProtocolChoice()
        }
        if (connection == SystemConnection().identifier) {
            settingsStagingArea.put("hardware_${useCase}printer_mode", "")
            settingsStagingArea.put("hardware_${useCase}printer_ip", "")
            settingsStagingArea.put("hardware_${useCase}printer_printername", "")
            if (ContextCompat.checkSelfPermission(
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
        for (p in settingsStagingArea) {
            defaultSharedPreferences.edit().putString(p.key, p.value).apply()
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
