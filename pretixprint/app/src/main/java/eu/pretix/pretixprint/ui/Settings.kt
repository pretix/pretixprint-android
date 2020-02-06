package eu.pretix.pretixprint.ui

import android.content.Intent
import android.os.Bundle
import android.preference.ListPreference
import android.preference.PreferenceFragment
import android.text.Html
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.widget.TextView
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import eu.pretix.pretixprint.BuildConfig
import eu.pretix.pretixprint.R
import org.jetbrains.anko.defaultSharedPreferences
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class SettingsFragment : PreferenceFragment() {
    val types = listOf("ticket", "badge", "receipt")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences)

        for (type in types) {
            findPreference("hardware_${type}printer_find").setOnPreferenceClickListener {
                val intent = Intent(activity, PrinterSetupActivity::class.java)
                intent.putExtra(FindPrinterActivity.EXTRA_TYPE, type)
                activity.startActivity(intent)
                return@setOnPreferenceClickListener true
            }
        }

        findPreference("hardware_receiptprinter_cpl").setOnPreferenceChangeListener { preference, newValue ->
            val cpl = findPreference("hardware_receiptprinter_cpl") as ListPreference
            findPreference("hardware_receiptprinter_cpl").summary = getString(R.string.pref_printer_cpl, newValue)
            return@setOnPreferenceChangeListener true
        }

        findPreference("licenses").setOnPreferenceClickListener {
            asset_dialog(R.raw.about, R.string.settings_label_licenses)
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
        findPreference("hardware_receiptprinter_cpl").summary = if (cpl.entry.isNullOrEmpty()) { getString(R.string.pref_printer_cpl, cpl.entries[31]) } else { getString(R.string.pref_printer_cpl, (cpl.entries.indexOf(cpl.entry) + 1).toString()) }
    }

    private fun asset_dialog(@RawRes htmlRes: Int, @StringRes title: Int) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_about, null, false)
        val dialog = AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(R.string.dismiss, null)
                .create()

        val textView = view.findViewById(R.id.aboutText) as TextView

        var text = ""

        val builder = StringBuilder()
        val fis: InputStream
        try {
            fis = resources.openRawResource(htmlRes)
            val reader = BufferedReader(InputStreamReader(fis, "utf-8"))
            while (true) {
                val line = reader.readLine()
                if (line != null) {
                    builder.append(line)
                } else {
                    break
                }
            }

            text = builder.toString()
            fis.close()
        } catch (e: IOException) {
            //Sentry.captureException(e)
            e.printStackTrace()
        }

        textView.text = Html.fromHtml(text)
        textView.movementMethod = LinkMovementMethod.getInstance()

        dialog.show()
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
