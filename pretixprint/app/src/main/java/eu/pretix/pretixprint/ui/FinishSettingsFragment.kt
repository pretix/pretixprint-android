package eu.pretix.pretixprint.ui

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.NetworkConnection
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.defaultSharedPreferences
import java.io.File
import java.io.FileOutputStream

class FinishSettingsFragment : SetupFragment() {

    fun writeDemoPdf(): File {
        val file = File(ctx.cacheDir, "demopage.pdf")
        if (file.exists()) {
            file.delete()
        }
        val asset = ctx.assets.open("demopage_8in_3.25in.pdf")
        val output = FileOutputStream(file)
        val buffer = ByteArray(1024)
        var size = asset.read(buffer)
        while (size != -1) {
            output.write(buffer, 0, size)
            size = asset.read(buffer)
        }
        asset.close()
        output.close()
        return file
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_finish_settings, container, false)
        val activity = activity as PrinterSetupActivity

        view.findViewById<Button>(R.id.btnTestPage).setOnClickListener {
            if (activity.mode() == NetworkConnection().identifier) {
                doAsync {
                    NetworkConnection().print(writeDemoPdf(), 1, activity!!, activity.useCase, activity.settingsStagingArea)
                }
            }
            // TODO: progress bar
            // TODO: other connections
        }
        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            activity.startProtocolSettings(true)
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            activity.save()
            activity.finish()
        }

        return view
    }
}
