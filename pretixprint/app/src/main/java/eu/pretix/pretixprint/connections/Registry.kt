package eu.pretix.pretixprint.connections

val connectionTypes = listOf<ConnectionType>(
        IMinInternalConnection(),
        SunmiInternalConnection(),
        NetworkConnection(),
        BluetoothConnection(),
        USBConnection(),
        CUPSConnection(),
        SystemConnection(),
        EFTTerminalConnection(),
)
