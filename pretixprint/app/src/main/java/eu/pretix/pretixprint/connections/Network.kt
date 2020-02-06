package eu.pretix.pretixprint.connections

import eu.pretix.pretixprint.R


class NetworkConnection : ConnectionType {
    override val identifier = "network_printer"
    override val nameResource = R.string.connection_type_network
    override val inputType = ConnectionType.Input.PLAIN_BYTES

    override fun allowedForUsecase(type: String): Boolean {
        return true
    }
}