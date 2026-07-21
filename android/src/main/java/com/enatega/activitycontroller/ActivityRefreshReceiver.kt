package com.enatega.activitycontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActivityRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.getInstance(context).refresh()
    }
}
