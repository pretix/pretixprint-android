package eu.pretix.pretixprint

import androidx.multidex.MultiDexApplication
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader
import eu.pretix.pretixprint.print.WYSIWYGRenderer
import org.jetbrains.anko.defaultSharedPreferences

class PretixPrint : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()

        WYSIWYGRenderer.registerFonts(this)
        PDFBoxResourceLoader.init(getApplicationContext())
        migrateSettings()
    }

    private fun migrateSettings() {
        for (useCase in listOf("ticket", "badge")) {
            val v = defaultSharedPreferences.getString("hardware_${useCase}printer_rotate90", "false")
            if (!v.isNullOrBlank() && v != "false") {
                defaultSharedPreferences.edit()
                    .putString("hardware_${useCase}printer_rotation", "90")
                    .remove("hardware_${useCase}printer_rotate90")
                    .apply()
            }
        }
    }
}
