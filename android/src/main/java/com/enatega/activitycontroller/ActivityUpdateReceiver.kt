package com.enatega.activitycontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

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

            val helper = NotificationHelper(context)
            // call update to handle everything (fetch image, show notification)
            helper.update(dataJson)

        } catch (e: Exception) {
            Log.e(TAG, "Error in ActivityUpdateReceiver", e)
        }
    }
}
