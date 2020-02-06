package eu.pretix.pretixprint.byteprotocols

import eu.pretix.pretixprint.R


class FGL : ByteProtocol {
    override val identifier = "FGL"
    override val nameResource = R.string.protocol_fgl

    override fun allowedForUsecase(type: String): Boolean {
        return type != "receipt"
    }
}