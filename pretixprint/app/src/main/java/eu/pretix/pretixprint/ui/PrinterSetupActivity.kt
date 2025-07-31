package eu.pretix.pretixprint.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.ESCPOS
import eu.pretix.pretixprint.byteprotocols.GraphicESCPOS
import eu.pretix.pretixprint.byteprotocols.protocols
import eu.pretix.pretixprint.connections.*
import eu.pretix.pretixprint.print.ESCPOSRenderer

class PrinterSetupActivity : AppCompatActivity() {
    companion object {
        val EXTRA_USECASE = "TYPE"
        val REQUEST_CODE_NOTIFICATIONS_AND_SAVE = 19567
    }

    var settingsStagingArea = mutableMapOf<String, String>()
    val fragmentManager = supportFragmentManager
    lateinit var fragment: SetupFragment
    lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    lateinit var useCase: String

    fun mode(): String {
        return settingsStagingArea.get("hardware_${useCase}printer_connection") ?: ""
    }

    fun proto(): String {
        return settingsStagingArea.get("hardware_${useCase}printer_mode") ?: ""
    }

    fun warnDiscardedSettings(): Boolean {
        if (settingsStagingArea.isNotEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.settings_not_saved)
                .setPositiveButton(R.string.settings_discard) { di, _ ->
                    di.dismiss()
                    finish()
                }
                .setNegativeButton(R.string.settings_continue) { di, _ ->
                    di.dismiss()
                }
                .create()
                .show()
            return true
        }
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (warnDiscardedSettings()) {
                    return true
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_printer_setup)
        setSupportActionBar(findViewById(R.id.topAppBar))
        supportActionBar?.let {
            it.setHomeButtonEnabled(true)
            it.setDisplayUseLogoEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }

        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (!defaultSharedPreferences.getString("pref_pin", "").isNullOrBlank() &&
            (!intent.hasExtra("pin") ||
                defaultSharedPreferences.getString("pref_pin", "") != intent.getStringExtra("pin")!!)) {
            // Protect against external calls
            finish()
            return
        }

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantMap: Map<String, Boolean> ->
                if (!grantMap.containsValue(false)) {
                    startConnectionSettings()
                }
            }

        useCase = intent.extras?.getString(EXTRA_USECASE) ?: ""

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.frame)
        ) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
                    or WindowInsetsCompat.Type.ime()
            )
            v.updatePadding(
                left = insets.left,
                right = insets.right,
                top = 0, // handled by AppBar
                bottom = insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

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
        val oldPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val connectionChanged = oldPrefs.getString("hardware_${useCase}printer_connection", "") != connection

        if (connection == IMinInternalConnection().identifier) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val manager = getSystemService(Context.USB_SERVICE) as UsbManager
                val iminPrinter = manager.deviceList.values.find { it.vendorId == 0x0519 && it.productId == 0x2013 }
                if (iminPrinter != null && !manager.hasPermission(iminPrinter)) {
                    // result is not relevant, print calls also try to acquire permission again
                    val permissionIntent = PendingIntentCompat.getBroadcast(this, 0, Intent(), 0, true)
                    manager.requestPermission(iminPrinter, permissionIntent)
                }
            }
            if (useCase == "receipt") {
                settingsStagingArea.put("hardware_${useCase}printer_mode", ESCPOS().identifier)
            } else {
                settingsStagingArea.put("hardware_${useCase}printer_mode", GraphicESCPOS().identifier)
                settingsStagingArea.put("hardware_${useCase}printer_graphicescposcompat", "true")

                if (connectionChanged) {
                    settingsStagingArea.put("hardware_${useCase}printer_rotation", "90")
                    settingsStagingArea.put("hardware_${useCase}printer_maxwidth", "72")
                    settingsStagingArea.put("hardware_${useCase}printer_dpi", "203")
                }
            }
            settingsStagingArea.put("hardware_${useCase}printer_usbcompat", "true")
            settingsStagingArea.put("hardware_${useCase}printer_ip", "519:2013")
            settingsStagingArea.put("hardware_${useCase}printer_printername", "")
            settingsStagingArea.put("hardware_${useCase}printer_dialect", ESCPOSRenderer.Companion.Dialect.IMin.name)
            if (connectionChanged) {
                settingsStagingArea.put("hardware_${useCase}printer_waitafterpage", "100")
            }
            if (useCase == "receipt") {
                return startFinalPage()
            } else {
                return startProtocolSettings()
            }
        }
        if (connection == SystemConnection().identifier) {
            settingsStagingArea.put("hardware_${useCase}printer_mode", "")
            settingsStagingArea.put("hardware_${useCase}printer_ip", "")
            settingsStagingArea.put("hardware_${useCase}printer_printername", "")
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.USE_FULL_SCREEN_INTENT) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.USE_FULL_SCREEN_INTENT)
            }
            if (perms.isNotEmpty()) {
                requestPermissionLauncher.launch(perms.toTypedArray())
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

    fun startProtocolChoice(is_back: Boolean = false) {
        if (is_back) {
            when (settingsStagingArea.get("hardware_${useCase}printer_connection") as String) {
                IMinInternalConnection().identifier -> return startConnectionChoice()
            }
        }

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
                IMinInternalConnection().identifier -> {
                    if (useCase == "receipt") {
                        return startConnectionChoice()
                    }
                }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_NOTIFICATIONS_AND_SAVE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                save()
                finish()
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.notification_permission_denied)
                    .setPositiveButton(R.string.notification_permission_denied_understood) { _, _ ->
                        save()
                        finish()
                    }
                    .create()
                    .show()
            }
        }
    }

    fun finalize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_NOTIFICATIONS_AND_SAVE
                )
            } else {
                save()
                finish()
            }
        } else {
            save()
            finish()
        }
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
