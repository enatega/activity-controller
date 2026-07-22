package com.enatega.activitycontroller

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

class YallaLiveActivityService : Service() {
    companion object {
        private const val TAG = "YallaLiveActivity"
        private const val ACTION_START = "com.enatega.activitycontroller.START"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"
        private const val EXTRA_NOTIFICATION = "notification"

        fun start(context: Context, notificationId: Int, notification: Notification) {
            val intent = Intent(context, YallaLiveActivityService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_NOTIFICATION, notification)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, YallaLiveActivityService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_NOTIFICATION, Notification::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_NOTIFICATION)
            }

            if (notificationId != 0 && notification != null) {
                startForeground(notificationId, notification)
                Log.i(TAG, "foreground service owns notification id=$notificationId")
            } else {
                Log.e(TAG, "foreground service start missing notification data")
                stopSelf(startId)
            }
        } else {
            NotificationHelper.getInstance(this).refresh()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
