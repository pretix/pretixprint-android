package eu.pretix.pretixprint.ui


import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceFragment
import android.text.TextUtils
import eu.pretix.pretixprint.R
import org.jetbrains.anko.defaultSharedPreferences


class SettingsFragment : PreferenceFragment() {
    val types = listOf("ticket", "badge")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences)

        for (type in types) {
            findPreference("hardware_${type}printer_find").setOnPreferenceClickListener {
                val intent = Intent(activity, FindPrinterActivity::class.java)
                intent.putExtra(FindPrinterActivity.EXTRA_TYPE, type)
                activity.startActivity(intent)
                return@setOnPreferenceClickListener true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        for (type in types) {
            if (!TextUtils.isEmpty(defaultSharedPreferences.getString("hardware_${type}printer_ip", ""))) {
                val ip = defaultSharedPreferences.getString("hardware_${type}printer_ip", "")
                val name = defaultSharedPreferences.getString("hardware_${type}printer_printername", "")
                findPreference("hardware_${type}printer_find").summary = getString(
                        R.string.pref_printer_current, name, ip
                )
            } else {
                findPreference("hardware_${type}printer_find").summary = ""
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

