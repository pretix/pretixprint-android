package eu.pretix.pretixprint.byteprotocols

val protocols = listOf<ByteProtocolInterface<*>>(
        FGL(),
        SLCS(),
        ESCPOS(),
        ePOSPrintXML(),
        StarPRNT(),
        GraphicESCPOS(),
        GraphicePOSPrintXML(),
        BrotherSDK(),
        BrotherRaster(),
        EvolisDirect(),
        PNG(),
        LinkOSCard(),
        LinkOS()
)
