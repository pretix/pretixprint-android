package eu.pretix.pretixprint.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import eu.pretix.pretixprint.PrintException
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.BluetoothConnection
import eu.pretix.pretixprint.connections.CUPSConnection
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.connections.USBConnection
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.ctx
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

    fun writeDemoTxt(): File {
        val file = File(ctx.cacheDir, "demopage.txt")
        if (file.exists()) {
            file.delete()
        }
        val asset = ctx.assets.open("demopage.txt")
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
            val file = when (activity.useCase) {
                "receipt" -> writeDemoTxt()
                else -> writeDemoPdf()
            }
            when (activity.mode()) {
                NetworkConnection().identifier -> {
                    doAsync {
                        NetworkConnection().print(file, 1, activity!!, activity.useCase, activity.settingsStagingArea)
                    }
                }
                BluetoothConnection().identifier -> {
                    doAsync {
                        BluetoothConnection().print(file, 1, activity!!, activity.useCase, activity.settingsStagingArea)
                    }
                }
                CUPSConnection().identifier -> {
                    doAsync {
                        CUPSConnection().print(file, 1, activity!!, activity.useCase, activity.settingsStagingArea)
                    }
                }
                USBConnection().identifier -> {
                    doAsync {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            USBConnection().print(file, 1, activity!!, activity.useCase, activity.settingsStagingArea)
                        } else {
                            throw Exception("USB not supported on this Android version.")
                        }
                    }
                }
            }

            // TODO: Better decision, what kind of demopage we're sending
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
