package eu.pretix.pretixprint

import android.app.Application
import android.content.Context
import com.facebook.flipper.core.FlipperClient


object FlipperInitializer {
    fun initFlipperPlugins(context: Context, client: FlipperClient) {
    }

    fun active(context: Context): Boolean {
        return false
    }
}