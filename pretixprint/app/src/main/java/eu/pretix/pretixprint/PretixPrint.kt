package eu.pretix.pretixprint

import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import com.facebook.flipper.android.AndroidFlipperClient
import com.facebook.flipper.core.FlipperClient
import com.facebook.soloader.SoLoader
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader
import eu.pretix.pretixprint.print.WYSIWYGRenderer

class PretixPrint : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()

        WYSIWYGRenderer.registerFonts(this)
        PDFBoxResourceLoader.init(getApplicationContext())

        SoLoader.init(this, false)
        if (BuildConfig.DEBUG && FlipperInitializer.active(this)) {
            val client: FlipperClient = AndroidFlipperClient.getInstance(this)
            FlipperInitializer.initFlipperPlugins(this, client)
        }

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
