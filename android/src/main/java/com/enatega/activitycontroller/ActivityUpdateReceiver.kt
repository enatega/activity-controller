package com.enatega.activitycontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

/**
 * BroadcastReceiver that expects an Intent with action: com.enatega.activitycontroller.ACTION_UPDATE_ACTIVITY
 * and extras: "data" -> JSON string with fields used by NotificationHelper (title, subtitle, total, orderId, status, imageUrl, activityId)
 *
 * Example (from FCM data-only message handling or host app code):
 * Intent intent = new Intent("com.enatega.activitycontroller.ACTION_UPDATE_ACTIVITY");
 * intent.putExtra("data", "{ \"activityId\":\"...\", \"status\":\"Delivered\" }");
 * context.sendBroadcast(intent);
 */
class ActivityUpdateReceiver : BroadcastReceiver() {
    private val TAG = "ActivityUpdateReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val dataJson = intent.getStringExtra("data") ?: return
            val json = JSONObject(dataJson)
            val activityId = json.optString("activityId")
            val title = json.optString("title", "")
            val subtitle = json.optString("subtitle", "")
            val total = json.optString("total", "")
            val orderId = json.optString("orderId", "")
            val status = json.optString("status", "")
            val imageUrl = json.optString("imageUrl", "")

            val helper = NotificationHelper(context)
            if (imageUrl.isNullOrEmpty()) {
                helper.showNotification(activityId, title, subtitle, total, orderId, status, null)
            } else {
                // fetch image on background thread
                Thread {
                    val bitmap = try {
                        val url = java.net.URL(imageUrl)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 10_000
                        conn.readTimeout = 10_000
                        conn.doInput = true
                        conn.connect()
                        val input = conn.inputStream
                        val bmp = android.graphics.BitmapFactory.decodeStream(input)
                        input.close()
                        bmp
                    } catch (e: Exception) {
                        Log.w(TAG, "image fetch failed", e)
                        null
                    }
                    helper.showNotification(activityId, title, subtitle, total, orderId, status, bitmap)
                }.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in ActivityUpdateReceiver", e)
        }
    }
}
