package eu.pretix.pretixprint.connections

import eu.pretix.pretixprint.R


class BluetoothConnection : ConnectionType {
    override val identifier = "bluetooth_printer"
    override val nameResource = R.string.connection_type_bluetooth
    override val inputType = ConnectionType.Input.PLAIN_BYTES

    override fun allowedForUsecase(type: String): Boolean {
        return true
    }
}