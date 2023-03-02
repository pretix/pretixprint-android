package eu.pretix.pretixprint.ui

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.RestrictionsManager
import android.os.Build
import android.os.Bundle
import android.preference.ListPreference
import android.preference.PreferenceFragment
import android.text.TextUtils
import android.view.LayoutInflater
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import eu.pretix.pretixprint.BuildConfig
import eu.pretix.pretixprint.R
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.selector
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min


class SettingsFragment : ChecksPinFragment, PreferenceFragment() {
    val types = listOf("ticket", "badge", "receipt")
    var pendingPinAction: ((pin: String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences)

        for (type in types) {
            findPreference("hardware_${type}printer_find").setOnPreferenceClickListener {
                val intent = Intent(activity, PrinterSetupActivity::class.java)
                intent.putExtra(PrinterSetupActivity.EXTRA_USECASE, type)
                startWithPIN(intent)
                return@setOnPreferenceClickListener true
            }
        }

        findPreference("hardware_receiptprinter_cpl").setOnPreferenceChangeListener { preference, newValue ->
            val cpl = findPreference("hardware_receiptprinter_cpl") as ListPreference
            findPreference("hardware_receiptprinter_cpl").summary = getString(R.string.pref_printer_cpl, newValue)
            return@setOnPreferenceChangeListener true
        }

        findPreference("last_prints").setOnPreferenceClickListener {
            show_last_prints()
            return@setOnPreferenceClickListener true
        }

        findPreference("licenses").setOnPreferenceClickListener {
            asset_dialog(R.string.settings_label_licenses)
            return@setOnPreferenceClickListener true
        }

        (findPreference("hardware_receiptprinter_cpl") as ProtectedListPreference).setEarlyPreferenceClickListener { pref ->
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

        (findPreference("pref_pin") as ProtectedEditTextPreference).setEarlyPreferenceClickListener { pref ->
            if (!hasPin()) {
                // false: handle normally
                return@setEarlyPreferenceClickListener false
            }
            pinProtect {
                (pref as ProtectedEditTextPreference).showDialog()
            }
            return@setEarlyPreferenceClickListener true
        }

        findPreference("version").summary = BuildConfig.VERSION_NAME
    }

    override fun onResume() {
        super.onResume()
        for (type in types) {
            val connection = defaultSharedPreferences.getString("hardware_${type}printer_connection", "network_printer")

            if (!TextUtils.isEmpty(defaultSharedPreferences.getString("hardware_${type}printer_ip", ""))) {
                val ip = defaultSharedPreferences.getString("hardware_${type}printer_ip", "")
                val name = defaultSharedPreferences.getString("hardware_${type}printer_printername", "")

                findPreference("hardware_${type}printer_find").summary = getString(
                        R.string.pref_printer_current, name, ip, getString(resources.getIdentifier(connection, "string", activity.packageName))
                )
            } else if (!TextUtils.isEmpty(defaultSharedPreferences.getString("hardware_${type}printer_connection", ""))) {
                findPreference("hardware_${type}printer_find").summary = getString(R.string.pref_printer_current_short,
                    getString(resources.getIdentifier(connection, "string", activity.packageName)))
            } else {
                findPreference("hardware_${type}printer_find").summary = ""
            }
        }

        val cpl = findPreference("hardware_receiptprinter_cpl") as ListPreference
        findPreference("hardware_receiptprinter_cpl").summary = if (cpl.entry.isNullOrEmpty()) {
            getString(R.string.pref_printer_cpl, cpl.entries[31])
        } else {
            getString(R.string.pref_printer_cpl, (cpl.entries.indexOf(cpl.entry) + 1).toString())
        }
    }

    private fun asset_dialog(@StringRes title: Int) {

        val webView = WebView(activity!!)
        webView.loadUrl("file:///android_asset/about.html")

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_about, null, false)
        val dialog = AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(webView)
                .setPositiveButton(R.string.dismiss, null)
                .create()

        dialog.show()
    }

    fun show_last_prints() {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val files = activity!!.cacheDir
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

        selector(getString(R.string.settings_label_last_prints), names) { dialogInterface, i ->
            val file = files[i]
            val extension = file.name.split(".")[1]
            when (extension) {
                "escpos" -> {
                    val intent = intentFor<FileViewerEscposActivity>()
                    intent.putExtra(FileViewerEscposActivity.EXTRA_PATH, file.absolutePath)
                    activity!!.startActivity(intent)
                }
                "log" -> {
                    val intent = intentFor<FileViewerLogActivity>()
                    intent.putExtra(FileViewerLogActivity.EXTRA_PATH, file.absolutePath)
                    activity!!.startActivity(intent)
                }
                "pdf" -> {
                    val intent = intentFor<FileViewerPdfActivity>()
                    intent.putExtra(FileViewerPdfActivity.EXTRA_PATH, file.absolutePath)
                    activity!!.startActivity(intent)
                }
                else -> throw RuntimeException("Unknown file type for file $file")
            }

        }
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
        PinDialog().show(childFragmentManager, PinDialog.TAG)
    }

    override fun checkPin(pin: String) {
        if (defaultSharedPreferences.getString("pref_pin", "") == pin) {
            (childFragmentManager.findFragmentByTag(PinDialog.TAG) as? PinDialog)?.dismiss()
            pendingPinAction?.let { it(pin) }
        }
    }

    fun startWithPIN(intent: Intent) {
        pinProtect { pin ->
            intent.putExtra("pin", pin)
            startActivity(intent)
        }
    }
}

class SettingsActivity : AppCompatPreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyRestrictions(this)
        if (!defaultSharedPreferences.contains("first_start")) {
            defaultSharedPreferences.edit().putBoolean("first_start", true).apply();
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK.or(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            startActivity(intent)
            finish()
        }

        // Display the fragment as the main content.
        fragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
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
            defaultSharedPreferences.edit().putString("pref_pin", restrictions.getString("pref_pin")).apply()
        }
    }
}
