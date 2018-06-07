package eu.pretix.pretixprint

import android.support.multidex.MultiDexApplication
import com.facebook.stetho.Stetho
import eu.pretix.pretixprint.BuildConfig
import eu.pretix.pretixprint.print.Renderer

class PretixPrint : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this)
        }
        Renderer.registerFonts(this)
    }
}
