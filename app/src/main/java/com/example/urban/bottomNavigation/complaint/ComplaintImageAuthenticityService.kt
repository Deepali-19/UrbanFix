package com.example.urban.bottomNavigation.complaint

import com.example.urban.AppConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlin.io.path.createTempFile

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
                val score = runCatching {
                    checkByUrl(imageUrl)
                }.recoverCatching {
                    checkByUploadedMedia(imageUrl)
                }.getOrThrow()

                callback(
                    Result.success(
                        ComplaintImageAuthenticityResult(
                            aiGeneratedScore = score,
                            label = scoreLabel(score),
                            checkedAt = checkedAt
                        )
                    )
                )
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }.start()
    }

    // This tries the simple remote URL flow first.
    private fun checkByUrl(imageUrl: String): Double {
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
                throw IOException("Image check failed (${response.code}): ${readFailureMessage(body)}")
            }

            return readScore(body)
        }
    }

    // This falls back to uploading the actual image file when the remote URL is not accepted.
    private fun checkByUploadedMedia(imageUrl: String): Double {
        val downloadRequest = Request.Builder()
            .url(imageUrl)
            .get()
            .build()

        client.newCall(downloadRequest).execute().use { downloadResponse ->
            if (!downloadResponse.isSuccessful) {
                val body = downloadResponse.body?.string().orEmpty()
                throw IOException("Complaint image download failed (${downloadResponse.code}): ${readFailureMessage(body)}")
            }

            val imageBytes = downloadResponse.body?.bytes()
                ?: throw IOException("Complaint image download returned an empty body.")
            val contentType = downloadResponse.body?.contentType()?.toString()
                ?: downloadResponse.header("Content-Type")
                ?: "image/jpeg"

            val tempFile = createTempFile(prefix = "complaint-image-", suffix = guessSuffix(contentType)).toFile()
            tempFile.writeBytes(imageBytes)

            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("models", "genai")
                    .addFormDataPart("api_user", AppConfig.sightengineApiUser)
                    .addFormDataPart("api_secret", AppConfig.sightengineApiSecret)
                    .addFormDataPart(
                        "media",
                        tempFile.name,
                        tempFile.asRequestBody(contentType.toMediaTypeOrNull())
                    )
                    .build()

                val request = Request.Builder()
                    .url("https://api.sightengine.com/1.0/check.json")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("Image check failed (${response.code}): ${readFailureMessage(body)}")
                    }

                    return readScore(body)
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    // This reads the ai_generated score from the API response.
    private fun readScore(rawJson: String): Double {
        val root = JSONObject(rawJson)
        val type = root.optJSONObject("type")
            ?: throw IOException("Image check response is missing the type object.")
        return type.optDouble("ai_generated", -1.0).takeIf { it >= 0.0 }
            ?: throw IOException("Image check response is missing the ai_generated score.")
    }

    // This keeps error toasts readable when the API returns a JSON failure body.
    private fun readFailureMessage(rawBody: String): String {
        if (rawBody.isBlank()) return "Empty response body"

        return runCatching {
            val root = JSONObject(rawBody)
            listOf(
                root.optString("error"),
                root.optString("message"),
                root.optString("status"),
                root.optJSONObject("error")?.optString("message").orEmpty()
            ).firstOrNull { it.isNotBlank() } ?: rawBody.take(180)
        }.getOrDefault(rawBody.take(180))
    }

    // This picks a simple extension for the temporary upload file.
    private fun guessSuffix(contentType: String): String {
        return when {
            contentType.contains("png", ignoreCase = true) -> ".png"
            contentType.contains("webp", ignoreCase = true) -> ".webp"
            contentType.contains("gif", ignoreCase = true) -> ".gif"
            else -> ".jpg"
        }
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
