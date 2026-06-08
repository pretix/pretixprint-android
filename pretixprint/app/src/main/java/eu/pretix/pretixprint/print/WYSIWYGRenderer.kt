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
        return str.replace(Regex("\\{([-a-zA-Z0-9:_]+)\\}")) { match ->
            val key = match.groups[1]!!.value
            // Do not use shortened version
            if (key == "secret") {
                op.getString("secret")
            } else {
                getTextContent(key, null, null)
            }
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
            else -> getTextContent(content, text, textI18n).trim()
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
            registerFontFamily(ctx, "Baloo Bhaijaan", "fonts/baloo-bhaijaan-v6-latin-ext_vietnamese_latin_arabic-%s.ttf", "regular", "regular", "regular", "regular")
            registerFontFamily(ctx, "Open Sans", "fonts/OpenSans-%s.ttf")
            registerFontFamily(ctx, "Droid Serif", "fonts/DroidSerif-%s-webfont.ttf")
            registerFontFamily(ctx, "Titillium", "fonts/titillium-%s-webfont.ttf", "regular", "bold", "bolditalic", "regularitalic")
            registerFontFamily(ctx, "Titillium Upright", "fonts/titillium-%s-webfont.ttf", "regularupright", "boldupright", "boldupright", "regularupright")
            registerFontFamily(ctx, "Titillium Semibold Upright", "fonts/titillium-%s-webfont.ttf", "semiboldupright", "boldupright", "boldupright", "semiboldupright")
            registerFontFamily(ctx, "DejaVu Sans", "fonts/DejaVuSans%s-webfont.ttf", "", "-Bold", "-Oblique", "-BoldOblique")

            val cat_json = ctx.assets.open("fonts/catalog.json").bufferedReader().use { it.readText() }
            val cat = JSONObject(cat_json)
            for (family in cat.keys()) {
                val familyConfig = cat.getJSONObject(family);
                val regularName = familyConfig.getJSONObject("regular").getString("truetype")
                val boldName = if (familyConfig.has("bold")) familyConfig.getJSONObject("bold").getString("truetype") else regularName
                val italicName = if (familyConfig.has("italic")) familyConfig.getJSONObject("italic").getString("truetype") else regularName
                val boldItalicName = if (familyConfig.has("bolditalic")) familyConfig.getJSONObject("bolditalic").getString("truetype") else regularName
                registerFontFamily(ctx, family, "fonts/%s", regularName, boldName, boldItalicName, italicName)
            }
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