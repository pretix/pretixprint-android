package eu.pretix.pretixprint

enum class Rotation(val degrees: Int) {
    d0(degrees = 0),
    d90(degrees = 90),
    d180(degrees = 180),
    d270(degrees = 270);

    override fun toString(): String {
        return "$degrees °"
    }
}