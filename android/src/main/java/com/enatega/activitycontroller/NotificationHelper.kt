package com.enatega.activitycontroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
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

    // Start - uses JS param names
    fun start(params: String): String {
        val json = JSONObject(params)
        val activityId = json.optString("activityId", System.currentTimeMillis().toString())
        currentActivityId = activityId

        showNotification(
            activityId = activityId,
            itemName = json.optString("itemName"),
            totalAmount = json.optString("totalAmount"),
            orderId = json.optString("orderId"),
            orderStatus = json.optString("orderStatus"),
            vehicleNumber = json.optString("vehicleNumber"),
            estimatedDelivery = json.optString("estimatedDelivery"),
            progress = json.optDouble("progress", 0.0),
            imageUrl = json.optString("itemImageUrl")
        )

        // Return a small object (JS expects activityId and pushToken maybe)
        // For now we return activityId only; JS can accept that object.
        val res = JSONObject()
        res.put("activityId", activityId)
        res.put("pushToken", activityId) // placeholder; replace if you generate a token
        return res.toString()
    }

    fun update(params: String) {
        val json = JSONObject(params)
        val activityId = json.optString("activityId")
        if (activityId.isEmpty()) return

        showNotification(
            activityId = activityId,
            itemName = json.optString("itemName"),
            totalAmount = json.optString("totalAmount"),
            orderId = json.optString("orderId"),
            orderStatus = json.optString("orderStatus"),
            vehicleNumber = json.optString("vehicleNumber"),
            estimatedDelivery = json.optString("estimatedDelivery"),
            progress = json.optDouble("progress", 0.0),
            imageUrl = json.optString("itemImageUrl")
        )
    }

    fun stop() {
        currentActivityId?.let { cancelNotification(it) }
        currentActivityId = null
    }

    private fun getNotificationManager(): NotificationManager {
        return ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun buildNotification(view: RemoteViews): Notification {
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent()
        val pending = PendingIntent.getActivity(
            ctx,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(view)
            .setCustomBigContentView(view)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
    }

    private fun showNotification(
        activityId: String,
        itemName: String?,
        totalAmount: String?,
        orderId: String?,
        orderStatus: String?,
        vehicleNumber: String?,
        estimatedDelivery: String?,
        progress: Double,
        imageUrl: String?
    ) {
        val nm = getNotificationManager()
        val view = RemoteViews(ctx.packageName, R.layout.notification_live_activity)

        // --- ORDER ID PARSING LOGIC (Swift equivalent) ---
        val parts = orderId?.split(":::") ?: listOf("")
        val displayOrderId = parts.getOrNull(0) ?: ""
        val realOrderId = parts.getOrNull(1) ?: displayOrderId
        // ------------------------------------------------

        // Text fields
        view.setTextViewText(R.id.tvItemName, itemName ?: "")
        view.setTextViewText(R.id.tvTotalAmount, if (!totalAmount.isNullOrEmpty()) "Total: $${totalAmount}" else "")
        view.setTextViewText(R.id.tvOrderStatus, orderStatus ?: "")
        view.setTextViewText(R.id.tvOrderId, "Order ID: $displayOrderId")  // UI shows only display version

        // Progress 0.0–1.0 or 0–100
        val progressInt = when {
            progress <= 1.0 -> (progress * 100).toInt()
            else -> progress.toInt().coerceIn(0, 100)
        }

        view.setInt(R.id.progressBar, "setProgress", progressInt)

        // Move driver icon
        val displayMetrics = ctx.resources.displayMetrics
        val maxTranslationPx = (displayMetrics.density * 200)
        val translationPx = (progressInt / 100f) * maxTranslationPx

        try {
            view.setFloat(R.id.ivDriver, "setTranslationX", translationPx)
        } catch (_: Exception) {}

        // Load Image
        if (!imageUrl.isNullOrEmpty()) {
            thread {
                val bmp = loadBitmapFromUrl(imageUrl)
                if (bmp != null) {
                    view.setImageViewBitmap(R.id.ivItemImage, bmp)
                } else {
                    view.setImageViewResource(R.id.ivItemImage, R.drawable.ic_placeholder_image)
                }
                nm.notify(activityId.hashCode(), buildNotification(view))
            }
        } else {
            view.setImageViewResource(R.id.ivItemImage, R.drawable.ic_placeholder_image)
            nm.notify(activityId.hashCode(), buildNotification(view))
        }
    }


    private fun loadBitmapFromUrl(imageUrl: String): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doInput = true
            conn.connect()
            val input = conn.inputStream
            val bmp = BitmapFactory.decodeStream(input)
            input.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun cancelNotification(activityId: String) {
        getNotificationManager().cancel(activityId.hashCode())
    }
}
