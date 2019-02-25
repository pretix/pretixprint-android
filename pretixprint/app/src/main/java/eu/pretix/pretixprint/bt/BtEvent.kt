package eu.pretix.pretixprint.bt

data class BtEvent(
        val playerId: Int,
        val afterSignal: Boolean,
        val elapsedTime: Int)