package eu.pretix.pretixprint.connections

val connectionTypes = listOf<ConnectionType>(
        NetworkConnection(),
        BluetoothConnection(),
        USBConnection(),
        CUPSConnection()
)
