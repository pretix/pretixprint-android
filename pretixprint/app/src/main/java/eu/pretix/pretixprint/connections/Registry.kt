package eu.pretix.pretixprint.connections

val connectionTypes = listOf<ConnectionType>(
        SunmiInternalConnection(),
        NetworkConnection(),
        BluetoothConnection(),
        USBConnection(),
        CUPSConnection(),
        SystemConnection(),
)
