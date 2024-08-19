package eu.pretix.pretixprint.connections

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.neostra.interfaces.INeostraInterfaces
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.byteprotocols.*
import eu.pretix.pretixprint.print.ESCPOSRenderer
import java.io.File
import java.io.IOException


class IMinInternalConnection : USBConnection() {
    override val identifier = "imin"
    override val nameResource = R.string.connection_type_imin

    override fun allowedForUsecase(type: String): Boolean {
        return Build.BRAND.uppercase() == "IMIN"
    }

    override fun print(tmpfile: File, numPages: Int, pagegroups: List<Int>, context: Context, type: String, settings: Map<String, String>?) {
        var iNeostraInterfaces: INeostraInterfaces? = null
        val conn = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                iNeostraInterfaces = INeostraInterfaces.Stub.asInterface(iBinder)
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                iNeostraInterfaces = null
            }
        }
        val intent = Intent()
        intent.action = "com.neostra.interfaces.NeostraInterfacesService"
        intent.component = ComponentName(
            "com.neostra.interfaces",
            "com.neostra.interfaces.NeostraInterfacesService"
        )
        val result: Boolean = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        if (!result) {
            System.err.println("cannot bind to INeostraInterfaces service")
        }

        var shouldOpenCashDrawer = false
        val reader = tmpfile.reader()
        while (true) {
            try {
                val char = reader.read()
                if (char == -1) {
                    break
                }
                if (char.toByte() == ESCPOSRenderer.ESC) {
                    if (reader.read().toChar() == 'p') {
                        // that's a openCashDrawer command
                        shouldOpenCashDrawer = true
                        break
                    }
                }
            } catch (e: IOException) {
                break
            }
        }
        if (shouldOpenCashDrawer) {
            iNeostraInterfaces?.openCashbox()
        }

        super.print(tmpfile, numPages, pagegroups, context, type, settings)
    }

}