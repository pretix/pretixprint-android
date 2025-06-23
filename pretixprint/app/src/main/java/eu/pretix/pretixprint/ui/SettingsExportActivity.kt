package eu.pretix.pretixprint.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.preference.PreferenceManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.databinding.ActivitySettingsExportBinding
import org.json.JSONObject
import kotlin.math.floor


class SettingsExportActivity : AppCompatActivity() {
    lateinit var binding: ActivitySettingsExportBinding

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsExportBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val json = JSONObject()
        json.put("__pretixprintconf", 1) // version tag
        for (entry in defaultSharedPreferences.all) {
            json.put(entry.key.toString(), entry.value)
        }
        var content = json.toString()

        val displayMetrics = DisplayMetrics()
        windowManager.getDefaultDisplay().getMetrics(displayMetrics)
        val height: Int = displayMetrics.heightPixels
        val width: Int = displayMetrics.widthPixels
        val sizePx = floor((height.coerceAtMost(width) / 3).toDouble() * 2).toInt()

        val qrCodeWriter = QRCodeWriter()
        val encodeHints = mutableMapOf<EncodeHintType, Any?>()
            .apply {
                this[EncodeHintType.MARGIN] = 0
            }
        val bitmapMatrix = try {
            qrCodeWriter.encode(
                content, BarcodeFormat.QR_CODE, sizePx, sizePx, encodeHints
            )
        } catch (_: WriterException) {
            null
        }

        val matrixWidth = bitmapMatrix?.width ?: sizePx
        val matrixHeight = bitmapMatrix?.height ?: sizePx
        val newBitmap = createBitmap(bitmapMatrix?.width ?: sizePx, bitmapMatrix?.height ?: sizePx)

        for (x in 0 until matrixWidth) {
            for (y in 0 until matrixHeight) {
                val shouldColorPixel = bitmapMatrix?.get(x, y) == true
                val pixelColor = if (shouldColorPixel) Color.BLACK else Color.WHITE
                newBitmap[x, y] = pixelColor
            }
        }

        view.findViewById<ImageView>(R.id.ivQrcode).setImageBitmap(newBitmap)
        view.findViewById<TextView>(R.id.tvQrcode).text = content
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                NavUtils.navigateUpFromSameTask(this)
                return true
            }
        }
        return false
    }
}