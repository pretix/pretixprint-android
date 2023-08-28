package eu.pretix.pretixprint.ui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View.GONE
import android.view.View.VISIBLE
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.pretix.pretixprint.BuildConfig
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.print.testPrint
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import eu.pretix.pretixprint.connections.IMinInternalConnection
import eu.pretix.pretixprint.connections.SunmiInternalConnection
import eu.pretix.pretixprint.connections.SystemConnection
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min


class SettingsFragment : PreferenceFragmentCompat() {
    val bgScope = CoroutineScope(Dispatchers.IO)
    lateinit var defaultSharedPreferences: SharedPreferences
    val types = listOf("ticket", "badge", "receipt")
    var pendingPinAction: ((pin: String) -> Unit)? = null

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

        findPreference<Preference>("version")?.summary = BuildConfig.VERSION_NAME

        childFragmentManager.setFragmentResultListener(PinDialog.RESULT_PIN, this) { _, bundle ->
            val pin = bundle.getString(PinDialog.RESULT_PIN)
            if (pin != null && defaultSharedPreferences.getString("pref_pin", "") == pin) {
                (childFragmentManager.findFragmentByTag(PinDialog.TAG) as? PinDialog)?.dismiss()
                pendingPinAction?.let { it(pin) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        for (type in types) {
            findPreference<PrinterPreference>("hardware_${type}printer_find")?.apply {
                if (!TextUtils.isEmpty(defaultSharedPreferences.getString("hardware_${type}printer_connection", ""))) {
                    moreVisibility = VISIBLE
                    summary = printerSummary(type)
                } else {
                    moreVisibility = GONE
                    summary = ""
                }
            }
        }

        val cpl = findPreference<ListPreference>("hardware_receiptprinter_cpl")
        if (cpl != null) {
            cpl.summary = if (cpl.entry.isNullOrEmpty()) {
                getString(R.string.pref_printer_cpl, cpl.entries[31])
            } else {
                getString(R.string.pref_printer_cpl, (cpl.entries.indexOf(cpl.entry) + 1).toString())
            }
        }
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
}

class SettingsActivity : AppCompatActivity() {

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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun applyRestrictions(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }
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
