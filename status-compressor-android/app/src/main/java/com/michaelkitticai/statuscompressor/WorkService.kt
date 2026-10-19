package com.michaelkitticai.statuscompressor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Keeps the app in the foreground while it compresses, with a progress notification. Without it,
 * switching to another app lets Android (Xiaomi especially) freeze or kill a long x264 encode.
 * The work itself stays in MainViewModel; this service only holds the process up.
 */
class WorkService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 35) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        try {
            // Not ServiceCompat: its type filtering turned mediaProcessing into "none" on Android 16.
            startForeground(ID, build(this, title, progress), type)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start the foreground service", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val ID = 1
        private const val CHANNEL = "work"
        private var running = false
        private var title = "Compressing..."
        private var progress: Float? = null

        /** Shows or updates the notification. Call first while the app is on screen. */
        fun update(context: Context, newTitle: String, newProgress: Float?) {
            title = newTitle
            progress = newProgress
            val app = context.applicationContext
            if (running) {
                app.getSystemService(NotificationManager::class.java).notify(ID, build(app, title, progress))
                return
            }
            try {
                app.startForegroundService(Intent(app, WorkService::class.java))
                running = true
            } catch (e: Exception) {
                // Android doesn't allow starting this from the background; the work still runs.
                Log.w(TAG, "Couldn't start the foreground service", e)
            }
        }

        fun stop(context: Context) {
            if (!running) return
            running = false
            context.applicationContext.stopService(Intent(context.applicationContext, WorkService::class.java))
        }

        private fun build(context: Context, title: String, progress: Float?): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Compressing", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText("Keep going in other apps. Status Compressor will finish in the background.")
                .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
        }
    }
}
