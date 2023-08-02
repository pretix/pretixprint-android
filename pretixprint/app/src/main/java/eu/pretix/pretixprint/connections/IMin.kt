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
import java.io.File


class IMinInternalConnection : USBConnection() {
    override val identifier = "imin"
    override val nameResource = R.string.connection_type_imin

    override fun allowedForUsecase(type: String): Boolean {
        return type == "receipt" && Build.BRAND.uppercase() == "IMIN"
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun print(tmpfile: File, numPages: Int, context: Context, type: String, settings: Map<String, String>?) {
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

        super.print(tmpfile, numPages, context, type, settings)

        // always open cash drawer after print
        if (iNeostraInterfaces != null) {
            iNeostraInterfaces?.openCashbox()
        }
    }

}