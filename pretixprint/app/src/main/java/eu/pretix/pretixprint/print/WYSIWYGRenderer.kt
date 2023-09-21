package eu.pretix.pretixprint.print

import android.content.Context
import eu.pretix.libpretixprint.templating.ContentProvider
import eu.pretix.libpretixprint.templating.FontRegistry
import eu.pretix.libpretixprint.templating.FontSpecification
import eu.pretix.libpretixprint.templating.Layout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.*


class OrderPositionContentProvider(private val order: JSONObject, private val op: JSONObject, private val imageMap: Map<String, InputStream?>) : ContentProvider {
    fun i18nToString(str: JSONObject, locale: String?): String? {
        val lng = locale ?: Locale.getDefault().language
        val lngparts = lng.split("[-_]".toRegex()).toTypedArray()
        try {
            if (str.has(lng) && str.getString(lng) != "") {
                return str.getString(lng)
            } else {
                val it: Iterator<*> = str.keys()
                while (it.hasNext()) {
                    val key = it.next() as String
                    val parts = key.split("[-_]".toRegex()).toTypedArray()
                    if (parts[0] == lngparts[0] && str.getString(key) != "") {
                        return str.getString(key)
                    }
                }
                if (str.has("en") && str.getString("en") != "") {
                    return str.getString("en")
                } else if (str.length() > 0) {
                    return str.getString(str.keys().next() as String)
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return null
    }

    private fun interpolate(str: String): String {
        return str.replace(Regex("\\{([a-zA-Z0-9:_]+)\\}")) { match ->
            getTextContent(match.groups[1]!!.value, null, null)
        }
    }

    override fun getTextContent(content: String?, text: String?, textI18n: JSONObject?): String {
        if (content == "other") {
            return interpolate(text ?: "")
        } else if (content == "other_i18n") {
            return if (textI18n != null) interpolate(i18nToString(textI18n, order.optString("locale")) ?: "") else ""
        } else if (op.has("pdf_data") && op.getJSONObject("pdf_data").has(content)) {
            if (op.getJSONObject("pdf_data").isNull(content)) {
                return ""
            }
            return op.getJSONObject("pdf_data").getString(content)
        } else {
            return "???"
        }
    }

    override fun getImageContent(content: String?): InputStream? {
        return imageMap[content]
    }

    override fun getBarcodeContent(content: String?, text: String?, textI18n: JSONObject?): String {
        return when (content) {
            "secret" -> op.getString("secret")  // the one in textcontent might be shortened
            "" -> op.getString("secret")  // required for backwards compatibility
            "pseudonymization_id" -> op.getString("pseudonymization_id")  // required for backwards compatibility
            else -> getTextContent(content, text, textI18n)
        }
    }

}


class WYSIWYGRenderer(private val layout: JSONArray, private val order: JSONObject, private val positionIndex: Int, private val background: InputStream?, private val ctx: Context, private val imageMap: Map<String, InputStream?>) {
    fun writePDF(outFile: File) {
        val positions = order.getJSONArray("positions")
        val posList = emptyList<ContentProvider>().toMutableList()
        posList.add(OrderPositionContentProvider(order, positions.getJSONObject(positionIndex), imageMap))
        try {
            val l = Layout(
                    layout,
                    background,
                    posList.listIterator()
            )
            l.render(outFile.absolutePath)
        } catch (e: JSONException) {
            e.printStackTrace();
        }
    }

    companion object {
        fun registerFonts(ctx: Context) {
            registerFontFamily(ctx, "Almarai", "fonts/almarai-v5-arabic-%s.ttf", "regular", "800", "800", "regular")
            registerFontFamily(ctx, "Baloo Bhaijaan", "fonts/baloo-bhaijaan-v6-latin-ext_vietnamese_latin_arabic-%s.ttf", "regular", "regular", "regular", "regular")
            registerFontFamily(ctx, "Open Sans", "fonts/OpenSans-%s.ttf")
            registerFontFamily(ctx, "Noto Sans", "fonts/NotoSans-%s-webfont.ttf")
            registerFontFamily(ctx, "Noto Sans Japanese", "fonts/noto-sans-jp-v52-cyrillic_japanese_latin_latin-ext_vietnamese-%s.ttf", "regular", "700", "700", "regular")
            registerFontFamily(ctx, "Roboto", "fonts/Roboto-%s.ttf")
            registerFontFamily(ctx, "Droid Serif", "fonts/DroidSerif-%s-webfont.ttf")
            registerFontFamily(ctx, "Fira Sans", "fonts/firasans-%s-webfont.ttf")
            registerFontFamily(ctx, "Lato", "fonts/Lato-%s.ttf")
            registerFontFamily(ctx, "Vollkorn", "fonts/Vollkorn-%s.ttf")
            registerFontFamily(ctx, "Montserrat", "fonts/montserrat-%s-webfont.ttf")
            registerFontFamily(ctx, "Oswald", "fonts/oswald-%s-webfont.ttf")
            registerFontFamily(ctx, "Tajawal", "fonts/tajawal-v3-latin_arabic-%s.ttf", "regular", "700", "700", "regular")
            registerFontFamily(ctx, "Titillium", "fonts/titillium-%s-webfont.ttf")
            registerFontFamily(ctx, "Titillium Upright", "fonts/titillium-%s-webfont.ttf", "RegularUpright", "BoldUpright", "BoldUpright", "RegularUpright")
            registerFontFamily(ctx, "Titillium Semibold Upright", "fonts/titillium-%s-webfont.ttf", "SemiboldUpright", "BoldUpright", "BoldUpright", "SemiboldUpright")
            registerFontFamily(ctx, "Roboto Condensed", "fonts/RobotoCondensed-%s-webfont.ttf")
            registerFontFamily(ctx, "DejaVu Sans", "fonts/DejaVuSans-%s-webfont.ttf")
            registerFontFamily(ctx, "Poppins", "fonts/poppins-v12-latin-%s.ttf", "500", "700", "700italic", "500italic")
            registerFontFamily(ctx, "Space Mono", "fonts/space-mono-v10-latin-ext_latin-%s.ttf", "regular", "700", "700italic", "italic")
            registerFontFamily(ctx, "Ubuntu", "fonts/ubuntu-v15-latin-ext_latin-%s.ttf", "regular", "700", "700italic", "italic")
        }

        fun storeFont(ctx: Context, path: String): String {
            val file = File(ctx.filesDir, path)
            if (!file.parentFile.exists()) {
                file.parentFile.mkdirs()
            }
            ctx.assets.open(path).use {
                val inputStream = it
                file.outputStream().use {
                    val buffer = ByteArray(1024) // Adjust if you want
                    var bytesRead: Int = 0
                    while (bytesRead != -1) {
                        it.write(buffer, 0, bytesRead)
                        bytesRead = inputStream.read(buffer)
                    }
                }
            }
            return file.absolutePath
        }

        fun registerFontFamily(ctx: Context, name: String, pattern: String, regularName: String = "Regular", boldName: String = "Bold", boldItalicName: String = "BoldItalic", italicName: String = "Italic") {
            FontRegistry.getInstance().add(
                    name,
                    FontSpecification.Style.REGULAR,
                    storeFont(ctx, String.format(pattern, regularName)))
            FontRegistry.getInstance().add(
                    name,
                    FontSpecification.Style.BOLDITALIC,
                    storeFont(ctx, String.format(pattern, boldItalicName)))
            FontRegistry.getInstance().add(
                    name,
                    FontSpecification.Style.BOLD,
                    storeFont(ctx, String.format(pattern, boldName)))
            FontRegistry.getInstance().add(
                    name,
                    FontSpecification.Style.ITALIC,
                    storeFont(ctx, String.format(pattern, italicName)))
        }
    }
}