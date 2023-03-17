package eu.pretix.pretixprint

import android.app.Application
import android.content.Context
import com.facebook.flipper.android.utils.FlipperUtils
import com.facebook.flipper.core.FlipperClient
import com.facebook.flipper.plugins.crashreporter.CrashReporterPlugin
import com.facebook.flipper.plugins.inspector.DescriptorMapping
import com.facebook.flipper.plugins.inspector.InspectorFlipperPlugin
import com.facebook.flipper.plugins.network.NetworkFlipperPlugin
import com.facebook.flipper.plugins.sharedpreferences.SharedPreferencesFlipperPlugin
import com.facebook.flipper.plugins.uidebugger.UIDebuggerFlipperPlugin
import com.facebook.flipper.plugins.uidebugger.core.UIDContext


object FlipperInitializer {
    fun initFlipperPlugins(context: Context, client: FlipperClient) {
        val descriptorMapping = DescriptorMapping.withDefaults()
        client.addPlugin(InspectorFlipperPlugin(context, descriptorMapping))
        client.addPlugin(UIDebuggerFlipperPlugin(UIDContext.create(context as Application)))
        client.addPlugin(NetworkFlipperPlugin())
        client.addPlugin(CrashReporterPlugin.getInstance())
        client.addPlugin(SharedPreferencesFlipperPlugin(context))
        client.start()
    }

    fun active(context: Context): Boolean {
        return FlipperUtils.shouldEnableFlipper(context)
    }
}