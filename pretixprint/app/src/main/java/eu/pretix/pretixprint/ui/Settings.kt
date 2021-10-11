package eu.pretix.pretixprint.ui

import android.content.Intent
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
import java.lang.RuntimeException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min


class SettingsFragment : PreferenceFragment() {
    val types = listOf("ticket", "badge", "receipt")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences)

        for (type in types) {
            findPreference("hardware_${type}printer_find").setOnPreferenceClickListener {
                val intent = Intent(activity, PrinterSetupActivity::class.java)
                intent.putExtra(PrinterSetupActivity.EXTRA_USECASE, type)
                activity.startActivity(intent)
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

        findPreference("version").summary = BuildConfig.VERSION_NAME
    }

    override fun onResume() {
        super.onResume()
        for (type in types) {
            if (!TextUtils.isEmpty(defaultSharedPreferences.getString("hardware_${type}printer_ip", ""))) {
                val ip = defaultSharedPreferences.getString("hardware_${type}printer_ip", "")
                val name = defaultSharedPreferences.getString("hardware_${type}printer_printername", "")
                val connection = defaultSharedPreferences.getString("hardware_${type}printer_connection", "network_printer")

                findPreference("hardware_${type}printer_find").summary = getString(
                        R.string.pref_printer_current, name, ip, getString(resources.getIdentifier(connection, "string", activity.packageName))
                )
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
                .listFiles { file, s -> s.startsWith("print_") }!!
                .toList()
                .sortedByDescending { it.lastModified() }
        val names = files
                .subList(0, min(files.size, 10))
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
                "pdf" -> {
                    val intent = intentFor<FileViewerPdfActivity>()
                    intent.putExtra(FileViewerEscposActivity.EXTRA_PATH, file.absolutePath)
                    activity!!.startActivity(intent)
                }
                else -> throw RuntimeException("Unknown file type for file $file")
            }

        }
    }
}

class SettingsActivity : AppCompatPreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
}
