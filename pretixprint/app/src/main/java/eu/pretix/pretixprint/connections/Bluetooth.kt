package eu.pretix.pretixprint.connections

import android.content.Context
import eu.pretix.pretixprint.R
import java.io.File


class BluetoothConnection : ConnectionType {
    override val identifier = "bluetooth_printer"
    override val nameResource = R.string.connection_type_bluetooth
    override val inputType = ConnectionType.Input.PLAIN_BYTES

    override fun allowedForUsecase(type: String): Boolean {
        return true
    }

    override fun print(tmpfile: File, numPages: Int, context: Context, type: String, settings: Map<String, String>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}