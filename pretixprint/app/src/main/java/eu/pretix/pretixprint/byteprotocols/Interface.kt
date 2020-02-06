package eu.pretix.pretixprint.byteprotocols

interface ByteProtocol {
    val identifier: String
    val nameResource: Int

    fun allowedForUsecase(type: String): Boolean
}