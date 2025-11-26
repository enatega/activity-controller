package com.enatega.activitycontroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class NotificationHelper(private val ctx: Context) {

    private val CHANNEL_ID = "enatega_live_activity_channel_v1"
    private val CHANNEL_NAME = "Live Activities"

    init {
        createChannelIfNeeded()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW // low so it's not intrusive
                )
                channel.description = "Live activity notifications"
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun showNotification(
        activityId: String,
        title: String?,
        subtitle: String?,
        total: String?,
        orderId: String?,
        status: String?,
        image: Bitmap?
    ) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val compactView = RemoteViews(ctx.packageName, R.layout.notification_live_activity)
        val bigView = RemoteViews(ctx.packageName, R.layout.notification_live_activity_big)

        compactView.setTextViewText(R.id.tvTitle, title ?: "")
        compactView.setTextViewText(R.id.tvSubtitle, subtitle ?: "")
        compactView.setTextViewText(R.id.tvTotal, total ?: "")
        compactView.setTextViewText(R.id.tvStatus, status ?: "")
        compactView.setTextViewText(R.id.tvOrderId, orderId ?: "")

        bigView.setTextViewText(R.id.tvTitleBig, title ?: "")
        bigView.setTextViewText(R.id.tvSubtitleBig, subtitle ?: "")
        bigView.setTextViewText(R.id.tvTotalBig, total ?: "")
        bigView.setTextViewText(R.id.tvStatusBig, status ?: "")
        bigView.setTextViewText(R.id.tvOrderIdBig, orderId ?: "")

        if (image != null) {
            compactView.setImageViewBitmap(R.id.ivIcon, image)
            bigView.setImageViewBitmap(R.id.ivIconBig, image)
        } else {
            // set placeholder if available (vector)
            compactView.setImageViewResource(R.id.ivIcon, R.drawable.ic_placeholder_image)
            bigView.setImageViewResource(R.id.ivIconBig, R.drawable.ic_placeholder_image)
        }

        // Optional: clicking the notification could open the host app main activity (adjust actionName)
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent()
        val pending = PendingIntent.getActivity(
            ctx,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(compactView)
            .setCustomBigContentView(bigView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val notification: Notification = builder.build()
        // notificationId: create hash from activityId to keep stable int
        val notificationId = activityId.hashCode()
        nm.notify(notificationId, notification)
    }

    fun cancelNotification(activityId: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(activityId.hashCode())
    }
}
