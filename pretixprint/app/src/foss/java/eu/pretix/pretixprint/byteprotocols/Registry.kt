package eu.pretix.pretixprint.byteprotocols

val protocols = listOf<ByteProtocolInterface<*>>(
        FGL(),
        SLCS(),
        ESCPOS(),
        ePOSPrintXML(),
        StarPRNT(),
        GraphicESCPOS(),
        GraphicePOSPrintXML(),
        BrotherRaster(),
        PNG(),
        ESCLabel(),
        TSPL(),
)
