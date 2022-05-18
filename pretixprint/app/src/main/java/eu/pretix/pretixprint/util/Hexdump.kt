package eu.pretix.pretixprint.util


class HexdumpByteArrayHolder(val maxSize: Int = 16) {
    private val bytes: ByteArray = ByteArray(maxSize)
    private var elements = 0

    fun size(): Int {
        return elements
    }

    fun isFull(): Boolean {
        return elements == maxSize
    }

    fun push(b: Byte) {
        if (elements >= maxSize) throw IndexOutOfBoundsException("full")
        bytes[elements++] = b
    }

    fun toHex(): String = buildString {
        repeat(maxSize) {
            if (it >= elements) append("  ")
            else append("%02x".format(bytes[it]))
            if (it < maxSize - 1) append(" ")
        }
    }

    fun toAscii(): String = buildString {
        repeat(maxSize) {
            if (it >= elements) append(" ")
            else append(Char(bytes[it].toUShort()).toString().replace(Regex("\\p{C}"), "."))
        }
    }

    override fun toString(): String {
        return "${toHex()} | ${toAscii()}"
    }
}

class HexdumpCollection(val maxSize: Int = 16) {
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
            collection.add(HexdumpByteArrayHolder(maxSize))
        }
        if (collection.last().isFull()) {
            collection.add(HexdumpByteArrayHolder(maxSize))
        }
        collection.last().push(b)
    }

    override fun toString(): String {
        return collection.joinToString("\n") { it.toString() }
    }
}

class DirectedHexdumpCollection(val maxSize: Int = 16) {
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
            collection.add(Pair(dir, HexdumpCollection(maxSize)))
        }
        if (collection.last().first != dir) {
            collection.add(Pair(dir, HexdumpCollection(maxSize)))
        }
        collection.last().second.pushByte(b)
    }

    override fun toString(): String {
        return collection.joinToString("\n") {
            "${it.first} ${it.second.toString().replace("\n", "\n  ")}"
        }
    }
}