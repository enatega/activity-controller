package com.enatega.activitycontroller

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import org.json.JSONObject

class ActivityControllerModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val helper = NotificationHelper(reactContext)

    override fun getName() = "ActivityController"

    @ReactMethod
    fun startLiveActivity(params: String, promise: Promise) {
        try {
            val result = helper.start(params)
            promise.resolve(result)
        } catch (e: Exception) {
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
