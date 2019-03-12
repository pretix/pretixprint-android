package eu.pretix.pretixprint.print

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class ESCPOSRenderer(private val receipt: JSONObject, private val charsPerLine : Int, private val ctx: Context) {
    private val out = mutableListOf<Byte>()
    var taxrates = mutableListOf<String>()
    var taxvalues = mutableListOf<Double>()
    companion object {
        const val ESC : Byte = 0x1B
        const val GS : Byte = 0x1D
        const val FONTA : String = "a"
        const val FONTB : String = "b"
        const val LEFT : String = "left"
        const val CENTER : String = "center"
        const val RIGHT : String = "right"
        enum class CharacterCodeTable(val codeTable: Int) {
            PC437(0),
            Katakana(1),
            PC850(2),
            Multilingual(2),
            PC860(3),
            Portuguese(3),
            PC863(4),
            CanadianFrench(4),
            PC865(5),
            Nordic(5),
            WPC1252(16),
            PC866(17),
            Cyrillic2(17),
            PC852(18),
            Latin2(18),
            PC858(19),
            Euro(19),
            Thai42(20),
            Thai11(21),
            Thai13(22),
            Thai14(23),
            Thai16(24),
            Thai17(25),
            Thai18(26),
            UserDefined1(254),
            UserDefined2(255)
        }
        enum class InternationalCharacterSet(val country: Int) {
            USA(0),
            France(1),
            Germany(2),
            UK(3),
            Denmark1(4),
            Sweden(5),
            Italy(6),
            Spain1(7),
            Japan(8),
            Norway(9),
            Denmark2(10),
            Spain2(11),
            LatinAmerica(12),
            Korea(13),
            Slovenia(14),
            Croatia(14),
            China(15),
            Vietnam(16),
            Arabia(17),
            IndiaDevanagari(66),
            IndiaBengali (67),
            IndiaTamil(68),
            IndiaTelugu(69),
            IndiaAssamese(70),
            IndiaOriya(71),
            IndiaKannada(72),
            IndiaMalayalam(73),
            IndiaGujarati(74),
            IndiaPunjabi(75),
            IndiaMarathi(82)
        }
    }


    fun render() : ByteArray {
        out.clear()
        init()
        characterCodeTable(CharacterCodeTable.WPC1252.codeTable)
        internationalCharacterSet(InternationalCharacterSet.Germany.country)

        val layout = receipt.getJSONArray("__layout")

        for (i in 0..(layout.length() - 1)) {
            val layoutLine = layout.getJSONObject(i)
            renderline(layoutLine)
        }

        newline(2)
        cut()
        return out.toByteArray()
    }

    private fun renderline(layoutLine: JSONObject) {
        when (layoutLine.getString("type")) {
            "textarea" -> {
                text(
                        getText(layoutLine),
                        if (layoutLine.has("align")) layoutLine.getString("align") else LEFT
                )
            }
            "newline" -> {
                if (layoutLine.has("count")) {
                    newline(layoutLine.getInt("count"))
                } else {
                    newline()
                }
            }
            "orderlines" -> {
                val positions = receipt.getJSONArray("positions")

                for (i in 0..(positions.length() - 1)) {
                    val position = positions.getJSONObject(i)

                    processTaxes()

                    val taxindex = taxrates.indexOf(position.getString("tax_rate"))

                    splitline(
                            position.getString("sale_text"),
                            DecimalFormat("0.00").format(position.getDouble("price")) + " " + (taxindex + 65).toChar()
                    )
                }
            }
            "taxlines" -> {
                processTaxes()

                for (i in 0..(taxrates.count() -1)) {
                    splitline(
                            (i + 65).toChar() + " " + DecimalFormat("0.00").format(taxrates[i].toDouble()) + "%:",
                            DecimalFormat("0.00").format(taxvalues[i])
                    )
                }
            }
            "splitline" -> {
                val splitLines = layoutLine.getJSONArray("content")

                splitline(
                        getText(splitLines.getJSONObject(0)),
                        getText(splitLines.getJSONObject(1))
                )
            }
            "testmode" -> {
                if (receipt.getBoolean("testmode")) {
                    mode(doubleheight = true, doublewidth = true, underline = true, emph = true)
                    text("TESTMODE", CENTER)
                    newline(2)
                    mode()
                }
            }
            "emphasize" -> {
                emphasize(layoutLine.getBoolean("on"))
            }
        }
    }

    private fun processTaxes() {
        if (taxrates.isEmpty()) {
            val positions = receipt.getJSONArray("positions")
            for (i in 0..(positions.length() - 1)) {
                val position = positions.getJSONObject(i)

                if (position.getString("tax_rate") !in taxrates) {
                    taxrates.add(position.getString("tax_rate"))
                    taxvalues.add(position.getDouble("tax_value"))
                } else {
                    val taxindex = taxrates.indexOf(position.getString("tax_rate"))
                    taxvalues[taxindex].plus(position.getDouble("tax_value"))
                }
            }
        }
    }

    private fun getText(layoutLine : JSONObject): String {
        if (layoutLine.has("content")) {
            var text: String
            val content = layoutLine.getString("content").split("_")

            text = when (content[0]) {
                "invoice" -> {
                   receipt.getJSONObject("__invoicesettings").getString(layoutLine.getString("content"))
                }
                "calc" -> {
                    when (layoutLine.getString("content")) {
                        "calc_total" -> {
                            val positions = receipt.getJSONArray("positions")
                            var total = 0.00

                            for (i in 0..(positions.length() - 1)) {
                                val position = positions.getJSONObject(i)

                                total = total.plus(position.getDouble("price"))
                            }

                            DecimalFormat("0.00").format(total)
                        }
                        else -> {
                            receipt.getString(layoutLine.getString("content"))
                        }
                    }
                }
                else -> {
                    if (layoutLine.getString("content").startsWith("datetime")) {
                        DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT, Locale.getDefault())
                                .format(
                                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'").parse(
                                                receipt.getString(layoutLine.getString("content"))
                                        )
                                )
                    } else {
                        try {
                            receipt.getString(layoutLine.getString("content"))
                        } catch (ex: JSONException) {
                            receipt.getString(layoutLine.getString("text"))
                        }
                    }
                }
            }

            if (layoutLine.has("padding")) {
                text += " ".repeat(layoutLine.getInt("padding"))
            }

            if (layoutLine.has("uppercase")) {
                text = text.toUpperCase()
            }

            if (text.isNotBlank()) {
                return text
            }
        }

        return layoutLine.getString("text")
    }

    private fun splitline(leftText : String, rightText: String, padding: Int = 2) {
        val limit = charsPerLine - rightText.length - padding
        val leftSplit = leftText.split(" ")
        var leftTextList = mutableListOf<String>()

        leftTextList.add("")

        for (i in 0..(leftSplit.count() - 1)) {
            if ((leftTextList.last().length + leftSplit[i].length + 1) <= limit) {
                if (leftTextList.last().isEmpty()) {
                    leftTextList[leftTextList.lastIndex] += leftSplit[i]
                } else {
                    leftTextList[leftTextList.lastIndex] += " " + leftSplit[i]
                }
            } else if (leftSplit[i].length <= limit) {
                leftTextList.add(leftSplit[i])
            } else {
                leftTextList.addAll(leftSplit[i].chunked(limit))
            }
        }

        for (i in 0..(leftTextList.count() - 2)) {
            text(leftTextList[i], LEFT)
            newline()
        }

        text(leftTextList.last() + " ".repeat(charsPerLine - leftTextList.last().length - rightText.length) + rightText, LEFT)
        newline()
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

        var printText = text.replace("–", "-")

        for (char in printText) {
            out.add(char.toByte())
        }
    }

    private fun characterCodeTable(table : Int = 0) {
        out.add(ESC)
        out.add('t'.toByte())
        out.add(table.toByte())
    }

    private fun internationalCharacterSet(country : Int = 0) {
        out.add(ESC)
        out.add('R'.toByte())
        out.add(country.toByte())
    }

    private fun cut(partial : Boolean = false) {
        out.add(GS)
        out.add('V'.toByte())

        if (partial) {
            out.add(1)
        } else {
            out.add(0)
        }
    }

    private fun emphasize(on : Boolean) {
        out.add(ESC)
        out.add('E'.toByte())

        if (on) {
            out.add(1)
        } else {
            out.add(0)
        }
    }
}