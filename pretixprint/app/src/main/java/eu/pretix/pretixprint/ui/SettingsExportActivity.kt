package eu.pretix.pretixprint.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import eu.pretix.pretixprint.databinding.ActivitySettingsExportBinding
import org.json.JSONObject
import kotlin.math.floor


class SettingsExportActivity : AppCompatActivity() {
    lateinit var binding: ActivitySettingsExportBinding

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsExportBinding.inflate(layoutInflater)
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
                        or WindowInsetsCompat.Type.ime()
            )
            v.updatePadding(
                left = insets.left,
                right = insets.right,
                top = 0, // handled by AppBar
                bottom = insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

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

        binding.ivQrcode.setImageBitmap(newBitmap)
        binding.tvQrcode.text = content
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