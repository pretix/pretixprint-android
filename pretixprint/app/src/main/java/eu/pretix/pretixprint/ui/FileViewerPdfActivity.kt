package eu.pretix.pretixprint.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.appcompat.app.AppCompatActivity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import eu.pretix.pretixprint.R
import kotlinx.android.synthetic.main.activity_file_viewer_pdf.*
import org.jetbrains.anko.doAsync
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt

class FileViewerPdfActivity : AppCompatActivity() {
    companion object {
        val EXTRA_PATH = "path"
    }

    var numPages = 1
    var pageIndex = 0

    fun Double.roundTo(numFractionDigits: Int): Double {
        val factor = 10.0.pow(numFractionDigits.toDouble())
        return (this * factor).roundToInt() / factor
    }

    fun load(page: Int) {
        tvPdfInfo.text = "Loading…"
        pageIndex = page
        val file = File(intent.getStringExtra(EXTRA_PATH))
        doAsync {
            if (Build.VERSION.SDK_INT >= 21) {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(fd)
                val page = renderer.openPage(page)
                val img = Bitmap.createBitmap((page.width / 72.0 * 600).toInt(), (page.height / 72.0 * 600).toInt(), Bitmap.Config.ARGB_8888)
                img.eraseColor(Color.WHITE)
                page.render(img, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)


                val pageCount = renderer.pageCount
                runOnUiThread {
                    tvPdfInfo.text = "page ${page.index + 1} of ${pageCount}, ${(page.width / 72.0 * 25.4).roundTo(4)} x ${(page.height / 72.0 * 25.4).roundTo(4)} cm"
                }
                numPages = renderer.pageCount

                draw(img)
                page.close()
                renderer.close()
                fd.close()
            } else {
                val doc = PDDocument.load(file.inputStream())
                val renderer = PDFRenderer(doc)
                val img = renderer.renderImageWithDPI(page, 600f)
                img.eraseColor(Color.WHITE)
                runOnUiThread {
                    tvPdfInfo.text = "page ${page + 1} of ${doc.numberOfPages}"
                }
                numPages = doc.numberOfPages
                draw(img)
            }
        }
    }

    fun draw(img: Bitmap) {
        runOnUiThread {
            img.setHasAlpha(false)
            imageView.setImageDrawable(BitmapDrawable(resources, img))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_viewer_pdf)

        load(0)

        btnNext.setOnClickListener {
            pageIndex = (pageIndex + 1) % numPages
            load(pageIndex)
        }
        btnPrev.setOnClickListener {
            if (pageIndex != 0) {
                pageIndex = (pageIndex - 1) % numPages
                load(pageIndex)
            }
        }
    }
}