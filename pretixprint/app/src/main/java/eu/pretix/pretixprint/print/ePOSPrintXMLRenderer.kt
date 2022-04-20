package eu.pretix.pretixprint.print

import android.content.Context
import org.json.JSONObject

class ePOSPrintXMLRenderer(private val receipt: JSONObject, private val charsPerLine: Int, private val ctx: Context) {
    fun render(): ByteArray {
        val escposdata = ESCPOSRenderer(receipt, charsPerLine, ctx).render().toHex()
        return """
            <epos-print xmlns="http://www.epson-pos.com/schemas/2011/03/epos-print">
                <command>
                    $escposdata
                </command>
            </epos-print>
        """.trimIndent().toByteArray()
    }

    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}