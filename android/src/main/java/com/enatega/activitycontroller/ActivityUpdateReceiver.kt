package com.enatega.activitycontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import org.json.JSONObject

class ActivityUpdateReceiver : BroadcastReceiver() {

    private val TAG = "ActivityUpdateReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d(TAG, "Received intent: ${intent.action}")

            val extras: Bundle? = intent.extras
            if (extras == null || extras.isEmpty) {
                Log.w(TAG, "No extras found in the intent")
                return
            }

            val orderDataString = extras.getString("orderData")
            Log.d(TAG, "Received orderData: $orderDataString")

            if (orderDataString.isNullOrEmpty()) {
                Log.w(TAG, "orderData is empty, nothing to send")
                return
            }

            // Parse orderData JSON safely
            val orderDataJson = try {
                JSONObject(orderDataString)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse orderData JSON", e)
                return
            }

            Log.d(TAG, "Parsed orderData JSON: $orderDataJson")

            NotificationHelper.getInstance(context).update(orderDataJson.toString())

        } catch (e: Exception) {
            Log.e(TAG, "Error in ActivityUpdateReceiver", e)
        }
    }

}
