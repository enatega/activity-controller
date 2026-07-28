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
        Log.i("DeliveryActivity", "React Native startLiveActivity invoked payloadBytes=${params.toByteArray().size}")

        try {
            val result = helper.start(params)
            Log.i("DeliveryActivity", "React Native startLiveActivity result=$result")
            promise.resolve(result)
        } catch (e: Exception) {
            Log.e("DeliveryActivity", "React Native startLiveActivity error", e)
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

    @ReactMethod
    fun isLiveActivityRunning(promise: Promise) {
        promise.resolve(helper.isRunning())
    }

    @ReactMethod
    fun areLiveActivitiesEnabled(promise: Promise) {
        promise.resolve(true)
    }

    @ReactMethod
    fun saveImageToAppGroup(imageUrl: String, appGroupId: String, promise: Promise) {
        promise.resolve(imageUrl)
    }

    @ReactMethod
    fun cleanAppGroupImages(maxAgeHours: Double, appGroupId: String, promise: Promise) {
        promise.resolve(null)
    }
}
