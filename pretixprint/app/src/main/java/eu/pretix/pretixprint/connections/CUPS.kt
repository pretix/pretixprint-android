package eu.pretix.pretixprint.connections

import eu.pretix.pretixprint.R


class CUPSConnection : ConnectionType {
    override val identifier = "cups"
    override val nameResource = R.string.connection_type_cups
    override val inputType = ConnectionType.Input.PDF

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }
}