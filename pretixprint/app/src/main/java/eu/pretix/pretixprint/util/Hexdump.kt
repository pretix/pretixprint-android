package eu.pretix.pretixprint.util


class HexdumpByteArrayHolder {
    companion object {
        const val MAX_ITEMS = 16
    }
    private val bytes: ByteArray = ByteArray(MAX_ITEMS)
    private var elements = 0

    fun size(): Int {
        return elements
    }

    fun push(b: Byte) {
        if (elements >= MAX_ITEMS) throw IndexOutOfBoundsException("full")
        bytes[elements++] = b
    }

    fun toHex(): String = buildString {
        repeat(MAX_ITEMS) {
            if (it >= elements) append("  ")
            else append("%02x".format(bytes[it]))
            if (it < MAX_ITEMS - 1) append(" ")
        }
    }

    fun toAscii(): String = buildString {
        repeat(MAX_ITEMS) {
            if (it >= elements) append(" ")
            else append(Char(bytes[it].toUShort()).toString().replace(Regex("\\p{C}"), "."))
        }
    }

    override fun toString(): String {
        return "${toHex()} | ${toAscii()}"
    }
}

class HexdumpCollection {
    private val collection = mutableListOf<HexdumpByteArrayHolder>()

    fun pushBytes(ba: ByteArray, forceNew: Boolean = false) {
        var fN = forceNew
        ba.forEach {
            pushByte(it, fN)
            fN = false
        }
    }

    fun pushByte(b: Byte, forceNew: Boolean = false) {
        if (collection.isEmpty() || forceNew) {
            collection.add(HexdumpByteArrayHolder())
        }
        if (collection.last().size() == HexdumpByteArrayHolder.MAX_ITEMS) {
            collection.add(HexdumpByteArrayHolder())
        }
        collection.last().push(b)
    }

    override fun toString(): String {
        return collection.joinToString("\n") { it.toString() }
    }
}

class DirectedHexdumpCollection {
    enum class Direction {
        IN, OUT;
        override fun toString(): String = when(this) {
            IN -> "←"
            OUT -> "→"
        }
    }
    private val collection = mutableListOf<Pair<Direction, HexdumpCollection>>()

    fun pushBytes(dir: Direction, ba: ByteArray, forceNew: Boolean = false) {
        var fN = forceNew
        ba.forEach {
            pushByte(dir, it, fN)
            fN = false
        }
    }

    fun pushByte(dir: Direction, b: Byte, forceNew: Boolean = false) {
        if (collection.isEmpty() || forceNew) {
            collection.add(Pair(dir, HexdumpCollection()))
        }
        if (collection.last().first != dir) {
            collection.add(Pair(dir, HexdumpCollection()))
        }
        collection.last().second.pushByte(b)
    }

    override fun toString(): String {
        return collection.joinToString("\n") {
            "${it.first} ${it.second.toString().replace("\n", "\n  ")}"
        }
    }
}