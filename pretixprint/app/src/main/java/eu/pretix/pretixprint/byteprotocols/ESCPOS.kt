package eu.pretix.pretixprint.byteprotocols

import eu.pretix.pretixprint.R


class ESCPOS : ByteProtocol {
    override val identifier = "ESC/POS"
    override val nameResource = R.string.protocol_escpos

    override fun allowedForUsecase(type: String): Boolean {
        return type == "receipt"
    }
}