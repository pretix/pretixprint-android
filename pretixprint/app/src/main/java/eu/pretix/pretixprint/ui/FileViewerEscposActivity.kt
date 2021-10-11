package eu.pretix.pretixprint.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.pretix.pretixprint.R
import kotlinx.android.synthetic.main.activity_file_viewer_escpos.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

class FileViewerEscposActivity : AppCompatActivity() {
    companion object {
        val EXTRA_PATH = "path"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_viewer_escpos)
        val file = File(intent.getStringExtra(EXTRA_PATH))

        val reader = BufferedReader(InputStreamReader(file.inputStream(), Charset.forName("cp1252")))
        val sb = StringBuilder()
        var line: String? = null
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append("\n")
        }
        reader.close()
        val data = sb.toString()
                .replace("(\\p{C})".toRegex()) { f ->
                    val char = f.groupValues[0][0]
                    when (char.toInt()) {
                        0x0A -> "\\n\n"
                        0x0D -> "\\r"
                        0x09 -> "\\t"
                        0x08 -> "<BS>"
                        0x1B -> "<ESC>"
                        0x1D -> "<GS>"
                        else -> "<0x${Integer.toHexString(char.toInt())}>"
                    }
                }

        tvEscposLog.text = data
    }
}