package eu.pretix.pretixprint.print

import android.content.Context
import android.util.Log
import org.json.JSONArray

class ESCPOSRenderer(private val layout: JSONArray, private val order: JSONArray, private val ctx: Context) {
    private val out = mutableListOf<Byte>()
    companion object {
        const val ESC : Byte = 0x1B
        const val FONTA : String = "a"
        const val FONTB : String = "b"
        const val LEFT : String = "left"
        const val CENTER : String = "center"
        const val RIGHT : String = "right"
    }

    fun render() : List<Byte> {
        init()

        for (i in 0..(layout.length() - 1)) {
            val layoutLine = layout.getJSONObject(i)

            if (layoutLine.getString("type") == "textarea") {
                text(
                        layoutLine.getString("value"),
                        if (layoutLine.has("align")) layoutLine.getString("align") else LEFT
                )
                newline()
            }
        }

        newline(3)
        return out
    }

    private fun init() {
        out.add(ESC)
        out.add('@'.toByte())
    }

    private fun mode(font: String = "a", emph : Boolean = false, doubleheight : Boolean = false, doublewidth : Boolean = false, underline : Boolean = false) {
        var modes = 0

        if (font == FONTB) {
            modes = modes or 1
        }

        if (emph) {
            modes = modes or 8
        }

        if (doubleheight) {
            modes = modes or 16
        }

        if (doublewidth) {
            modes = modes or 32
        }

        if (underline) {
            modes = modes or 128
        }

        out.add(ESC)
        out.add('!'.toByte())
        out.add(modes.toByte())
    }

    private fun newline() {
        out.add(0x0A)
    }

    private fun newline(lines : Int) {
        out.add(ESC)
        out.add('d'.toByte())
        out.add(lines.toByte())
    }

    private fun text(text : String, align : String = LEFT) {
        out.add(ESC)
        out.add('a'.toByte())

        if (align == CENTER) {
            out.add(1)
        } else if (align == RIGHT) {
            out.add(2)
        } else {
            out.add(0)
        }

        for (char in text) {
            out.add(char.toByte())
        }
    }
}