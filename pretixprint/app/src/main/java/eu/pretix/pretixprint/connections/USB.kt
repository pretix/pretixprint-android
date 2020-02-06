package eu.pretix.pretixprint.connections

import eu.pretix.pretixprint.R


class USBConnection : ConnectionType {
    override val identifier = "usb"
    override val nameResource = R.string.connection_type_usb
    override val inputType = ConnectionType.Input.PLAIN_BYTES

    override fun allowedForUsecase(type: String): Boolean {
        return true
    }
}