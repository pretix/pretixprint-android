package eu.pretix.pretixprint.byteprotocols

import eu.pretix.pretixprint.R


class SLCS : ByteProtocol {
    override val identifier = "SLCS"
    override val nameResource = R.string.protocol_slcs

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }
}