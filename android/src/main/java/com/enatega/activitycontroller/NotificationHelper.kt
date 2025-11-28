//package com.enatega.activitycontroller
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.PendingIntent
//import android.content.Context
//import android.content.Intent
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.os.Build
//import android.util.Log
//import android.widget.RemoteViews
//import androidx.core.app.NotificationCompat
//import org.json.JSONObject
//import java.net.HttpURLConnection
//import java.net.URL
//import kotlin.concurrent.thread
//
//class NotificationHelper(private val ctx: Context) {
//
//    private val CHANNEL_ID = "enatega_live_activity_channel_v1"
//    private val CHANNEL_NAME = "Live Activities"
//
//    private var currentOrderId: String? = null
//    private var currentParams: JSONObject? = null
//
//    init {
//        createChannelIfNeeded()
//    }
//
//    companion object {
//        @Volatile
//        private var instance: NotificationHelper? = null
//
//        fun getInstance(ctx: Context): NotificationHelper {
//            return instance ?: synchronized(this) {
//                instance ?: NotificationHelper(ctx.applicationContext).also { instance = it }
//            }
//        }
//    }
//
//    private fun createChannelIfNeeded() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
//                val channel = NotificationChannel(
//                    CHANNEL_ID,
//                    CHANNEL_NAME,
//                    NotificationManager.IMPORTANCE_LOW
//                )
//                channel.description = "Live activity notifications"
//                nm.createNotificationChannel(channel)
//            }
//        }
//    }
//
//    // Start - uses JS param names
//    fun start(params: String): String {
//        val json = JSONObject(params)
//
//        // --- ORDER ID PARSING LOGIC (Swift equivalent) ---
//        val orderId = json.optString("orderId")
//        val parts = orderId.split(":::")
//        val displayOrderId = parts.getOrNull(0) ?: ""
//        val realOrderId = parts.getOrNull(1) ?: displayOrderId
//        // ------------------------------------------------
//
//
//        currentOrderId = displayOrderId
//        currentParams = json
//        showNotification(
//
//            itemName = json.optString("itemName"),
//            totalAmount = json.optString("totalAmount"),
//            orderId = displayOrderId,
//            orderStatus = json.optString("orderStatus"),
//            vehicleNumber = json.optString("vehicleNumber"),
//            estimatedDelivery = json.optString("estimatedDelivery"),
//            progress = json.optDouble("progress", 0.0),
//            imageUrl = json.optString("itemImageUrl")
//        )
//
//        // Return a small object (JS expects activityId and pushToken maybe)
//        // For now we return activityId only; JS can accept that object.
//        val res = JSONObject()
//
//        return res.toString()
//    }
//
//    fun update(params: String) {
//        val json = JSONObject(params)
//        val orderId = json.optString("orderId")
//        if (orderId.isEmpty()) return
//
//        Log.d("NOTIFICATION_HELPER", "order id received: $orderId")
//        Log.d("NOTIFICATION_HELPER", "current id received: $currentOrderId")
//        Log.d("NOTIFICATION_HELPER", "current params received: $currentParams")
//
//        if(orderId == currentOrderId){
//            currentParams?.let {
//                showNotification(
//                    itemName = it.optString("itemName"),
//                    totalAmount = it.optString("totalAmount"),
//                    orderId = it.optString("orderId"),
//                    orderStatus = json.optString("orderStatus"),
//                    vehicleNumber = json.optString("vehicleNumber"),
//                    estimatedDelivery = json.optString("estimatedDelivery"),
//                    progress = json.optDouble("progress", 0.0),
//                    imageUrl = it.optString("itemImageUrl")
//                )
//            }
//        }
//
//
//
//
//    }
//
//    fun stop() {
//        currentOrderId?.let { cancelNotification(it) }
//        currentOrderId = null
//        currentParams = null
//    }
//
//    private fun getNotificationManager(): NotificationManager {
//        return ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//    }
//
//    private fun buildNotification(view: RemoteViews): Notification {
//        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent()
//        val pending = PendingIntent.getActivity(
//            ctx,
//            0,
//            intent,
//            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
//        )
//
//        return NotificationCompat.Builder(ctx, CHANNEL_ID)
//            .setSmallIcon(R.drawable.ic_notification)
//            .setCustomContentView(view)
//            .setCustomBigContentView(view)
//            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
//            .setOngoing(true)
//            .setOnlyAlertOnce(true)
//            .setContentIntent(pending)
//            .build()
//    }
//
//    private fun showNotification(
//
//        itemName: String?,
//        totalAmount: String?,
//        orderId: String?,
//        orderStatus: String?,
//        vehicleNumber: String?,
//        estimatedDelivery: String?,
//        progress: Double,
//        imageUrl: String?
//    ) {
//        val nm = getNotificationManager()
//        val view = RemoteViews(ctx.packageName, R.layout.notification_live_activity)
//
//
//
//        // Text fields
//        view.setTextViewText(R.id.tvItemName, itemName ?: "")
//        view.setTextViewText(R.id.tvTotalAmount, if (!totalAmount.isNullOrEmpty()) "Total: $${totalAmount}" else "")
//        view.setTextViewText(R.id.tvOrderStatus, orderStatus ?: "")
//        view.setTextViewText(R.id.tvOrderId, "Order ID: $orderId")  // UI shows only display version
//
//        // Progress 0.0–1.0 or 0–100
//        val progressInt = when {
//            progress <= 1.0 -> (progress * 100).toInt()
//            else -> progress.toInt().coerceIn(0, 100)
//        }
//
//        view.setInt(R.id.progressBar, "setProgress", progressInt)
//
//        // Move driver icon
//        val displayMetrics = ctx.resources.displayMetrics
//        val maxTranslationPx = (displayMetrics.density * 200)
//        val translationPx = (progressInt / 100f) * maxTranslationPx
//
//        try {
//            view.setFloat(R.id.ivDriver, "setTranslationX", translationPx)
//        } catch (_: Exception) {}
//
//        // Load Image
//        if (!imageUrl.isNullOrEmpty()) {
//            thread {
//                val bmp = loadBitmapFromUrl(imageUrl)
//                if (bmp != null) {
//                    view.setImageViewBitmap(R.id.ivItemImage, bmp)
//                } else {
//                    view.setImageViewResource(R.id.ivItemImage, R.drawable.ic_placeholder_image)
//                }
//                nm.notify(orderId.hashCode(), buildNotification(view))
//            }
//        } else {
//            view.setImageViewResource(R.id.ivItemImage, R.drawable.ic_placeholder_image)
//            nm.notify(orderId.hashCode(), buildNotification(view))
//        }
//    }
//
//
//    private fun loadBitmapFromUrl(imageUrl: String): Bitmap? {
//        return try {
//            val url = URL(imageUrl)
//            val conn = url.openConnection() as HttpURLConnection
//            conn.connectTimeout = 10_000
//            conn.readTimeout = 10_000
//            conn.doInput = true
//            conn.connect()
//            val input = conn.inputStream
//            val bmp = BitmapFactory.decodeStream(input)
//            input.close()
//            bmp
//        } catch (e: Exception) {
//            null
//        }
//    }
//
//    private fun cancelNotification(activityId: String) {
//        getNotificationManager().cancel(activityId.hashCode())
//    }
//}


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
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class NotificationHelper private constructor(private val ctx: Context) {

    private val CHANNEL_ID = "enatega_live_activity_channel_v1"
    private val CHANNEL_NAME = "Live Activities"

    private var currentOrderId: String? = null
    private var currentParams: JSONObject? = null

    init {
        createChannelIfNeeded()
        restoreState()   // <-- Load previous state (IMPORTANT)
    }

    companion object {
        @Volatile
        private var instance: NotificationHelper? = null

        fun getInstance(ctx: Context): NotificationHelper {
            return instance ?: synchronized(this) {
                instance ?: NotificationHelper(ctx.applicationContext).also { instance = it }
            }
        }
    }

    // -------------------------------
    // PERSISTENCE LOGIC
    // -------------------------------

    private fun persistState() {
        val prefs = ctx.getSharedPreferences("live_activity", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("currentOrderId", currentOrderId)
            .putString("currentParams", currentParams?.toString())
            .apply()
        Log.d("NOTIFICATION_HELPER", "State persisted: orderId=$currentOrderId")
    }

    private fun restoreState() {
        val prefs = ctx.getSharedPreferences("live_activity", Context.MODE_PRIVATE)
        currentOrderId = prefs.getString("currentOrderId", null)

        prefs.getString("currentParams", null)?.let {
            currentParams = try { JSONObject(it) } catch (_: Exception) { null }
        }

        Log.d("NOTIFICATION_HELPER", "State restored: currentOrderId=$currentOrderId")
        Log.d("NOTIFICATION_HELPER", "State restored: currentParams=$currentParams")
    }

    // -------------------------------
    // START METHOD
    // -------------------------------

    fun start(params: String): String {
        val json = JSONObject(params)

        val orderId = json.optString("orderId")
        val parts = orderId.split(":::")

        val displayOrderId = parts.getOrNull(0) ?: ""
        val realOrderId = parts.getOrNull(1) ?: displayOrderId

        currentOrderId = displayOrderId
        currentParams = json

        persistState() // <-- save values so update() can restore

        showNotification(
            itemName = json.optString("itemName"),
            totalAmount = json.optString("totalAmount"),
            orderId = displayOrderId,
            orderStatus = json.optString("orderStatus"),
            vehicleNumber = json.optString("vehicleNumber"),
            estimatedDelivery = json.optString("estimatedDelivery"),
            progress = json.optDouble("progress", 0.0),
            imageUrl = json.optString("itemImageUrl")
        )

        return JSONObject().toString()
    }

    // -------------------------------
    // UPDATE METHOD
    // -------------------------------

    fun update(params: String) {
        val json = JSONObject(params)
        val orderId = json.optString("orderId")

        if (orderId.isEmpty()) return

        Log.d("NOTIFICATION_HELPER", "update() received orderId: $orderId")
        Log.d("NOTIFICATION_HELPER", "stored currentOrderId: $currentOrderId")
        Log.d("NOTIFICATION_HELPER", "stored currentParams: $currentParams")

        // Restore in case app was restarted
        if (currentOrderId == null || currentParams == null) {
            restoreState()
        }

        if (orderId == currentOrderId) {

            currentParams?.let {
                showNotification(
                    itemName = it.optString("itemName"),
                    totalAmount = it.optString("totalAmount"),
                    orderId = orderId,
                    orderStatus = json.optString("orderStatus"),
                    vehicleNumber = json.optString("vehicleNumber"),
                    estimatedDelivery = json.optString("estimatedDelivery"),
                    progress = json.optDouble("progress", 0.0),
                    imageUrl = it.optString("itemImageUrl")
                )
            }
        }
    }

    // -------------------------------
    // STOP METHOD
    // -------------------------------
    fun stop() {
        currentOrderId?.let { cancelNotification(it) }
        currentOrderId = null
        currentParams = null

        val prefs = ctx.getSharedPreferences("live_activity", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    // -------------------------------
    // NOTIFICATION BUILDING
    // -------------------------------

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
            .setSmallIcon(R.drawable.icon)
            .setCustomContentView(view)
            .setCustomBigContentView(view)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pending)
            .build()
    }

    private fun showNotification(
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

        // text fields
        view.setTextViewText(R.id.tvItemName, itemName ?: "")
        view.setTextViewText(R.id.tvTotalAmount, if (!totalAmount.isNullOrEmpty()) "Total: ${totalAmount}" else "")
        view.setTextViewText(R.id.tvOrderStatus, orderStatus ?: "")
        view.setTextViewText(R.id.tvOrderId, "Order ID: $orderId")

        // progress
        val progressInt = if (progress <= 1.0) (progress * 100).toInt() else progress.toInt().coerceIn(0, 100)
        view.setInt(R.id.progressBar, "setProgress", progressInt)

        // animation
        val displayMetrics = ctx.resources.displayMetrics
        val maxTranslationPx = (displayMetrics.density * 200)
        val translationPx = (progressInt / 100f) * maxTranslationPx

        try {
            view.setFloat(R.id.ivDriver, "setTranslationX", translationPx)
        } catch (_: Exception) {}

        if (!imageUrl.isNullOrEmpty()) {
            thread {
                val bmp = loadBitmapFromUrl(imageUrl)
                if (bmp != null) {
                    view.setImageViewBitmap(R.id.ivItemImage, bmp)
                } else {
                    view.setImageViewResource(R.id.ivItemImage, R.drawable.ic_placeholder_image)
                }
                nm.notify(orderId.hashCode(), buildNotification(view))
            }
        } else {
            view.setImageViewResource(R.id.ivItemImage, R.drawable.ic_placeholder_image)
            nm.notify(orderId.hashCode(), buildNotification(view))
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
