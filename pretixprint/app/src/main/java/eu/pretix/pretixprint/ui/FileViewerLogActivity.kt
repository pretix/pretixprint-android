package eu.pretix.pretixprint.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.pretix.pretixprint.databinding.ActivityFileViewerLogBinding
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

class FileViewerLogActivity : AppCompatActivity() {
    companion object {
        val EXTRA_PATH = "path"
    }

    private lateinit var binding: ActivityFileViewerLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileViewerLogBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val file = File(intent.getStringExtra(EXTRA_PATH))

        val reader = BufferedReader(InputStreamReader(file.inputStream(), Charset.forName("cp1252")))
        val sb = StringBuilder()
        var line: String? = null
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append("\n")
        }
        reader.close()
        val data = sb.toString()

        binding.tvEscposLog.text = data
    }
}