package com.enatega.activitycontroller

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import org.json.JSONObject

class ActivityControllerModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val helper = NotificationHelper.getInstance(reactContext)

    override fun getName() = "ActivityController"

    @ReactMethod
    fun startLiveActivity(params: String, promise: Promise) {
        Log.d("LiveActivityModule", "startLiveActivity called with params: $params")

        try {
            val result = helper.start(params)
            Log.d("LiveActivityModule", "startLiveActivity result: $result")
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e("LiveActivityModule", "startLiveActivity error", e)
            promise.reject("START_ERROR", e)
        }
    }

    @ReactMethod
    fun updateLiveActivity(params: String, promise: Promise) {
        try {
            helper.update(params)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("UPDATE_ERROR", e)
        }
    }

    @ReactMethod
    fun stopLiveActivity(promise: Promise) {
        try {
            helper.stop()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("STOP_ERROR", e)
        }
    }
}
