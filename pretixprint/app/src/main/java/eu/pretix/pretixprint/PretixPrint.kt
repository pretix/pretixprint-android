package eu.pretix.pretixprint

import androidx.multidex.MultiDexApplication
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader
import eu.pretix.pretixprint.print.WYSIWYGRenderer

class PretixPrint : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()

        WYSIWYGRenderer.registerFonts(this)
        PDFBoxResourceLoader.init(getApplicationContext())
    }
}
