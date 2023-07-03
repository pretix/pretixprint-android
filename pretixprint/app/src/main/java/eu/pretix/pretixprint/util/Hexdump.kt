package eu.pretix.pretixprint.util


open class HexdumpByteArrayHolder(private val maxSize: Int = 16) {
    private val bytes: ByteArray = ByteArray(maxSize)
    private var elements = 0

    fun size(): Int {
        return elements
    }

    fun isFull(): Boolean {
        return elements == maxSize
    }

    fun same(other: HexdumpByteArrayHolder?): Boolean {
        return other != null && elements == other.elements && bytes.contentEquals(other.bytes)
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

class DirectedHexdumpByteArrayHolder(val direction: Direction, _maxSize: Int = 16):
    HexdumpByteArrayHolder(_maxSize) {

    enum class Direction {
        IN, OUT;
        override fun toString(): String = when(this) {
            IN -> "←"
            OUT -> "→"
        }
    }

    fun same(other: DirectedHexdumpByteArrayHolder?): Boolean {
        return other != null && direction == other.direction && super.same(other)
    }

    override fun toString(): String {
        return "${direction} ${toHex()} | ${toAscii()}"
    }
}

open class DirectedHexdumpCollection(val maxSize: Int = 16) {
    protected val collection = mutableListOf<DirectedHexdumpByteArrayHolder>()

    fun pushBytes(dir: DirectedHexdumpByteArrayHolder.Direction, ba: ByteArray, forceNew: Boolean = false) {
        var fN = forceNew
        ba.forEach {
            pushByte(dir, it, fN)
            fN = false
        }
    }

    open fun pushByte(dir: DirectedHexdumpByteArrayHolder.Direction, b: Byte, forceNew: Boolean = false) {
        if (collection.isEmpty() || forceNew) {
            collection.add(DirectedHexdumpByteArrayHolder(dir, maxSize))
        }
        if (collection.last().direction != dir) {
            collection.add(DirectedHexdumpByteArrayHolder(dir, maxSize))
        }
        if (collection.last().isFull()) {
            collection.add(DirectedHexdumpByteArrayHolder(dir, maxSize))
        }
        collection.last().push(b)
    }

    override fun toString(): String {
        return collection.joinToString("\n")
    }

    fun toList(): List<DirectedHexdumpByteArrayHolder> {
        return collection.toList()
    }
}