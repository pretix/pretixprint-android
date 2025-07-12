package eu.pretix.pretixprint.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.View.GONE
import android.view.View.VISIBLE
import android.webkit.WebView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.pretix.pretixprint.BuildConfig
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.IMinInternalConnection
import eu.pretix.pretixprint.connections.SunmiInternalConnection
import eu.pretix.pretixprint.connections.SystemConnection
import eu.pretix.pretixprint.print.testPrint
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import splitties.toast.toast
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min
import androidx.core.content.edit
import eu.pretix.pretixprint.hardware.DataWedgeHelper
import eu.pretix.pretixprint.hardware.HardwareScanner
import eu.pretix.pretixprint.hardware.ScanReceiver
import eu.pretix.pretixprint.hardware.defaultToScanner


class SettingsFragment : PreferenceFragmentCompat() {
    val bgScope = CoroutineScope(Dispatchers.IO)
    lateinit var defaultSharedPreferences: SharedPreferences
    val types = listOf("ticket", "badge", "receipt")
    var pendingPinAction: ((pin: String) -> Unit)? = null
    val hardwareScanner = HardwareScanner(object : ScanReceiver {
        override fun scanResult(result: String) {
            importSettingsFromJsonString(result)
        }
    })
    var versionClickCount = 0
    var versionClickToast: Toast? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey)

        for (type in types) {
            findPreference<PrinterPreference>("hardware_${type}printer_find")?.apply {
                setOnPreferenceClickListener {
                    val intent = Intent(activity, PrinterSetupActivity::class.java)
                    intent.putExtra(PrinterSetupActivity.EXTRA_USECASE, type)
                    startWithPIN(intent)
                    return@setOnPreferenceClickListener true
                }
                setOnMenuItemClickListener = { menuItem ->
                    when (menuItem.itemId) {
                        R.id.testpage -> { printTestPage(type); true }
                        R.id.maintenance -> { openMaintainPrinter(type); true }
                        R.id.remove -> { confirmRemovePrinter(type); true }
                        else -> false
                    }
                }
            }
        }

        findPreference<ListPreference>("hardware_receiptprinter_cpl")?.setOnPreferenceChangeListener { preference, newValue ->
            preference.summary = getString(R.string.pref_printer_cpl, newValue)
            return@setOnPreferenceChangeListener true
        }

        findPreference<Preference>("last_prints")?.setOnPreferenceClickListener {
            show_last_prints()
            return@setOnPreferenceClickListener true
        }

        findPreference<Preference>("licenses")?.setOnPreferenceClickListener {
            asset_dialog(R.string.settings_label_licenses)
            return@setOnPreferenceClickListener true
        }

        findPreference<ProtectedListPreference>("hardware_receiptprinter_cpl")?.setEarlyPreferenceClickListener { pref ->
            if (!hasPin()) {
                // false: handle normally
                return@setEarlyPreferenceClickListener false
            }
            pinProtect {
                (pref as ProtectedListPreference).showDialog()
            }
            // true: we've handled it, skip
            return@setEarlyPreferenceClickListener true
        }

        findPreference<ProtectedEditTextPreference>("pref_pin")?.setEarlyPreferenceClickListener { pref ->
            if (!hasPin()) {
                // false: handle normally
                return@setEarlyPreferenceClickListener false
            }
            pinProtect {
                (pref as ProtectedEditTextPreference).showDialog()
            }
            return@setEarlyPreferenceClickListener true
        }

        findPreference<Preference>("version")?.apply {
            setOnPreferenceClickListener {
                if (defaultSharedPreferences.getBoolean("dev_mode", false)) {
                    if (versionClickToast != null) {
                        versionClickToast!!.cancel()
                    }
                    versionClickToast = Toast.makeText(context, R.string.show_dev_already, Toast.LENGTH_SHORT).apply { show() }
                    return@setOnPreferenceClickListener true
                }
                if (versionClickCount < 4) {
                    versionClickCount++
                    val remaining = 5 - versionClickCount
                    if (versionClickToast != null) {
                        versionClickToast!!.cancel()
                    }
                    val text = resources.getQuantityString(R.plurals.show_dev_countdown, remaining, remaining)
                    versionClickToast = Toast.makeText(context, text, Toast.LENGTH_SHORT).apply { show() }
                } else {
                    defaultSharedPreferences.edit() { putBoolean("dev_mode", true) }
                    findPreference<PreferenceCategory>("export_import")?.isVisible = true
                    findPreference<Preference>("import_scan")?.isVisible = defaultToScanner()
                    if (versionClickToast != null) {
                        versionClickToast!!.cancel()
                    }
                    versionClickToast = Toast.makeText(context, R.string.show_dev_on, Toast.LENGTH_SHORT).apply { show() }
                }
                return@setOnPreferenceClickListener true
            }
            summary = BuildConfig.VERSION_NAME
        }

        findPreference<PreferenceCategory>("export_import")?.isVisible = defaultSharedPreferences.getBoolean("dev_mode", false)
        findPreference<Preference>("import_scan")?.isVisible = defaultToScanner()
        findPreference<ProtectedEditTextPreference>("export")?.setEarlyPreferenceClickListener { pref ->
            pinProtect {
                startActivity(Intent(requireContext(), SettingsExportActivity::class.java))
            }
            return@setEarlyPreferenceClickListener true
        }

        childFragmentManager.setFragmentResultListener(PinDialog.RESULT_PIN, this) { _, bundle ->
            val pin = bundle.getString(PinDialog.RESULT_PIN)
            if (pin != null && defaultSharedPreferences.getString("pref_pin", "") == pin) {
                (childFragmentManager.findFragmentByTag(PinDialog.TAG) as? PinDialog)?.dismiss()
                pendingPinAction?.let { it(pin) }
            }
        }
    }

    override fun onStop() {
        hardwareScanner.stop(requireContext())
        super.onStop()
    }

    fun updatePreferenceViews() {
        var anyVisible = false
        var usesSystemPrinter = false
        for (type in types) {
            findPreference<PrinterPreference>("hardware_${type}printer_find")?.apply {
                val connection = defaultSharedPreferences.getString("hardware_${type}printer_connection", "")
                if (!TextUtils.isEmpty(connection)) {
                    moreVisibility = VISIBLE
                    anyVisible = true
                    summary = printerSummary(type)
                    if (connection == SystemConnection().identifier) {
                        usesSystemPrinter = true
                    }
                } else {
                    moreVisibility = GONE
                    summary = ""
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            findPreference<Preference>("notification_permission")?.isVisible = anyVisible && ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
            findPreference<Preference>("notification_permission")?.setOnPreferenceClickListener {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    SettingsActivity.REQUEST_CODE_NOTIFICATIONS
                )
                true
            }
        } else {
            findPreference<Preference>("notification_permission")?.isVisible = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && usesSystemPrinter) {
            findPreference<Preference>("fullscreen_permission")?.isVisible = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.USE_FULL_SCREEN_INTENT
            ) != PackageManager.PERMISSION_GRANTED
            findPreference<Preference>("fullscreen_permission")?.setOnPreferenceClickListener {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.USE_FULL_SCREEN_INTENT),
                    SettingsActivity.REQUEST_CODE_NOTIFICATIONS
                )
                true
            }
        } else {
            findPreference<Preference>("fullscreen_permission")?.isVisible = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(
                requireContext().applicationContext.packageName
            )
            findPreference<Preference>("battery_optimizations")?.isVisible = anyVisible && !isIgnoringBatteryOptimizations
            findPreference<Preference>("battery_optimizations")?.setOnPreferenceClickListener {
                val intent = Intent()
                intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS would be nicer but is restricted in play store
                // intent.data = Uri.parse("package:${requireContext().applicationContext.packageName}")
                startActivity(intent)
                true
            }
        } else {
            findPreference<Preference>("battery_optimizations")?.isVisible = false
        }

        val cpl = findPreference<ListPreference>("hardware_receiptprinter_cpl")
        if (cpl != null) {
            val entry = defaultSharedPreferences.getString("hardware_receiptprinter_cpl", null)
            cpl.summary = if (entry.isNullOrEmpty()) {
                getString(R.string.pref_printer_cpl, cpl.entries[31])
            } else {
                getString(R.string.pref_printer_cpl, entry)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePreferenceViews()
        val dwh = DataWedgeHelper(requireContext(), "pretixprint", R.raw.dwprofile)
        if (dwh.hasDataWedge) {
            dwh.install()
        }
        hardwareScanner.start(requireContext())
    }

    private fun printerSummary(type: String): String {
        val ip = defaultSharedPreferences.getString("hardware_${type}printer_ip", "")
        val name = defaultSharedPreferences.getString("hardware_${type}printer_printername", "")
        val connection = defaultSharedPreferences.getString("hardware_${type}printer_connection", "network_printer")
        val connectionStringId = resources.getIdentifier(connection, "string", requireActivity().packageName)
        val humanConnection = if (connectionStringId != 0) getString(connectionStringId) else "???"
        if (connection in listOf(IMinInternalConnection().identifier, SunmiInternalConnection().identifier, SystemConnection().identifier)) {
            return getString(R.string.pref_printer_current_short, humanConnection)
        }
        return getString(R.string.pref_printer_current, name, ip, humanConnection)
    }

    private fun asset_dialog(@StringRes title: Int) {
        val webView = WebView(requireActivity())
        webView.loadUrl("file:///android_asset/about.html")
        webView.setBackgroundColor(Color.TRANSPARENT)

        val dialog = MaterialAlertDialogBuilder(requireActivity())
                .setTitle(title)
                .setView(webView)
                .setPositiveButton(R.string.dismiss, null)
                .create()

        dialog.show()
    }

    fun show_last_prints() {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val files = requireActivity().cacheDir
                .listFiles { file, s -> s.startsWith("print_") || s.startsWith("error_") }!!
                .toList()
                .filter {
                    System.currentTimeMillis() - it.lastModified() < 3600 * 1000
                }
                .sortedByDescending { it.lastModified() }
        val names = files
                .subList(0, min(files.size, 20))
                .map {
                    "${f.format(Date(it.lastModified()))} (${it.name.split(".")[1]})"
                }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_label_last_prints)
            .setItems(names.toTypedArray()) { _, i ->
                val file = files[i]
                val extension = file.name.split(".")[1]
                when (extension) {
                    "escpos" -> {
                        startActivity(Intent(requireContext(), FileViewerEscposActivity::class.java).apply {
                            putExtra(FileViewerEscposActivity.EXTRA_PATH, file.absolutePath)
                        })
                    }
                    "log" -> {
                        startActivity(Intent(requireContext(), FileViewerLogActivity::class.java).apply {
                            putExtra(FileViewerLogActivity.EXTRA_PATH, file.absolutePath)
                        })
                    }
                    "pdf" -> {
                        startActivity(Intent(requireContext(), FileViewerPdfActivity::class.java).apply {
                            putExtra(FileViewerPdfActivity.EXTRA_PATH, file.absolutePath)
                        })
                    }
                    else -> throw RuntimeException("Unknown file type for file $file")
                }
            }
            .create()
            .show()
    }

    fun hasPin(): Boolean {
        return !defaultSharedPreferences.getString("pref_pin", "").isNullOrBlank()
    }

    fun pinProtect(valid: ((pin: String) -> Unit)) {
        if (!hasPin()) {
            valid("")
            return
        }
        pendingPinAction = valid
        PinDialog().show(childFragmentManager)
    }

    fun startWithPIN(intent: Intent) {
        pinProtect { pin ->
            intent.putExtra("pin", pin)
            startActivity(intent)
        }
    }

    fun printTestPage(useCase: String) {
        pinProtect {
            val proto = defaultSharedPreferences.getString("hardware_${useCase}printer_mode", "")!!
            val mode = defaultSharedPreferences.getString("hardware_${useCase}printer_connection", "")!!
            if (proto == "" && mode == "") return@pinProtect

            val settingsMap = mutableMapOf<String, String>()
            defaultSharedPreferences.all.mapValuesTo(settingsMap) { it.value.toString() }

            val typeRef = resources.getIdentifier("settings_label_${useCase}printer", "string", requireActivity().packageName)
            val testMessage = getString(R.string.testing_printer, getString(typeRef))

            val progress = Snackbar.make(requireContext(), listView, testMessage, BaseTransientBottomBar.LENGTH_INDEFINITE)
            progress.show()
            bgScope.launch {
                try {
                    testPrint(requireContext(), proto, mode, useCase, settingsMap)

                    activity?.runOnUiThread {
                        progress.dismiss()
                        MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.test_success).create().show()
                    }
                } catch (e: PrintException) {
                    Sentry.captureException(e)
                    activity?.runOnUiThread {
                        progress.dismiss()
                        MaterialAlertDialogBuilder(requireContext()).setMessage(e.message).create().show()
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                    Sentry.captureException(e)
                    activity?.runOnUiThread {
                        progress.dismiss()
                        MaterialAlertDialogBuilder(requireContext()).setMessage(e.toString()).create().show()
                    }
                } finally {
                    activity?.runOnUiThread {
                        progress.dismiss()
                    }
                }
            }
        }
    }

    fun openMaintainPrinter(type: String) {
        val intent = Intent(requireContext(), MaintenanceActivity::class.java)
        intent.putExtra(MaintenanceActivity.EXTRA_TYPE, type)
        startWithPIN(intent)
    }

    fun confirmRemovePrinter(type: String) {
        pinProtect {
            val typeRef = resources.getIdentifier("settings_label_${type}printer", "string", requireActivity().packageName)
            val summary = printerSummary(type)
            val alert = MaterialAlertDialogBuilder(requireActivity())
                .setTitle(getString(R.string.pref_delete_printer, getString(typeRef)))
                .setMessage(summary)
                .setPositiveButton(R.string.action_delete) { dialog, _ ->
                    removePrinter(type)
                    onResume() // refresh preferences
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            alert.show()
        }
    }

    @SuppressLint("ApplySharedPref")
    fun removePrinter(type: String) {
        val edit = defaultSharedPreferences.edit()
        defaultSharedPreferences.all.keys
            .filter { it.startsWith("hardware_${type}") }
            .forEach { edit.remove(it) }
        edit.apply()
    }

    fun importSettingsFromJsonString(data: String) {
        if (!data.startsWith("{")) {
            return
        }
        val data = JSONObject(data)
        if (!data.has("__pretixprintconf")) {
            return
        }

        pinProtect {
            var size = 0
            defaultSharedPreferences.edit(commit = true) {
                data.keys().forEach { key ->
                    if (key.startsWith("__")) return@forEach
                    size += 1

                    val v: Any = data.get(key)
                    when(v) {
                        JSONObject.NULL -> remove(key)
                        is Int -> putInt(key, v)
                        is Long -> putLong(key, v)
                        is Float -> putFloat(key, v)
                        is Double -> putFloat(key, v.toFloat())
                        is Boolean -> putBoolean(key, v)
                        else -> putString(key, v.toString())
                    }
                }
            }
            toast(getString(R.string.import_successful, size))
            updatePreferenceViews()
        }
    }
}

class SettingsActivity : AppCompatActivity() {
    companion object {
        val REQUEST_CODE_NOTIFICATIONS = 19467
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyRestrictions(this)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.contains("first_start")) {
            prefs.edit().putBoolean("first_start", true).apply()
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK.or(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            startActivity(intent)
            finish()
        }

        setContentView(R.layout.activity_settings)

        // Display the fragment as the main content.
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, SettingsFragment())
            .commit()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_NOTIFICATIONS) {
            supportFragmentManager.fragments.forEach { it.onResume() }
        }
    }

    fun applyRestrictions(ctx: Context) {
        val restrictionsMgr = ctx.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager?
            ?: return
        val restrictions = restrictionsMgr.applicationRestrictions
        if (restrictions.containsKey("pref_pin")) {
            PreferenceManager
                .getDefaultSharedPreferences(this)
                .edit()
                .putString("pref_pin", restrictions.getString("pref_pin"))
                .apply()
        }
    }
}
