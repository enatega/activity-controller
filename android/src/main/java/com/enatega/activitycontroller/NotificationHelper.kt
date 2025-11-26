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
import com.enatega.activitycontroller.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class NotificationHelper(private val ctx: Context) {

    private val CHANNEL_ID = "enatega_live_activity_channel_v1"
    private val CHANNEL_NAME = "Live Activities"

    private var currentActivityId: String? = null

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
                    NotificationManager.IMPORTANCE_LOW
                )
                channel.description = "Live activity notifications"
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun start(params: String): String {
        val json = JSONObject(params)
        val activityId = json.optString("activityId", System.currentTimeMillis().toString())
        currentActivityId = activityId
        showNotification(
            activityId,
            json.optString("title"),
            json.optString("subtitle"),
            json.optString("total"),
            json.optString("orderId"),
            json.optString("status"),
            null
        )
        return activityId
    }

    fun update(params: String) {
        val json = JSONObject(params)
        val activityId = json.optString("activityId")
        if (activityId.isEmpty()) return

        val imageUrl = json.optString("imageUrl", "")
        if (imageUrl.isEmpty()) {
            showNotification(
                activityId,
                json.optString("title"),
                json.optString("subtitle"),
                json.optString("total"),
                json.optString("orderId"),
                json.optString("status"),
                null
            )
        } else {
            thread {
                val bitmap = try {
                    val url = URL(imageUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.doInput = true
                    conn.connect()
                    val input = conn.inputStream
                    val bmp = android.graphics.BitmapFactory.decodeStream(input)
                    input.close()
                    bmp
                } catch (e: Exception) {
                    null
                }
                showNotification(
                    activityId,
                    json.optString("title"),
                    json.optString("subtitle"),
                    json.optString("total"),
                    json.optString("orderId"),
                    json.optString("status"),
                    bitmap
                )
            }
        }
    }

    fun stop() {
        currentActivityId?.let { cancelNotification(it) }
        currentActivityId = null
    }

    private fun getNotificationManager(): NotificationManager {
        return ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun showNotification(
        activityId: String,
        title: String?,
        subtitle: String?,
        total: String?,
        orderId: String?,
        status: String?,
        image: Bitmap?
    ) {
        val nm = getNotificationManager()

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
            compactView.setImageViewResource(R.id.ivIcon, R.drawable.ic_placeholder_image)
            bigView.setImageViewResource(R.id.ivIconBig, R.drawable.ic_placeholder_image)
        }

        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent()
        val pending = PendingIntent.getActivity(
            ctx,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(compactView)
            .setCustomBigContentView(bigView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val notificationId = activityId.hashCode()
        nm.notify(notificationId, builder.build())
    }

    private fun cancelNotification(activityId: String) {
        getNotificationManager().cancel(activityId.hashCode())
    }
}
