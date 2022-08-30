package eu.pretix.pretixprint.byteprotocols

val protocols = listOf<ByteProtocolInterface<*>>(
        FGL(),
        SLCS(),
        ESCPOS(),
        ePOSPrintXML(),
        GraphicESCPOS(),
        GraphicePOSPrintXML(),
        BrotherRaster(),
        PNG()
)
