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
import kotlinx.coroutines.experimental.withTimeout
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.support.v4.alert
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.indeterminateProgressDialog
import org.jetbrains.anko.support.v4.progressDialog
import org.jetbrains.anko.uiThread
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
        val activity = activity as PrinterSetupActivity
        val view = inflater.inflate(R.layout.fragment_finish_settings, container, false)

        view.findViewById<Button>(R.id.btnTestPage).setOnClickListener {
            val pb = indeterminateProgressDialog(R.string.testing)
            pb.setCancelable(true)
            pb.show()
            doAsync {
                try {
                    testprint()
                    uiThread {
                        if (this@FinishSettingsFragment.activity == null)
                            return@uiThread
                        alert(R.string.test_success).show()
                    }
                } catch (e: PrintException) {
                    uiThread {
                        if (this@FinishSettingsFragment.activity == null)
                            return@uiThread
                        alert(e.message).show()
                    }
                } finally {
                    uiThread {
                        if (this@FinishSettingsFragment.activity == null)
                            return@uiThread
                        pb.dismiss()
                    }
                }

            }
        }
        view.findViewById<Button>(R.id.btnPrev).setOnClickListener {
            back()
        }
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            activity.save()
            activity.finish()
        }

        return view
    }

    override fun back() {
        (activity as PrinterSetupActivity).startProtocolSettings(true)
    }

    fun testprint() {
        val activity = activity as PrinterSetupActivity
        val file = when (activity.useCase) {
            "receipt" -> writeDemoTxt()
            else -> writeDemoPdf()
        }
        when (activity.mode()) {
            NetworkConnection().identifier -> {
                NetworkConnection().print(file, 1, activity!!, activity.useCase, activity.settingsStagingArea)
            }
            BluetoothConnection().identifier -> {
                BluetoothConnection().print(file, 1, activity!!, activity.useCase, activity.settingsStagingArea)
            }
            CUPSConnection().identifier -> {
                CUPSConnection().print(file, 1, activity!!, activity.useCase, activity.settingsStagingArea)
            }
            USBConnection().identifier -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    USBConnection().print(file, 1, activity!!, activity.useCase, activity.settingsStagingArea)
                } else {
                    throw Exception("USB not supported on this Android version.")
                }
            }
        }
    }
}
