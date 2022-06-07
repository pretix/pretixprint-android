package eu.pretix.pretixprint

import androidx.multidex.MultiDexApplication
import com.facebook.stetho.Stetho
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader
import eu.pretix.pretixprint.print.WYSIWYGRenderer

class PretixPrint : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this)
        }
        WYSIWYGRenderer.registerFonts(this)
        PDFBoxResourceLoader.init(getApplicationContext());
    }
}
