package com.enatega.activitycontroller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import kotlin.math.ceil

class NotificationHelper private constructor(private val context: Context) {
    companion object {
        private const val TAG = "YallaLiveActivity"
        private const val CHANNEL_ID = "yalla_delivery_activity_v2"
        private const val PREFS = "yalla_delivery_activity"
        private const val TERMINAL_TIMEOUT_MS = 30 * 60 * 1000L
        private const val REFRESH_REQUEST_CODE = 1077

        @Volatile private var instance: NotificationHelper? = null

        fun getInstance(context: Context): NotificationHelper =
            instance ?: synchronized(this) {
                instance ?: NotificationHelper(context.applicationContext).also { instance = it }
            }
    }

    private var currentOrderId: String? = null
    private var currentPayload: JSONObject? = null

    init {
        createChannel()
        restoreState()
        Log.i(TAG, "helper initialized sdk=${Build.VERSION.SDK_INT} restoredOrder=$currentOrderId notificationsEnabled=${notificationsEnabled()} channelImportance=${channelImportance()}")
    }

    fun start(rawPayload: String): String {
        val payload = JSONObject(rawPayload)
        val orderId = payload.getString("orderId")
        Log.i(TAG, "start requested order=$orderId currentOrder=$currentOrderId payloadBytes=${rawPayload.toByteArray().size}")
        if (currentOrderId != null && currentOrderId != orderId) {
            val existingOrderId = currentOrderId!!
            if (isNotificationActive(existingOrderId)) {
                Log.w(TAG, "start skipped because active notification exists for order=$existingOrderId")
                return debugResult(existingOrderId, alreadyRunning = true).toString()
            }

            Log.w(TAG, "clearing stale session for order=$existingOrderId because no active notification exists")
            clearPersistedState()
        }

        currentOrderId = orderId
        currentPayload = payload
        persistState()
        showNotification(payload)
        return debugResult(orderId, alreadyRunning = false).toString()
    }

    fun update(rawPayload: String) {
        val update = JSONObject(rawPayload)
        if (currentOrderId == null || currentPayload == null) restoreState()
        if (update.optString("orderId") != currentOrderId) return

        val merged = currentPayload ?: JSONObject()
        if (update.has("displayOrderId")) merged.put("displayOrderId", update.optString("displayOrderId"))
        merged.put("state", update.optJSONObject("state") ?: update)
        merged.put("terminal", update.optBoolean("terminal", false))
        currentPayload = merged
        persistState()
        showNotification(merged)
    }

    fun stop() {
        YallaLiveActivityService.stop(context)
        currentOrderId?.let { notificationManager().cancel(it.hashCode()) }
        cancelRefresh()
        currentOrderId = null
        currentPayload = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun isRunning(): Boolean {
        if (currentOrderId == null) restoreState()
        return currentOrderId != null
    }

    fun refresh() {
        if (currentOrderId == null || currentPayload == null) restoreState()
        currentPayload?.let(::showNotification)
    }

    private fun showNotification(payload: JSONObject) {
        val orderId = payload.optString("orderId")
        val displayOrderId = payload.optString("displayOrderId", orderId)
        val state = payload.optJSONObject("state") ?: payload
        val status = state.optString("status", "PENDING").uppercase()
        val language = state.optString("language", "en").takeIf { it in listOf("en", "ar", "he") } ?: "en"
        val riderName = state.optString("riderName")
        val riderPhone = state.optString("riderPhone")
        val arrivalEpoch = state.optLong("estimatedArrivalEpoch", 0L)
        val etaUpdatedAtEpoch = state.optLong("etaUpdatedAtEpoch", 0L)
        val terminal = payload.optBoolean("terminal", status in listOf("DELIVERED", "COMPLETED", "CANCELLED", "CANCELLEDBYREST"))
        val cancelled = status.contains("CANCEL")
        val showEta = status == "PICKED" && riderName.isNotBlank() && arrivalEpoch > 0L && !terminal
        val etaIsActive = arrivalEpoch * 1000L > System.currentTimeMillis()
        val notificationId = orderId.hashCode()

        Log.i(
            TAG,
            "building notification order=$orderId id=$notificationId status=$status terminal=$terminal " +
                "notificationsEnabled=${notificationsEnabled()} channelImportance=${channelImportance()}"
        )

        val compact = RemoteViews(context.packageName, R.layout.notification_live_activity)
        val expanded = RemoteViews(context.packageName, R.layout.notification_live_activity_big)
        val copy = Copy(language)
        val headline = copy.headline(status)
        val eta = etaText(arrivalEpoch, language)
        val layoutDirection = if (language in listOf("ar", "he")) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

        compact.setInt(R.id.compactRoot, "setLayoutDirection", layoutDirection)
        expanded.setInt(R.id.expandedRoot, "setLayoutDirection", layoutDirection)

        compact.setTextViewText(R.id.tvHeadline, headline)
        compact.setTextColor(R.id.tvHeadline, if (cancelled) Color.rgb(235, 87, 87) else Color.WHITE)
        compact.setTextViewText(R.id.tvOrderIdCompact, "#$displayOrderId")
        compact.setViewVisibility(R.id.etaGroupCompact, if (showEta) View.VISIBLE else View.GONE)
        compact.setViewVisibility(R.id.tvOrderIdCompact, if (showEta) View.GONE else View.VISIBLE)
        compact.setTextViewText(R.id.tvEta, eta)
        expanded.setTextViewText(R.id.tvStatusBig, headline)
        expanded.setTextColor(R.id.tvStatusBig, if (cancelled) Color.rgb(235, 87, 87) else Color.rgb(180, 181, 184))
        expanded.setViewVisibility(R.id.etaGroupBig, if (showEta) View.VISIBLE else View.GONE)
        expanded.setViewVisibility(R.id.tvOrderIdBig, if (showEta) View.GONE else View.VISIBLE)
        expanded.setTextViewText(R.id.tvEtaBig, eta)
        expanded.setTextViewText(R.id.tvOrderIdBig, "#$displayOrderId")
        if (showEta) {
            val ring = etaRing(arrivalEpoch, etaUpdatedAtEpoch)
            compact.setImageViewBitmap(R.id.etaRingCompact, ring)
            expanded.setImageViewBitmap(R.id.etaRingBig, ring)
        }

        bindStages(expanded, status, copy, cancelled)
        val showRider = riderName.isNotBlank() && stageIndex(status) >= 2
        expanded.setViewVisibility(R.id.riderPanel, if (showRider) View.VISIBLE else View.GONE)
        expanded.setTextViewText(R.id.tvRiderName, "${copy.meet} $riderName")
        expanded.setTextViewText(R.id.tvRiderSubtitle, copy.yourRider)
        expanded.setViewVisibility(R.id.btnCall, if (showRider && riderPhone.isNotBlank()) View.VISIBLE else View.GONE)

        val trackingIntent = deepLinkIntent("yalla-delivery-new://order-tracking?id=$orderId")
        val chatIntent = deepLinkIntent("yalla-delivery-new://order-tracking?id=$orderId&chat=courier", 1)
        compact.setOnClickPendingIntent(R.id.compactRoot, trackingIntent)
        expanded.setOnClickPendingIntent(R.id.expandedRoot, trackingIntent)
        expanded.setOnClickPendingIntent(R.id.btnChat, chatIntent)
        if (riderPhone.isNotBlank()) {
            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$riderPhone")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            expanded.setOnClickPendingIntent(
                R.id.btnCall,
                PendingIntent.getActivity(context, 2, dial, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(trackingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(!terminal)
            .setAutoCancel(terminal)
            .setColor(Color.rgb(170, 200, 16))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply { if (terminal) setTimeoutAfter(TERMINAL_TIMEOUT_MS) }
            .build()

        if (!terminal) {
            notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        }

        try {
            if (terminal) {
                YallaLiveActivityService.stop(context)
            }
            notificationManager().notify(notificationId, notification)
            if (!terminal) {
                runCatching {
                    YallaLiveActivityService.start(context, notificationId, notification)
                }.onFailure { error ->
                    Log.e(TAG, "foreground service could not start; ongoing notification remains posted", error)
                }
            }
            Log.i(TAG, "notify completed order=$orderId id=$notificationId activeAfterNotify=${isNotificationActive(orderId)}")
        } catch (error: Exception) {
            Log.e(TAG, "notify failed order=$orderId id=$notificationId", error)
            throw error
        }
        if (terminal) {
            cancelRefresh()
            currentOrderId = null
            currentPayload = null
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        } else if (showEta && etaIsActive) {
            scheduleRefresh()
        } else {
            cancelRefresh()
        }
    }

    private fun bindStages(view: RemoteViews, status: String, copy: Copy, cancelled: Boolean) {
        val dotIds = intArrayOf(R.id.stagePendingDot, R.id.stageAcceptedDot, R.id.stageAssignedDot, R.id.stagePickedDot, R.id.stageDeliveredDot)
        val iconIds = intArrayOf(R.id.stagePendingIcon, R.id.stageAcceptedIcon, R.id.stageAssignedIcon, R.id.stagePickedIcon, R.id.stageDeliveredIcon)
        val labelIds = intArrayOf(R.id.stagePendingLabel, R.id.stageAcceptedLabel, R.id.stageAssignedLabel, R.id.stagePickedLabel, R.id.stageDeliveredLabel)
        val railIds = intArrayOf(R.id.stageRail1, R.id.stageRail2, R.id.stageRail3, R.id.stageRail4)
        val current = stageIndex(status)
        for (index in dotIds.indices) {
            val completed = !cancelled && (current == 4 || index < current)
            val color = when {
                cancelled -> Color.rgb(105, 107, 112)
                completed -> Color.rgb(170, 200, 16)
                index == current -> Color.rgb(255, 202, 13)
                else -> Color.rgb(105, 107, 112)
            }
            view.setImageViewResource(dotIds[index], if (completed) R.drawable.stage_circle_filled else R.drawable.stage_circle_outline)
            view.setInt(dotIds[index], "setColorFilter", color)
            view.setInt(iconIds[index], "setColorFilter", if (completed) Color.BLACK else color)
            view.setTextViewText(labelIds[index], copy.stage(index))
            view.setTextColor(labelIds[index], if (!cancelled && index <= current) Color.WHITE else Color.rgb(105, 107, 112))
        }
        for (index in railIds.indices) {
            val color = if (!cancelled && index < current) Color.rgb(170, 200, 16) else Color.rgb(105, 107, 112)
            view.setInt(railIds[index], "setBackgroundColor", color)
        }
    }

    private fun stageIndex(status: String) = when (status) {
        "ACCEPTED" -> 1
        "ASSIGNED" -> 2
        "PICKED" -> 3
        "DELIVERED", "COMPLETED" -> 4
        else -> 0
    }

    private fun etaText(epoch: Long, language: String): String {
        if (epoch <= 0) return "--"
        val minutes = ceil((epoch * 1000L - System.currentTimeMillis()).coerceAtLeast(0L) / 60000.0).toInt()
        return when (language) {
            "ar" -> "$minutes د"
            "he" -> "$minutes דק׳"
            else -> "$minutes min"
        }
    }

    private fun etaRing(arrivalEpoch: Long, updatedAtEpoch: Long): Bitmap {
        val size = 72
        val stroke = 9f
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        val bounds = RectF(stroke, stroke, size - stroke, size - stroke)
        paint.color = Color.rgb(55, 56, 59)
        canvas.drawArc(bounds, -90f, 360f, false, paint)

        val now = System.currentTimeMillis() / 1000L
        val start = updatedAtEpoch.takeIf { it in 1 until arrivalEpoch } ?: (arrivalEpoch - 30 * 60L)
        val duration = (arrivalEpoch - start).coerceAtLeast(1L)
        val remaining = ((arrivalEpoch - now).coerceIn(0L, duration)).toFloat() / duration.toFloat()
        paint.color = Color.rgb(255, 202, 13)
        canvas.drawArc(bounds, -90f, 360f * remaining, false, paint)
        return bitmap
    }

    private fun deepLinkIntent(url: String, requestCode: Int = 0): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scheduleRefresh() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 60_000L,
            refreshPendingIntent()
        )
    }

    private fun cancelRefresh() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(refreshPendingIntent())
    }

    private fun refreshPendingIntent(): PendingIntent {
        val intent = Intent(context, ActivityRefreshReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun persistState() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("orderId", currentOrderId)
            .putString("payload", currentPayload?.toString())
            .apply()
    }

    private fun restoreState() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        currentOrderId = prefs.getString("orderId", null)
        currentPayload = prefs.getString("payload", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Delivery progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live delivery status and estimated arrival"
                setShowBadge(false)
            }
            notificationManager().createNotificationChannel(channel)
            Log.i(TAG, "notification channel ready id=$CHANNEL_ID importance=${channelImportance()}")
        }
    }

    private fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun channelImportance(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationManager().getNotificationChannel(CHANNEL_ID)?.importance ?: -1
    } else {
        NotificationManager.IMPORTANCE_DEFAULT
    }

    private fun isNotificationActive(orderId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return runCatching {
            notificationManager().activeNotifications.any { it.id == orderId.hashCode() }
        }.onFailure { error ->
            Log.e(TAG, "failed to query active notifications for order=$orderId", error)
        }.getOrDefault(false)
    }

    private fun debugResult(orderId: String, alreadyRunning: Boolean): JSONObject = JSONObject()
        .put("activityId", "android:$orderId")
        .put("pushToken", "")
        .put("alreadyRunning", alreadyRunning)
        .put("notificationPosted", isNotificationActive(orderId))
        .put("notificationsEnabled", notificationsEnabled())
        .put("channelImportance", channelImportance())
        .put("sdkInt", Build.VERSION.SDK_INT)

    private fun clearPersistedState() {
        currentOrderId = null
        currentPayload = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun notificationManager() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private class Copy(private val language: String) {
        private val stages = mapOf(
            "en" to listOf("Pending", "Accepted", "Assigned", "Picked", "Delivered"),
            "ar" to listOf("قيد الانتظار", "مقبول", "تم التعيين", "تم الاستلام", "تم التوصيل"),
            "he" to listOf("בהמתנה", "אושר", "שויך", "נאסף", "נמסר")
        )
        fun stage(index: Int) = (stages[language] ?: stages.getValue("en"))[index]
        fun headline(status: String): String {
            val values = mapOf(
                "en" to mapOf("PENDING" to "Order received", "ACCEPTED" to "Preparing your order", "ASSIGNED" to "Rider assigned", "PICKED" to "Almost here!", "DELIVERED" to "Delivered!", "COMPLETED" to "Delivered!", "CANCELLED" to "Order cancelled", "CANCELLEDBYREST" to "Order cancelled"),
                "ar" to mapOf("PENDING" to "تم استلام الطلب", "ACCEPTED" to "يتم تحضير طلبك", "ASSIGNED" to "تم تعيين السائق", "PICKED" to "اقتربنا!", "DELIVERED" to "تم التوصيل!", "COMPLETED" to "تم التوصيل!", "CANCELLED" to "تم إلغاء الطلب", "CANCELLEDBYREST" to "تم إلغاء الطلب"),
                "he" to mapOf("PENDING" to "ההזמנה התקבלה", "ACCEPTED" to "מכינים את ההזמנה", "ASSIGNED" to "שליח שויך", "PICKED" to "כמעט הגענו!", "DELIVERED" to "נמסר!", "COMPLETED" to "נמסר!", "CANCELLED" to "ההזמנה בוטלה", "CANCELLEDBYREST" to "ההזמנה בוטלה")
            )
            return values[language]?.get(status) ?: values.getValue("en")[status] ?: "Order update"
        }
        val arrivingAt get() = if (language == "ar") "الوصول في" else if (language == "he") "הגעה בשעה" else "Arriving at"
        val meet get() = if (language == "ar") "تعرّف على" else if (language == "he") "הכירו את" else "Meet"
        val yourRider get() = if (language == "ar") "السائق الخاص بك" else if (language == "he") "השליח שלך" else "Your rider"
    }
}
