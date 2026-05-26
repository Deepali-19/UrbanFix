package com.example.urban.loginSingUp

import com.example.urban.AppConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// This sends approval decision push notifications when the project has an FCM server key.
object ApprovalNotificationSender {

    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun sendDecisionNotification(
        deviceToken: String,
        title: String,
        body: String,
        type: String
    ) {
        val serverKey = AppConfig.fcmServerKey
        if (serverKey.isBlank() || deviceToken.isBlank()) return

        val payload = JSONObject().apply {
            put("to", deviceToken)
            put(
                "data",
                JSONObject().apply {
                    put("title", title)
                    put("body", body)
                    put("type", type)
                    put("timestamp", System.currentTimeMillis())
                }
            )
            put(
                "notification",
                JSONObject().apply {
                    put("title", title)
                    put("body", body)
                }
            )
            put("priority", "high")
        }

        val request = Request.Builder()
            .url("https://fcm.googleapis.com/fcm/send")
            .addHeader("Authorization", "key=$serverKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonType))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = Unit
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }
}
