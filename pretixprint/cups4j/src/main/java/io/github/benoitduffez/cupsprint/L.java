package io.github.benoitduffez.cupsprint;

import android.util.Log;

import androidx.annotation.Nullable;


// TODO: Sentry integration

/**
 * Logging/crash reporting functions
 */
public class L {
    /**
     * Verbose log + crashlytics log
     *
     * @param msg Log message
     */
    public static void v(String msg) {
        Log.v("cups", msg);
    }

    /**
     * Info log + crashlytics log
     *
     * @param msg Log message
     */
    public static void i(String msg) {
        Log.i("cups", msg);
    }

    /**
     * Warning log + crashlytics log
     *
     * @param msg Log message
     */
    public static void w(String msg) {
        Log.w("cups", msg);
    }

    /**
     * Debug log + crashlytics log
     *
     * @param msg Log message
     */
    public static void d(String msg) {
        Log.d("cups", msg);
    }

    /**
     * Error log + crashlytics log
     *
     * @param msg Log message
     */
    public static void e(String msg) {
        Log.e("cups", msg);
    }

    /**
     * Error reporting + send exception to crashlytics
     *
     * @param msg Log message
     * @param t   Throwable to send to crashlytics, if not null
     */
    public static void e(String msg, @Nullable Throwable t) {
        e(msg);
        if (t != null) {
            e(t.getLocalizedMessage());
        }
    }
}
