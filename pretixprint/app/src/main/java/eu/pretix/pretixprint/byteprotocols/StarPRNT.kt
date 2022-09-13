package eu.pretix.pretixprint.byteprotocols

import com.zebra.sdk.comm.BluetoothConnection
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.ConnectionType
import eu.pretix.pretixprint.connections.NetworkConnection
import eu.pretix.pretixprint.connections.USBConnection
import eu.pretix.pretixprint.ui.SetupFragment

class StarPRNT : ESCPOS() {
    override val identifier = "StarPRNT"
    override val nameResource = R.string.protocol_starprnt

    override fun createSettingsFragment(): SetupFragment? {
        return null
    }

    override fun allowedForConnection(type: ConnectionType): Boolean {
        return type is USBConnection || type is NetworkConnection || type is BluetoothConnection
    }
}