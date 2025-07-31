package eu.pretix.pretixprint.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.databinding.ActivityFileViewerPdfBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

class FileViewerPdfActivity : AppCompatActivity() {
    val bgScope = CoroutineScope(Dispatchers.IO)

    companion object {
        val EXTRA_PATH = "path"
    }

    private lateinit var binding: ActivityFileViewerPdfBinding
    var numPages = 1
    var pageIndex = 0

    fun Double.roundTo(numFractionDigits: Int): Double {
        val factor = 10.0.pow(numFractionDigits.toDouble())
        return (this * factor).roundToInt() / factor
    }

    fun load(page: Int) {
        val renderDpi = 300f  // 600 will crash on A4 paper size on most devices

        binding.tvPdfInfo.text = "Loading…"
        pageIndex = page
        val file = File(intent.getStringExtra(EXTRA_PATH))

        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val name = "${f.format(Date(file.lastModified()))} (${file.name.split(".")[1]})"
        supportActionBar?.title = name

        bgScope.launch {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(fd)
                val page = renderer.openPage(pageIndex)
                val img = Bitmap.createBitmap((page.width / 72.0 * renderDpi).toInt(), (page.height / 72.0 * renderDpi).toInt(), Bitmap.Config.ARGB_8888)
                img.eraseColor(Color.WHITE)
                page.render(img, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)


                val pageCount = renderer.pageCount
                runOnUiThread {
                    binding.tvPdfInfo.text = "page ${pageIndex + 1} of ${pageCount}, ${(page.width / 72.0 * 25.4).roundTo(4)} x ${(page.height / 72.0 * 25.4).roundTo(4)} cm"
                }
                numPages = renderer.pageCount

                draw(img)
                page.close()
                renderer.close()
                fd.close()
        }
    }

    fun draw(img: Bitmap) {
        runOnUiThread {
            img.setHasAlpha(false)
            binding.imageView.setImageDrawable(BitmapDrawable(resources, img))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileViewerPdfBinding.inflate(layoutInflater)
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

        load(0)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_file_viewer_pdf, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_prev -> {
            if (pageIndex != 0) {
                pageIndex = (pageIndex - 1) % numPages
                load(pageIndex)
            }
            true
        }
        R.id.action_next -> {
            pageIndex = (pageIndex + 1) % numPages
            load(pageIndex)
            true
        }
        android.R.id.home -> {
            NavUtils.navigateUpFromSameTask(this)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}