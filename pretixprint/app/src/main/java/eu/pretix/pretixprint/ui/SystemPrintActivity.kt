package eu.pretix.pretixprint.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.connections.SystemConnection
import eu.pretix.pretixprint.print.AbstractPrintService
import java.io.File


class SystemPrintActivity : AppCompatActivity() {

    companion object {
        const val INTENT_EXTRA_CALLER = "caller"
        const val INTENT_EXTRA_FILE = "tmpfile"
        const val INTENT_EXTRA_PAGENUM = "pagenum"
        const val INTENT_EXTRA_PAGEGROUPS = "pagegroups"
        const val INTENT_EXTRA_TYPE = "type"
    }

    var hadLaunchedPrint = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove print notification that brought the user to this activity
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        notificationManagerCompat.cancel(AbstractPrintService.ONGOING_NOTIFICATION_ID)

        setContentView(R.layout.activity_system_print)

        val tmpfile = intent.extras?.get(INTENT_EXTRA_FILE) as File
        val pagenum = intent.extras?.get(INTENT_EXTRA_PAGENUM) as Int
        val pagegroups = intent.extras?.get(INTENT_EXTRA_PAGEGROUPS) as IntArray
        val type = intent.extras?.get(INTENT_EXTRA_TYPE) as String

        SystemConnection().print(tmpfile, pagenum, pagegroups.toList(), this, type, null)
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