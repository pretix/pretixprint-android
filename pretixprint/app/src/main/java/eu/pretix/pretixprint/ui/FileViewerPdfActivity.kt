package eu.pretix.pretixprint.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.appcompat.app.AppCompatActivity
import eu.pretix.pretixprint.databinding.ActivityFileViewerPdfBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
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
        bgScope.launch {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(fd)
                val page = renderer.openPage(page)
                val img = Bitmap.createBitmap((page.width / 72.0 * renderDpi).toInt(), (page.height / 72.0 * renderDpi).toInt(), Bitmap.Config.ARGB_8888)
                img.eraseColor(Color.WHITE)
                page.render(img, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)


                val pageCount = renderer.pageCount
                runOnUiThread {
                    binding.tvPdfInfo.text = "page ${page.index + 1} of ${pageCount}, ${(page.width / 72.0 * 25.4).roundTo(4)} x ${(page.height / 72.0 * 25.4).roundTo(4)} cm"
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
        val view = binding.root
        setContentView(view)

        load(0)

        binding.btnNext.setOnClickListener {
            pageIndex = (pageIndex + 1) % numPages
            load(pageIndex)
        }
        binding.btnPrev.setOnClickListener {
            if (pageIndex != 0) {
                pageIndex = (pageIndex - 1) % numPages
                load(pageIndex)
            }
        }
    }
}