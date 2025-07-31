package eu.pretix.pretixprint.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import eu.pretix.pretixprint.databinding.ActivityFileViewerEscposBinding
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

class FileViewerEscposActivity : AppCompatActivity() {
    companion object {
        val EXTRA_PATH = "path"
    }

    private lateinit var binding: ActivityFileViewerEscposBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileViewerEscposBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.let {
            it.setDisplayUseLogoEnabled(false)
            it.setDisplayHomeAsUpEnabled(true)
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            binding.content
        ) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = insets.left,
                right = insets.right,
                top = 0, // handled by AppBar
                bottom = insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

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

        binding.tvEscposLog.text = data
    }
}