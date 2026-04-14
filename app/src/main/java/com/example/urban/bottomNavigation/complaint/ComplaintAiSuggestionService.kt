package com.example.urban.bottomNavigation.complaint

import com.example.urban.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ComplaintAiSuggestionResult(
    val suggestion: String,
    val generatedAt: Long
)

object ComplaintAiSuggestionService {

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // This tells the UI whether the Gemini feature is ready to use on this device.
    fun isConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    // This sends the complaint details to Gemini and returns one practical solution suggestion.
    fun generateSuggestion(
        complaint: Complaint,
        callback: (Result<ComplaintAiSuggestionResult>) -> Unit
    ) {
        if (!isConfigured()) {
            callback(Result.failure(IllegalStateException("Gemini API key is not configured locally.")))
            return
        }

        Thread {
            try {
                val now = System.currentTimeMillis()
                val payload = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(
                            JSONObject().apply {
                                put(
                                    "text",
                                    "You are a municipal complaint triage assistant for an Indian civic administration app. " +
                                        "Give one practical, safe, field-usable resolution suggestion for solving the complaint. " +
                                        "Do not use headings, labels, bullets, or section titles. " +
                                        "Write as one clean short operational paragraph that an admin or officer can directly follow."
                                )
                            }
                        ))
                    })
                    put("contents", JSONArray().put(
                        JSONObject().apply {
                            put("parts", JSONArray().put(
                                JSONObject().apply {
                                    put("text", buildPrompt(complaint))
                                }
                            ))
                        }
                    ))
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 320)
                    })
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/${BuildConfig.GEMINI_MODEL}:generateContent")
                    .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("AI request failed (${response.code}): $bodyString")
                    }

                    val suggestion = extractText(bodyString)
                    if (suggestion.isBlank()) {
                        throw IOException("AI returned an empty suggestion.")
                    }

                    callback(
                        Result.success(
                            ComplaintAiSuggestionResult(
                                suggestion = suggestion.trim(),
                                generatedAt = now
                            )
                        )
                    )
                }
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }.start()
    }

    // This builds the complaint context that Gemini will read before generating a suggestion.
    private fun buildPrompt(complaint: Complaint): String {
        val reportedOn = complaint.timestamp.takeIf { it > 0L }?.let {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(it))
        }.orEmpty().ifBlank { "Unknown" }

        return """
            Complaint title: ${complaint.title.ifBlank { "Untitled complaint" }}
            Issue type: ${complaint.issueType.ifBlank { "Unknown" }}
            Department: ${ComplaintDataFormatter.resolvedDepartment(complaint)}
            Priority: ${priorityLabel(complaint.priority)}
            Status: ${statusLabel(complaint.status)}
            Description: ${complaint.description.ifBlank { "No description provided" }}
            Location: ${ComplaintDataFormatter.locationLabel(complaint)}
            Coordinates: ${ComplaintDataFormatter.coordinatesLabel(complaint)}
            Reported on: $reportedOn
            Existing field remark: ${complaint.feedback.ifBlank { "No field remark yet" }}

            Give a practical resolution suggestion for city officials and field officers.
        """.trimIndent()
    }

    // This reads the plain text response from the Gemini API JSON response body.
    private fun extractText(rawJson: String): String {
        val root = JSONObject(rawJson)
        val candidates = root.optJSONArray("candidates") ?: return ""
        val builder = StringBuilder()

        for (i in 0 until candidates.length()) {
            val candidate = candidates.optJSONObject(i) ?: continue
            val content = candidate.optJSONObject("content") ?: continue
            val parts = content.optJSONArray("parts") ?: continue

            for (j in 0 until parts.length()) {
                val part = parts.optJSONObject(j) ?: continue
                val text = part.optString("text").trim()
                if (text.isNotBlank()) {
                    if (builder.isNotEmpty()) builder.append("\n\n")
                    builder.append(text)
                }
            }
        }

        return builder.toString()
    }

    // This converts the numeric priority value into a simple readable label.
    private fun priorityLabel(priority: Int): String {
        return when (priority) {
            2 -> "High"
            1 -> "Medium"
            else -> "Low"
        }
    }

    // This converts the numeric status value into a simple readable label.
    private fun statusLabel(status: Int): String {
        return when (status) {
            0 -> "Pending"
            1 -> "In Progress"
            2 -> "Resolved"
            else -> "Unknown"
        }
    }
}
