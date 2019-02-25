package eu.pretix.pretixprint.bt

enum class BtState(val value: Byte) {
    Idle(1),
    Set(2),
    Locked(3)
}

enum class State {
    Initial,
    Idle,
    Paired,
    Setup
}