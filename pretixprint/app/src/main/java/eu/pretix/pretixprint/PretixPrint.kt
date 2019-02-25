package eu.pretix.pretixprint

import androidx.multidex.MultiDexApplication
import com.facebook.stetho.Stetho
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader
import eu.pretix.pretixprint.print.WYSIWYGRenderer
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory

class PretixPrint : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this)
        }
        if (BuildConfig.SENTRY_DSN != null) {
            val sentryDsn = BuildConfig.SENTRY_DSN
            Sentry.init(sentryDsn, AndroidSentryClientFactory(this))
        }
        WYSIWYGRenderer.registerFonts(this)
        PDFBoxResourceLoader.init(getApplicationContext());
    }
}
