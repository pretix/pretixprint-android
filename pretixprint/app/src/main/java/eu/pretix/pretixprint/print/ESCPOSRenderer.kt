package eu.pretix.pretixprint.print

import android.content.Context
import eu.pretix.pretixprint.R
import org.joda.time.format.ISODateTimeFormat
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.Charset
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.*

class ESCPOSRenderer(private val dialect: Dialect, private val receipt: JSONObject, private val charsPerLine: Int, private val ctx: Context) {
    private val out = mutableListOf<Byte>()
    var taxrates = mutableListOf<String>()
    var taxvalues = mutableListOf<Double>()
    var reverseSale: Boolean = false

    companion object {
        const val ESC: Byte = 0x1B
        const val FS: Byte = 0x1C
        const val GS: Byte = 0x1D
        const val RS: Byte = 0x1E
        const val BEL: Byte = 0x07
        const val SUB: Byte = 0x1A
        const val FONTA: String = "a"
        const val FONTB: String = "b"
        const val FONTC: String = "c" // only for Dialect.StarPRNT
        const val LEFT: String = "left"
        const val CENTER: String = "center"
        const val RIGHT: String = "right"

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
            StarCp1252(32),
            IminCp1250(0x48),
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
            IndiaBengali(67),
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

        enum class Cashdrawer(val number: Int) {
            Drawer1(0),
            Drawer2(1)
        }

        enum class Dialect(val description: String) {
            EpsonDefault("Epson, Bixolon, Metapace, SNBC"),
            Sunmi("Sunmi"),
            IMin("iMin"),
            StarPRNT("StarPRNT")
        }
    }

    fun render(): ByteArray {
        out.clear()
        init()
        when(dialect) {
            Dialect.EpsonDefault -> {
                characterCodeTable(CharacterCodeTable.WPC1252.codeTable)
                internationalCharacterSet(InternationalCharacterSet.Germany.country)
            }
            Dialect.StarPRNT -> {
                characterCodeTable(CharacterCodeTable.StarCp1252.codeTable)
                internationalCharacterSet(InternationalCharacterSet.Germany.country)
            }
            Dialect.Sunmi -> {
                selectKanjiCharacterMode()
                selectKanjiCharacterCodeSystem(-1)
            }
            Dialect.IMin -> {
                cancelKanjiCharacterMode()
                characterCodeTable(CharacterCodeTable.IminCp1250.codeTable)
            }
        }

        val layout = receipt.getJSONArray("__layout")

        for (i in 0 until layout.length()) {
            val layoutLine = layout.getJSONObject(i)
            renderline(layoutLine)
        }

        if (receipt.optBoolean("feedAfter", true)) {
            newline(4)
        }
        if (receipt.optBoolean("cutAfter", true)) {
            cut()
        }
        if (receipt.optBoolean("drawerAfter", true)) {
            opencashdrawer(Cashdrawer.Drawer1.number, 50, 500)
            opencashdrawer(Cashdrawer.Drawer2.number, 50, 500)
        }

        return out.toByteArray()
    }

    private fun renderline(layoutLine: JSONObject) {
        val modekeys = listOf("font", "emph", "doubleheight", "doublewidth", "underline")
        when (layoutLine.getString("type")) {
            "textarea" -> {
                val t = getText(layoutLine)
                if (!t.isNullOrBlank()) {
                    if (modekeys.filter { layoutLine.has(it) }.isNotEmpty()) {
                        mode(
                                font = layoutLine.optString("font", FONTA),
                                emph = layoutLine.optBoolean("emph", false),
                                doubleheight = layoutLine.optBoolean("doubleheight", false),
                                doublewidth = layoutLine.optBoolean("doublewidth", false),
                                underline = layoutLine.optBoolean("underline", false)
                        )
                    }
                    text(
                            t,
                            if (layoutLine.has("align")) layoutLine.getString("align") else LEFT
                    )
                    if (modekeys.filter { layoutLine.has(it) }.isNotEmpty()) {
                        mode()
                    }
                }
            }
            "qr" -> {
                val t = getText(layoutLine)
                if (!t.isNullOrBlank()) {
                    qr(t, layoutLine.optInt("blocksize", 6))
                }
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
                processTaxes()

                // We group duplicate consecutive lines based on all attributes that are relevant
                // for printing. We never group items that have addons to avoid horrible confusion,
                // but we can group duplicate add-ons of the same parent.
                val receiptLines = mutableListOf<ReceiptLine>()
                val addonHolders = mutableSetOf<Long>()
                for (i in 0 until positions.length()) {
                    val position = positions.getJSONObject(i)
                    if (!position.isNull("addon_to")) {
                        addonHolders.add(position.getLong("addon_to"))
                    }
                }
                for (i in 0 until positions.length()) {
                    val position = positions.getJSONObject(i)
                    receiptLines.add(ReceiptLine(
                            type = position.optString("type", ""),
                            sale_text = position.getString("sale_text"),
                            subevent_text = position.optString("subevent_text", ""),
                            price = position.getDouble("price"),
                            tax_rate = position.getString("tax_rate"),
                            canceled = position.getBoolean("canceled"),
                            item = position.optLong("item", 0),
                            variation = position.optLong("variation", 0),
                            tax_rule = position.optLong("tax_rule", 0),
                            subevent = position.optLong("subevent", 0),
                            voucher_code = position.optString("voucher_code", ""),
                            is_addon = !position.isNull("addon_to"),
                            addon_group = if (!position.isNull("addon_to")) {
                                position.getLong("addon_to")
                            } else if (addonHolders.contains(position.getLong("position_id"))) {
                                position.getLong("position_id")
                            } else {
                                null
                            }
                    ))
                }

                for ((line, cnt) in receiptLines.mergeConsecutive()) {
                    val taxindex = taxrates.indexOf(line.tax_rate)

                    if (line.type == "PRODUCT_RETURN") {
                        reverseSale = true
                        emphasize(true)
                        text(ctx.getString(R.string.receiptline_return)); newline()
                        emphasize(false)
                    }
                    splitline(
                            (if(line.is_addon) "+ " else "") + line.sale_text,
                            DecimalFormat("0.00").format(line.price * cnt) + " " + (taxindex + 65).toChar(),
                            indentation = if(line.is_addon) 2 else 0
                    )
                    newline()
                    val singlePrice = DecimalFormat("0.00").format(line.price)
                    if (cnt > 1) {
                        splitline(
                                "$cnt x ${receipt.getString("currency")} $singlePrice",
                                "        ",
                                indentation=if(line.is_addon) 4 else 2
                        )
                        newline()
                    }
                    if (!line.subevent_text.isNullOrBlank() && line.subevent_text != "null" && !line.is_addon) {
                        splitline(
                                line.subevent_text,
                                "        ",
                                indentation=2
                        )
                        newline()
                    }

                    if (line.canceled) {
                        emphasize(true)
                        text(ctx.getString(R.string.receiptline_cancellation)); newline()
                        emphasize(false)

                        splitline(
                                (if(line.is_addon) "+ " else "") + line.sale_text,
                                "- " + DecimalFormat("0.00").format(line.price * cnt) + " " + (taxindex + 65).toChar(),
                                indentation = if(line.is_addon) 2 else 0
                        )
                        newline()
                        if (cnt > 1) {
                            splitline(
                                    "-$cnt x ${receipt.getString("currency")} $singlePrice",
                                    "        ",
                                    indentation=2
                            )
                            newline()
                        }
                    }
                }
            }
            "taxlines" -> {
                processTaxes()

                for (i in 0..(taxrates.count() - 1)) {
                    splitline(
                            (i + 65).toChar() + " " + DecimalFormat("0.00").format(taxrates[i].toDouble()) + "%:",
                            DecimalFormat("0.00").format(taxvalues[i])
                    )
                    newline()
                }
            }
            "paymentlines" -> {
                when (receipt.getString("payment_type")) {
                    "square_pos" -> {
                        emphasize(true)
                        text(ctx.getString(R.string.receiptline_paidcard))
                        newline()
                        emphasize(false)
                        val payment_data = receipt.getJSONObject("payment_data")
                        // These two could have been splitlines - but I'm too lazy to fix the splitline() function to work properly with these UUIDs
                        text("LID: ")
                        text(payment_data.getString("client_transactionId"))
                        newline()
                        if (payment_data.has("server_transactionId")) {
                            text("SID: ")
                            text(payment_data.getString("server_transactionId"))
                            newline()
                        }
                        newline()
                    }
                    "stripe_terminal" -> {
                        emphasize(true)
                        text(ctx.getString(R.string.receiptline_paidcard))
                        newline()
                        emphasize(false)
                        val payment_data = receipt.getJSONObject("payment_data")
                        splitline("Application", payment_data.getString("application_preferred_name"))
                        splitline("AID", payment_data.getString("dedicated_file_name"))
                        splitline("TVR", payment_data.getString("terminal_verification_results"))
                        splitline("ARC / TSI", payment_data.getString("authorization_response_code") + " / " + payment_data.getString("transaction_status_information"))
                        newline()
                    }
                    "sumup", "card" -> {
                        val payment_data = receipt.getJSONObject("payment_data")
                        text("-".repeat(charsPerLine), CENTER)
                        newline(2)
                        emphasize(true)
                        text(ctx.getString(R.string.receiptline_customerreceipt), CENTER);
                        newline(2)
                        emphasize(false)
                        splitline(ctx.getString(R.string.receiptline_merchantid), payment_data.getString("merchant_code")); newline()
                        splitline(ctx.getString(R.string.receiptline_transactionid), payment_data.getString("tx_code")); newline()
                        //splitline(ctx.getString(R.string.receiptline_terminalid), payment_data.getString("")); newline()
                        //splitline(ctx.getString(R.string.receiptline_receiptnumber), payment_data.getString("")); newline()
                        newline()
                        text(payment_data.getString("card_type")); newline()
                        text("**** **** **** " + payment_data.getString("last4")); newline()
                        //text("Max Mustermann"); newline()
                        text(payment_data.getString("entry_mode"))
                        newline(2)
                        mode(doubleheight = true, doublewidth = true)
                        text(ctx.getString(R.string.receiptline_paymentreceipt), CENTER); newline()
                        mode()
                        text(getDate(receipt.getString("datetime_closed")), CENTER)
                        newline(2)
                        emphasize(true)
                        splitline(ctx.getString(R.string.receiptline_amount), receipt.getString("currency") + " " + DecimalFormat("0.00").format(calcTotal())); newline()
                        newline(2)
                        emphasize(false)
                        text(ctx.getString(R.string.receiptline_cardissuerpaymenttext), CENTER)
                        newline(2)
                        mode(doubleheight = true, doublewidth = true)
                        text(payment_data.getString("status"), CENTER)
                        newline(2)
                        mode()
                        text(ctx.getString(R.string.receiptline_keepreceipt), CENTER)
                        newline(2)
                        text("-".repeat(charsPerLine), CENTER); newline()
                    }
                    "izettle" -> {
                        var payment_data = receipt.getJSONObject("payment_data")
                        if (payment_data.has("reversal_of")) {
                            reverseSale = true
                            payment_data = payment_data.getJSONObject("reversal_of")
                        }
                        text("-".repeat(charsPerLine), CENTER)
                        newline(2)
                        emphasize(true)
                        text(ctx.getString(R.string.receiptline_customerreceipt), CENTER)
                        newline(2)
                        emphasize(false)
                        text(payment_data.optString("applicationName")); newline()
                        text(payment_data.optString("maskedPan")); newline()
                        text(payment_data.optString("cardPaymentEntryMode"))
                        newline(2)
                        mode(doubleheight = true, doublewidth = true)
                        text(ctx.getString(R.string.receiptline_paymentreceipt), CENTER); newline()
                        mode()
                        text(getDate(receipt.getString("datetime_closed")), CENTER)
                        newline(2)
                        emphasize(true)
                        splitline(ctx.getString(R.string.receiptline_amount), receipt.getString("currency") + " " + DecimalFormat("0.00").format(calcTotal())); newline()
                        newline(2)
                        emphasize(false)
                        if (calcTotal().compareTo(0.0f) > 0) {
                            text(ctx.getString(R.string.receiptline_cardissuerpaymenttext), CENTER)
                            newline(2)
                        }
                        mode(doubleheight = true, doublewidth = true)
                        text(payment_data.optString("authorizationCode"), CENTER); newline()
                        text(payment_data.optString("tvr"), CENTER)
                        newline(2)
                        mode()
                        text(ctx.getString(R.string.receiptline_keepreceipt), CENTER)
                        newline(2)
                        text("-".repeat(charsPerLine), CENTER); newline()
                    }
                    "terminal_zvt" -> {
                        text(ctx.getString(R.string.receiptline_paidcard))
                        newline()
                        val payment_data = receipt.getJSONObject("payment_data")
                        if (payment_data.has("cardName") && !payment_data.isNull("cardName")) {
                            text(
                                ctx.getString(R.string.receiptline_cardtype) + " " + payment_data.getString(
                                    "cardName"
                                )
                            )
                            newline()
                        }
                        if (payment_data.has("traceNumber") && !payment_data.isNull("traceNumber")) {
                            text(
                                ctx.getString(R.string.receiptline_tracenr) + " " + payment_data.getString(
                                    "traceNumber"
                                )
                            )
                            newline()
                        }
                        if (payment_data.has("receiptNumber") && !payment_data.isNull("receiptNumber")) {
                            text(
                                ctx.getString(R.string.receiptline_receiptnumber) + " " + payment_data.getString(
                                    "receiptNumber"
                                )
                            )
                            newline()
                        }
                    }
                    "external" -> {
                        text(ctx.getString(R.string.receiptline_paidcard))
                        newline()
                    }
                    "cash" -> {
                        if (calcTotal().compareTo(0.0f) != 0) {
                            text(ctx.getString(R.string.receiptline_paidcash))
                            newline()
                        }
                    }
                }
            }
            "splitline" -> {
                val splitLines = layoutLine.getJSONArray("content")

                splitline(
                        getText(splitLines.getJSONObject(0))!!,
                        getText(splitLines.getJSONObject(1))!!
                )
            }
            "headline" -> {
                if (receipt.getBoolean("printed")) {
                    mode(doubleheight = true, doublewidth = true, underline = true, emph = true)
                    text(ctx.getString(R.string.receiptline_copy), CENTER)
                    newline(2)
                    mode()
                }
            }
            "footlines" -> {
                if (reverseSale) {
                    text(ctx.getString(R.string.receiptline_customerinformation))
                    newline(10)
                    mode(underline = true)
                    text("X")
                    text(" ".repeat(charsPerLine - 1))
                    newline()
                    mode(underline = false)
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
                    taxvalues.add(0.00)
                }

                val taxindex = taxrates.indexOf(position.getString("tax_rate"))

                if (!(position.getBoolean("canceled"))) {
                    taxvalues[taxindex] = taxvalues[taxindex].plus(position.getDouble("tax_value"))
                }
            }
        }
    }

    private fun getText(layoutLine: JSONObject): String? {
        if (layoutLine.has("content")) {
            var text: String
            val fullContent = layoutLine.getString("content")
            val content = layoutLine.getString("content").split("_")

            text = when (content[0]) {
                "invoice" -> {
                    val invoiceSettings = receipt.getJSONObject("__invoicesettings")
                    if (invoiceSettings.isNull(fullContent)) {
                        ""
                    } else {
                        invoiceSettings.optString(fullContent, "")
                    }
                }
                "calc" -> {
                    when (fullContent) {
                        "calc_total" -> {
                            DecimalFormat("0.00").format(calcTotal())
                        }
                        else -> {
                            receipt.optString(fullContent, "")
                        }
                    }
                }
                "seat" -> {
                    try {
                        receipt.getJSONObject("seat").optString(content[1])
                    } catch (ex: JSONException) {
                        ""
                    }
                }
                else -> {
                    if (fullContent.startsWith("datetime")) {
                        getDate(receipt.getString(fullContent))
                    } else {
                        if (receipt.isNull(fullContent)) {
                            ""
                        } else {
                            try {
                                receipt.optString(fullContent, "")
                            } catch (ex: JSONException) {
                                receipt.optString(layoutLine.getString("text"), "")
                            }
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

            if (layoutLine.has("prefix") && !text.isEmpty()) {
                text = layoutLine.getString("prefix") + text
            }

            return text
        }

        return layoutLine.getString("text")
    }

    private fun getDate(date: String): String {
        val dfOut = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT, Locale.getDefault())
        dfOut.timeZone = TimeZone.getDefault()
        val parser = ISODateTimeFormat.dateTimeParser()
        return dfOut.format(
                parser.parseDateTime(date).toLocalDateTime().toDate()
        )
    }

    private fun calcTotal(): Double {
        val positions = receipt.getJSONArray("positions")
        var total = 0.00

        for (i in 0..(positions.length() - 1)) {
            val position = positions.getJSONObject(i)

            if (!(position.getBoolean("canceled"))) {
                total = total.plus(position.getDouble("price"))
            }
        }

        return total
    }

    private fun splitline(leftText: String, rightText: String, padding: Int = 2, indentation: Int = 0) {
        val limit = charsPerLine - rightText.length - padding - indentation
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
            text(" ".repeat(indentation) + leftTextList[i], LEFT)
            newline()
        }

        text(" ".repeat(indentation) + leftTextList.last() + " ".repeat(charsPerLine - leftTextList.last().length - rightText.length - indentation) + rightText, LEFT)
    }

    private fun init() {
        out.add(ESC)
        out.add('@'.toByte())
    }

    private fun mode(font: String = FONTA, emph: Boolean = false, doubleheight: Boolean = false, doublewidth: Boolean = false, underline: Boolean = false) {
        if (dialect == Dialect.StarPRNT) {
            // font
            out.add(ESC)
            out.add(RS)
            out.add('F'.toByte())
            when(font) {
                FONTA -> out.add(0)
                FONTB -> out.add(1)
                FONTC -> out.add(2)
            }
            // emph
            out.add(ESC)
            out.add((if (emph) 'E' else 'F').toByte())
            // double
            out.add(ESC)
            out.add('i'.toByte())
            out.add(if (doubleheight) 1 else 0)
            out.add(if (doublewidth) 1 else 0)
            // underline
            out.add(ESC)
            out.add('-'.toByte())
            out.add(if (underline) 1 else 0)
            return
        }

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

    private fun newline(lines: Int) {
        out.add(ESC)
        when(dialect) {
            Dialect.StarPRNT -> out.add('a'.toByte())
            else -> out.add('d'.toByte())
        }
        out.add(lines.toByte())
    }

    private fun qr(text: String, blockSize: Int) {
        val data = text.toByteArray()
        val payloadLen = data.size + (if (dialect == Dialect.StarPRNT) 0 else 3)
        val payloadPL = (payloadLen % 256)
        val payloadPH = (payloadLen / 256)

        if (dialect == Dialect.StarPRNT) {
            // https://www.starmicronics.com/support/Mannualfolder/escpos_cm_en.pdf
            // QR Code: Select the model
            out.addAll(listOf(0x1b, 0x1d, 0x79, 0x53, 0x30, 0x01).map { it.toByte() })
            // Set n for error correction [x00 -> 7%] [x01-> 15%] [x02 -> 25%] [x03 -> 30%]
            out.addAll(listOf(0x1b, 0x1d, 0x79, 0x53, 0x31, 0x01).map { it.toByte() })
            // QR Code: Set the size of module
            out.addAll(listOf(0x1b, 0x1d, 0x79, 0x53, 0x32, blockSize).map { it.toByte() })
            // QR Code: Store the data in the symbol storage area
            out.addAll(listOf(0x1b, 0x1d, 0x79, 0x44, 0x31, 0x00, payloadPL, payloadPH).map { it.toByte() })
            out.addAll(data.toList())
            // QR Code: print
            out.addAll(listOf(0x1b, 0x1d, 0x79, 0x50).map { it.toByte() })
        } else {
            // QR Code: Select the model
            //              Hex     1D      28      6B      04      00      31      41      n1(x32)     n2(x00) - size of model
            // set n1 [49 x31, model 1] [50 x32, model 2] [51 x33, micro qr code]
            // https://reference.epson-biz.com/modules/ref_escpos/index.php?content_id=140
            out.addAll(listOf(0x1d, 0x28, 0x6b, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00).map { it.toByte() })
            // QR Code: Set the size of module
            // Hex      1D      28      6B      03      00      31      43      n
            // n depends on the printer
            // https://reference.epson-biz.com/modules/ref_escpos/index.php?content_id=141
            out.addAll(listOf(0x1d, 0x28, 0x6b, 0x03, 0x00, 0x31, 0x43, blockSize).map { it.toByte() })
            // QR Code: Set error correction
            //          Hex     1D      28      6B      03      00      31      45      n
            // Set n for error correction [48 x30 -> 7%] [49 x31-> 15%] [50 x32 -> 25%] [51 x33 -> 30%]
            // https://reference.epson-biz.com/modules/ref_escpos/index.php?content_id=142
            out.addAll(listOf(0x1d, 0x28, 0x6b, 0x03, 0x00, 0x31, 0x45, 0x31).map { it.toByte() })
            // QR Code: Store the data in the symbol storage area
            // Hex      1D      28      6B      pL      pH      31      50      30      d1...dk
            // https://reference.epson-biz.com/modules/ref_escpos/index.php?content_id=143
            //                        1D          28          6B         pL          pH  cn(49->x31) fn(80->x50) m(48->x30) d1…dk
            out.addAll(listOf(0x1d, 0x28, 0x6b, payloadPL, payloadPH, 0x31, 0x50, 0x30).map { it.toByte() })
            out.addAll(data.toList())
            // QR Code: Print the symbol data in the symbol storage area
            // Hex      1D      28      6B      03      00      31      51      m
            // https://reference.epson-biz.com/modules/ref_escpos/index.php?content_id=144
            out.addAll(listOf(0x1d, 0x28, 0x6b, 0x03, 0x00, 0x31, 0x51, 0x30).map { it.toByte() })
        }
    }

    private fun align(align: String = LEFT) {
        out.add(ESC)
        if (dialect == Dialect.StarPRNT) {
            out.add(GS)
        }
        out.add('a'.toByte())

        if (align == CENTER) {
            out.add(1)
        } else if (align == RIGHT) {
            out.add(2)
        } else {
            out.add(0)
        }
    }

    private fun text(text: String, align: String = LEFT) {
        align(align)
        val printText = text.replace("–", "-")

        if (dialect == Dialect.Sunmi) {
            out.addAll(printText.toByteArray(Charset.forName("UTF-8")).toTypedArray())
        } else if (dialect == Dialect.IMin) {
            out.addAll(printText.toByteArray(Charset.forName("cp1250")).toTypedArray())
        } else {
            for (char in printText) {
                out.add(char.toByte())
            }
        }
    }

    private fun characterCodeTable(table: Int = 0) {
        out.add(ESC)
        if (dialect == Dialect.StarPRNT) {
            out.add(GS)
        }
        out.add('t'.toByte())
        out.add(table.toByte())
    }

    private fun internationalCharacterSet(country: Int = 0) {
        out.add(ESC)
        out.add('R'.toByte())
        out.add(country.toByte())
    }

    private fun selectKanjiCharacterMode() {
        out.add(FS)
        out.add('&'.toByte())
    }

    private fun cancelKanjiCharacterMode() {
        out.add(FS)
        out.add('.'.toByte())
    }

    private fun selectKanjiCharacterCodeSystem(system: Int = 0) {
        out.add(FS)
        out.add('C'.toByte())
        out.add(system.toByte())
    }

    private fun cut(partial: Boolean = false) {
        if (dialect == Dialect.StarPRNT) {
            out.add(ESC)
            out.add('d'.toByte())
        } else {
            out.add(GS)
            out.add('V'.toByte())
        }
        if (partial) {
            out.add(1)
        } else {
            out.add(0)
        }
    }

    private fun emphasize(on: Boolean) {
        out.add(ESC)
        when(dialect) {
            Dialect.StarPRNT -> {
                out.add(if (on) 'E'.toByte() else 'F'.toByte())
            }
            else -> {
                out.add('E'.toByte())
                out.add(if (on) 1 else 0)
            }
        }
    }

    private fun opencashdrawer(drawer: Int, durationOn: Int, durationOff: Int) {
        if (dialect == Dialect.StarPRNT) {
            if (drawer == Cashdrawer.Drawer1.number) {
                // config
                out.add(ESC)
                out.add(BEL)
                out.add(durationOn.toByte())
                out.add(durationOff.toByte())
                // open
                out.add(BEL)
            } else if (drawer == Cashdrawer.Drawer2.number) {
                // drawer 2 is using durations from the printer firmware, cannot override
                out.add(SUB)
            }
            return
        }
        out.add(ESC)
        out.add('p'.toByte())
        out.add(drawer.toByte())
        out.add(durationOn.toByte())
        out.add(durationOff.toByte())
    }

    fun renderTestPage(): ByteArray {
        out.clear()
        init()
        when(dialect) {
            Dialect.EpsonDefault -> {
                characterCodeTable(CharacterCodeTable.WPC1252.codeTable)
                internationalCharacterSet(InternationalCharacterSet.Germany.country)
            }
            Dialect.StarPRNT -> {
                characterCodeTable(CharacterCodeTable.StarCp1252.codeTable)
                internationalCharacterSet(InternationalCharacterSet.Germany.country)
            }
            Dialect.Sunmi -> {
                selectKanjiCharacterMode()
                selectKanjiCharacterCodeSystem(-1)
            }
            Dialect.IMin -> {
                cancelKanjiCharacterMode()
                characterCodeTable(CharacterCodeTable.IminCp1250.codeTable)
            }
        }
        qr("TEST COMPLETED", 6)
        newline()
        text("German: äöüÄÖÜß", align = LEFT)
        newline()
        mode(doubleheight = true, doublewidth = true, emph = true, underline = true)
        text("TEST COMPLETED", align = CENTER)
        newline()
        newline(4)
        mode()
        align()
        cut()
        opencashdrawer(Cashdrawer.Drawer1.number, 50, 500)
        opencashdrawer(Cashdrawer.Drawer2.number, 50, 500)
        return out.toByteArray()
    }

    data class ReceiptLine(
            val type: String,
            val tax_rate: String,
            val price: Double,
            val sale_text: String,
            val subevent_text: String?,
            val canceled: Boolean,
            val item: Long?,
            val variation: Long?,
            val tax_rule: Long?,
            val voucher_code: String?,
            val subevent: Long?,
            val is_addon: Boolean,
            val addon_group: Long?,
    )

    private fun <E> List<E>.mergeConsecutive(): List<Pair<E, Int>>
            = fold(listOf()) { acc, e ->
        if (acc.isNotEmpty() && acc.last().first == e) {
            val currentTotal = acc.last().second
            acc.dropLast(1) + (e to currentTotal + 1)
        } else
            acc + (e to 1)
    }
}