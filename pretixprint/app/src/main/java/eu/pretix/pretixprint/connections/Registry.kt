package eu.pretix.pretixprint.connections

val connectionTypes = listOf<ConnectionType>(
        IMinInternalConnection(),
        SunmiInternalConnection(),
        NetworkConnection(),
        BluetoothConnection(),
        USBConnection(),
        CUPSConnection(),
        SystemConnection(),
)

fun getConnectionClass(type: String): ConnectionType? {
        return connectionTypes.find { it.identifier == type }
}