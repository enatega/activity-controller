package com.enatega.activitycontroller

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.facebook.react.bridge.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class ActivityControllerModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val TAG = "ActivityController"
    private val gson = Gson()
    private val helper = NotificationHelper(reactContext)
    private var currentActivityId: String? = null
    private var isRunning = false

    override fun getName(): String = "ActivityController"

    @ReactMethod
    fun startLiveActivity(paramsJson: String, promise: Promise) {
        try {
            val params = JSONObject(paramsJson)
            // parse params - flexible
            val title = params.optString("title", "Live Activity")
            val subtitle = params.optString("subtitle", "")
            val total = params.optString("total", "")
            val orderId = params.optString("orderId", "")
            val status = params.optString("status", "")
            val imageUrl = params.optString("imageUrl", "")

            // create id
            val activityId = UUID.randomUUID().toString()
            currentActivityId = activityId
            isRunning = true

            // show initial notification (without remote image yet)
            helper.showNotification(
                activityId,
                title,
                subtitle,
                total,
                orderId,
                status,
                null
            )

            // if image URL provided, fetch and update
            if (imageUrl.isNotEmpty()) {
                GlobalScope.launch(Dispatchers.IO) {
                    val bmp = fetchBitmapFromUrl(imageUrl)
                    if (bmp != null) {
                        helper.showNotification(
                            activityId,
                            title,
                            subtitle,
                            total,
                            orderId,
                            status,
                            bmp
                        )
                    }
                }
            }

            // result object (JS expects e.g. { activityId, pushToken? })
            val map = Arguments.createMap()
            map.putString("activityId", activityId)
            promise.resolve(map)
        } catch (e: Exception) {
            Log.e(TAG, "startLiveActivity error", e)
            promise.reject("start_error", e)
        }
    }

    @ReactMethod
    fun updateLiveActivity(paramsJson: String, promise: Promise) {
        try {
            val params = JSONObject(paramsJson)
            val title = params.optString("title", null)
            val subtitle = params.optString("subtitle", null)
            val total = params.optString("total", null)
            val orderId = params.optString("orderId", null)
            val status = params.optString("status", null)
            val imageUrl = params.optString("imageUrl", null)
            val activityId = params.optString("activityId", currentActivityId)

            // fetch bitmap synchronously on background
            GlobalScope.launch(Dispatchers.IO) {
                var bmp: Bitmap? = null
                if (imageUrl != null && imageUrl.isNotEmpty()) {
                    bmp = fetchBitmapFromUrl(imageUrl)
                }
                helper.showNotification(
                    activityId,
                    title ?: "",
                    subtitle ?: "",
                    total ?: "",
                    orderId ?: "",
                    status ?: "",
                    bmp
                )
            }

            promise.resolve(null)
        } catch (e: Exception) {
            Log.e(TAG, "updateLiveActivity error", e)
            promise.reject("update_error", e)
        }
    }

    @ReactMethod
    fun stopLiveActivity(promise: Promise) {
        try {
            currentActivityId?.let { helper.cancelNotification(it) }
            isRunning = false
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("stop_error", e)
        }
    }

    @ReactMethod
    fun isLiveActivityRunning(promise: Promise) {
        promise.resolve(isRunning)
    }

    @ReactMethod
    fun areLiveActivitiesEnabled(promise: Promise) {
        // host app must request POST_NOTIFICATIONS permission on Android 13+, module cannot auto-grant that.
        promise.resolve(true)
    }

    // Save an image to module's cache directory and return the absolute path
    @ReactMethod
    fun saveImageToAppGroup(imageUri: String, promise: Promise) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val srcUri = Uri.parse(imageUri)
                val bitmap = when (srcUri.scheme) {
                    "http", "https" -> fetchBitmapFromUrl(imageUri)
                    "content", "file" -> {
                        val stream = reactContext.contentResolver.openInputStream(srcUri)
                        BitmapFactory.decodeStream(stream)
                    }
                    else -> null
                }
                if (bitmap == null) {
                    promise.reject("save_error", "Could not decode provided imageUri")
                    return@launch
                }
                val cacheDir = File(reactContext.cacheDir, "activity_images")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val filename = "img_${System.currentTimeMillis()}.png"
                val outFile = File(cacheDir, filename)
                val fos = FileOutputStream(outFile)
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
                fos.flush()
                fos.close()
                promise.resolve(outFile.absolutePath)
            } catch (e: Exception) {
                promise.reject("save_error", e)
            }
        }
    }

    @ReactMethod
    fun cleanAppGroupImages(maxAgeHours: Double, promise: Promise) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val millis = (maxAgeHours * 3600_000).toLong()
                val cacheDir = File(reactContext.cacheDir, "activity_images")
                if (!cacheDir.exists()) {
                    promise.resolve(null)
                    return@launch
                }
                val now = System.currentTimeMillis()
                cacheDir.listFiles()?.forEach { file ->
                    if (now - file.lastModified() > millis) {
                        file.delete()
                    }
                }
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("clean_error", e)
            }
        }
    }

    private fun fetchBitmapFromUrl(urlStr: String): Bitmap? {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doInput = true
            conn.connect()
            val input: InputStream = conn.inputStream
            val bmp = BitmapFactory.decodeStream(input)
            input.close()
            return bmp
        } catch (e: Exception) {
            Log.e(TAG, "fetchBitmap error", e)
            return null
        }
    }
}
