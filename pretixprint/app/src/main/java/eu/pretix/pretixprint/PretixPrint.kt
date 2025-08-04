package eu.pretix.pretixprint

import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import eu.pretix.pretixprint.print.WYSIWYGRenderer

class PretixPrint : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()

        WYSIWYGRenderer.registerFonts(this)

        migrateSettings()
    }

    private fun migrateSettings() {
        for (useCase in listOf("ticket", "badge")) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val v = prefs.getString("hardware_${useCase}printer_rotate90", "false")
            if (!v.isNullOrBlank() && v != "false") {
                prefs.edit()
                    .putString("hardware_${useCase}printer_rotation", "90")
                    .remove("hardware_${useCase}printer_rotate90")
                    .apply()
            }
        }
    }
}
