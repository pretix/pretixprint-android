package eu.pretix.pretixprint.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.ESCPOS
import eu.pretix.pretixprint.byteprotocols.FGL
import eu.pretix.pretixprint.byteprotocols.SLCS
import eu.pretix.pretixprint.connections.BluetoothConnection
import eu.pretix.pretixprint.connections.CUPSConnection
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.connections.USBConnection
import org.jetbrains.anko.defaultSharedPreferences
import java.lang.RuntimeException

class PrinterSetupActivity : AppCompatActivity() {
    companion object {
        val EXTRA_USECASE = "TYPE"
    }

    var settingsStagingArea = mutableMapOf<String, String>()
    val fragmentManager = supportFragmentManager
    lateinit var fragment: SetupFragment
    lateinit var useCase: String

    fun mode(): String {
        return settingsStagingArea.get("hardware_${useCase}printer_connection") ?: ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_printer_setup)
        useCase = intent.extras.getString(EXTRA_USECASE) ?: ""
        startConnectionChoice()
    }

    fun startConnectionChoice() {
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragment = ChooseConnectionTypeFragment()
        fragment.useCase = useCase
        fragmentTransaction.replace(R.id.frame, fragment)
        fragmentTransaction.commit()
    }

    fun startConnectionSettings() {
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragment = when (settingsStagingArea.get("hardware_${useCase}printer_connection") as String) {
            NetworkConnection().identifier -> NetworkSettingsFragment()
            BluetoothConnection().identifier -> BluetoothSettingsFragment()
            USBConnection().identifier -> USBSettingsFragment()
            CUPSConnection().identifier -> CUPSSettingsFragment()
            else -> throw RuntimeException("Unknown connection type")
        }
        fragment.useCase = intent.extras.getString(EXTRA_USECASE) ?: ""
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
                fragment.useCase = intent.extras.getString(EXTRA_USECASE) ?: ""
                fragmentTransaction.replace(R.id.frame, fragment)
                fragmentTransaction.commit()
            }
        }
    }

    fun startProtocolSettings(is_back: Boolean = false) {
        if (is_back && settingsStagingArea.get("hardware_${useCase}printer_connection") == CUPSConnection().identifier) {
            // For proper backwards navigation from final page
            return startConnectionSettings()
        } else if (settingsStagingArea.get("hardware_${useCase}printer_mode") == ESCPOS().identifier) {
            if (is_back) {
                return startProtocolChoice()
            } else {
                return startFinalPage()
            }
        }

        val fragmentTransaction = fragmentManager.beginTransaction()
        fragment = when (settingsStagingArea.get("hardware_${useCase}printer_mode") as String) {
            FGL().identifier -> FGLSettingsFragment()
            SLCS().identifier -> SLCSSettingsFragment()
            else -> throw RuntimeException("Unknown protocol type")
        }
        fragment.useCase = intent.extras.getString(EXTRA_USECASE) ?: ""
        fragmentTransaction.replace(R.id.frame, fragment)
        fragmentTransaction.commit()
    }

    fun startFinalPage() {
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragment = FinishSettingsFragment()
        fragment.useCase = intent.extras.getString(EXTRA_USECASE) ?: ""
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
