package eu.pretix.pretixprint.connections

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import eu.pretix.pretixprint.BuildConfig
import eu.pretix.pretixprint.R
import java.io.File

const val FILEPROVIDERAUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider"
class EFTTerminalConnection : ConnectionType {
    override val identifier = "eftterminal"
    override val nameResource = R.string.connection_type_eftterminal
    override val inputType = ConnectionType.Input.PLAIN_BYTES

    override fun allowedForUsecase(type: String): Boolean {
        return type == "receipt"
    }

    override fun isConfiguredFor(context: Context, type: String): Boolean {
        return true
    }

    override fun print(tmpfile: File, numPages: Int, pagegroups: List<Int>, context: Context, useCase: String, settings: Map<String, String>?) {
        val intent = Intent()
        if (BuildConfig.DEBUG) {
            intent.`package` = "eu.pretix.pretixpos.debug"
        } else {
            intent.`package` = "eu.pretix.pretixpos"
        }

        intent.action = "eu.pretix.pretixpos.print.PRINT_ON_EFTTERMINAL"

        BuildConfig.APPLICATION_ID

        val dataUri = FileProvider.getUriForFile(
                context,
                FILEPROVIDERAUTHORITY,
                tmpfile)
        context.grantUriPermission(intent.`package`, dataUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

        intent.clipData = ClipData.newRawUri(null, dataUri)

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)


        if (isPackageInstalled("eu.pretix.pretixpos.debug", context.packageManager, 20) && BuildConfig.DEBUG) {
            intent.component = ComponentName("eu.pretix.pretixpos.debug", "eu.pretix.pretixpos.hardware.EFTTerminalPrintService")
        } else if (isPackageInstalled("eu.pretix.pretixpos", context.packageManager, 20)) {
            intent.component = ComponentName("eu.pretix.pretixpos", "eu.pretix.pretixpos.hardware.EFTTerminalPrintService")
        } else if (isPackageInstalled("eu.pretix.pretixpos.debug", context.packageManager, 20)) {
            intent.component = ComponentName("eu.pretix.pretixpos.debug", "eu.pretix.pretixpos.hardware.EFTTerminalPrintService")
        } else if (isPackageInstalled("eu.pretix.pretixpos", context.packageManager)) {
            intent.component = ComponentName("eu.pretix.pretixpos", "eu.pretix.pretixpos.hardware.EFTTerminalPrintService")
        } else if (isPackageInstalled("eu.pretix.pretixpos.debug", context.packageManager)) {
            intent.component = ComponentName("eu.pretix.pretixpos.debug", "eu.pretix.pretixpos.hardware.EFTTerminalPrintService")
        } else {
            //throw Exception(context.getString(R.string.error_print_no_app))
            throw Exception("pretixPOS not installed")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun isPackageInstalled(packagename: String, packageManager: PackageManager, minVersion: Int? = null): Boolean {
        try {
            val pi = packageManager.getPackageInfo(packagename, 0)
            if (minVersion != null && pi.versionCode < minVersion) {
                return false
            }
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
    }
}
