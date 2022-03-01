package eu.pretix.pretixprint.byteprotocols

val protocols = listOf<ByteProtocolInterface<*>>(
        FGL(),
        SLCS(),
        ESCPOS(),
        GraphicESCPOS(),
        BrotherRaster(),
        LinkOSCard(),
        LinkOS()
)
