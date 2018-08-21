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



class OrderPositionContentProvider(private val order: JSONObject, private val op: JSONObject) : ContentProvider {
    override fun getTextContent(content: String?, text: String?): String {
        if (content == "other") {
            return text ?: ""
        } else if (op.has("pdf_data") && op.getJSONObject("pdf_data").has(content)) {
            return op.getJSONObject("pdf_data").getString(content)
        } else {
            return "???"
        }
    }

    override fun getBarcodeContent(content: String?): String {
        return when(content) {
            "secret" -> op.getString("secret")
            "pseudonymization_id" -> op.getString("pseudonymization_id")
            else -> "???"
        }
    }

}


class Renderer(private val layout: JSONArray, private val order: JSONObject, private val positionIndex: Int, private val background: InputStream?, private val ctx: Context) {
    fun writePDF(outFile: File) {
        val positions = order.getJSONArray("positions")
        val posList = emptyList<ContentProvider>().toMutableList()
        posList.add(OrderPositionContentProvider(order, positions.getJSONObject(positionIndex)))
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
            registerFontFamily(ctx, "Open Sans", "fonts/OpenSans-%s.ttf")
            registerFontFamily(ctx, "Noto Sans", "fonts/NotoSans-%s-webfont.ttf")
            registerFontFamily(ctx, "Roboto", "fonts/Roboto-%s.ttf")
            registerFontFamily(ctx, "Droid Serif", "fonts/DroidSerif-%s-webfont.ttf")
            registerFontFamily(ctx, "Fira Sans", "fonts/firasans-%s-webfont.ttf")
            registerFontFamily(ctx, "Lato", "fonts/Lato-%s.ttf")
            registerFontFamily(ctx, "Vollkorn", "fonts/Vollkorn-%s.ttf")
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

        fun registerFontFamily(ctx: Context, name: String, pattern: String) {
            FontRegistry.getInstance().add(
                    name,
                    FontSpecification.Style.REGULAR,
                    storeFont(ctx, String.format(pattern, "Regular")))
            FontRegistry.getInstance().add(
                    name,
                    FontSpecification.Style.BOLDITALIC,
                    storeFont(ctx, String.format(pattern, "BoldItalic")))
            FontRegistry.getInstance().add(
                    name,
                    FontSpecification.Style.BOLD,
                    storeFont(ctx, String.format(pattern, "Bold")))
            FontRegistry.getInstance().add(
                    name,
                    FontSpecification.Style.ITALIC,
                    storeFont(ctx, String.format(pattern, "Italic")))
        }
    }
}