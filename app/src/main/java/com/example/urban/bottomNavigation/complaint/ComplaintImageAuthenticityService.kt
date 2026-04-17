package com.example.urban.bottomNavigation.complaint

import com.example.urban.AppConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class ComplaintImageAuthenticityResult(
    val aiGeneratedScore: Double,
    val label: String,
    val checkedAt: Long
)

// This object handles the image authenticity check using the complaint image URL.
object ComplaintImageAuthenticityService {

    private val client = OkHttpClient()

    // This tells the screen if the Sightengine keys are ready for use.
    fun isConfigured(): Boolean {
        return AppConfig.sightengineApiUser.isNotBlank() && AppConfig.sightengineApiSecret.isNotBlank()
    }

    // This sends the complaint image URL to Sightengine and returns the stored check result.
    fun checkImage(imageUrl: String, callback: (Result<ComplaintImageAuthenticityResult>) -> Unit) {
        if (!isConfigured()) {
            callback(Result.failure(IllegalStateException("Sightengine API keys are not configured locally.")))
            return
        }

        if (imageUrl.isBlank()) {
            callback(Result.failure(IllegalStateException("Complaint image URL is missing.")))
            return
        }

        Thread {
            try {
                val checkedAt = System.currentTimeMillis()
                val url = "https://api.sightengine.com/1.0/check.json".toHttpUrl().newBuilder()
                    .addQueryParameter("models", "genai")
                    .addQueryParameter("url", imageUrl)
                    .addQueryParameter("api_user", AppConfig.sightengineApiUser)
                    .addQueryParameter("api_secret", AppConfig.sightengineApiSecret)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("Image check failed (${response.code}): $body")
                    }

                    val score = readScore(body)
                    callback(
                        Result.success(
                            ComplaintImageAuthenticityResult(
                                aiGeneratedScore = score,
                                label = scoreLabel(score),
                                checkedAt = checkedAt
                            )
                        )
                    )
                }
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }.start()
    }

    // This reads the ai_generated score from the API response.
    private fun readScore(rawJson: String): Double {
        val root = JSONObject(rawJson)
        val type = root.optJSONObject("type")
            ?: throw IOException("Image check response is missing the type object.")
        return type.optDouble("ai_generated", -1.0).takeIf { it >= 0.0 }
            ?: throw IOException("Image check response is missing the ai_generated score.")
    }

    // This turns the numeric score into a simple admin-friendly result.
    private fun scoreLabel(score: Double): String {
        return when {
            score >= 0.85 -> "Likely AI-generated"
            score >= 0.45 -> "Needs manual review"
            else -> "Likely real image"
        }
    }
}
