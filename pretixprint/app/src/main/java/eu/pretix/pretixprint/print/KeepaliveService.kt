package eu.pretix.pretixprint.print

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import eu.pretix.pretixprint.R
import eu.pretix.pretixprint.ui.SettingsActivity

class KeepaliveService : Service() {

    companion object {
        val CHANNEL_ID = "pretixprint_keepalive_channel"
        val ONGOING_NOTIFICATION_ID = 43

        fun start(context: Context) {
            val intent = Intent(context, KeepaliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private fun _startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_keepalive),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = getString(R.string.notification_channel_keepalive_description)
            channel.setSound(null, null)
            channel.enableVibration(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val startBaseActivity = Intent(this, SettingsActivity::class.java)
        startBaseActivity.action = Intent.ACTION_MAIN
        startBaseActivity.addCategory(Intent.CATEGORY_LAUNCHER)
        startBaseActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val keepAliveNotification: Notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.keepalive_notification))
                .setSmallIcon(R.drawable.ic_stat_print)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this,
                        0,
                        startBaseActivity,
                        if (Build.VERSION.SDK_INT >= 23) {
                            PendingIntent.FLAG_IMMUTABLE
                        } else {
                            0
                        }
                    )
                )
                .build()
        this.startForeground(ONGOING_NOTIFICATION_ID, keepAliveNotification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _startForeground()
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()
        _startForeground()
    }
}