package eu.pretix.pretixprint.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import eu.pretix.pretixprint.connections.SystemConnection
import eu.pretix.pretixprint.databinding.ActivitySystemPrintBinding
import eu.pretix.pretixprint.print.AbstractPrintService
import java.io.File


class SystemPrintActivity : AppCompatActivity() {

    companion object {
        const val INTENT_EXTRA_CALLER = "caller"
    }

    private lateinit var binding: ActivitySystemPrintBinding
    var hadLaunchedPrint = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove print notification that brought the user to this activity
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        notificationManagerCompat.cancel(AbstractPrintService.ONGOING_NOTIFICATION_ID)

        binding = ActivitySystemPrintBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tmpfile = intent.extras?.get("tmpfile") as File
        val pagenum = intent.extras?.get("pagenum") as Int
        val type = intent.extras?.get("type") as String

        SystemConnection().print(tmpfile, pagenum, this, type, null)
        hadLaunchedPrint = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && hadLaunchedPrint) {
            val caller = intent.extras?.get(INTENT_EXTRA_CALLER) as Class<*>
            val stopIntent = Intent(this, caller)
            stopIntent.action = AbstractPrintService.ACTION_STOP_SERVICE
            startService(stopIntent)
            finish()
        }
    }

}