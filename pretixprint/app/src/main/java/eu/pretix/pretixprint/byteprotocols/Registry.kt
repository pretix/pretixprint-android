package eu.pretix.pretixprint.byteprotocols

import eu.pretix.pretixprint.connections.*

val protocols = listOf<ByteProtocol<*>>(
        FGL(),
        SLCS(),
        ESCPOS()
)
