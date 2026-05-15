package com.example.urban.loginSingUp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// Loads city suggestions from Google Places autocomplete.
object GoogleCitySuggestionService {

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchCitySuggestions(
        apiKey: String,
        query: String,
        sessionToken: String,
        languageCode: String
    ): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || query.isBlank()) {
            return@withContext emptyList()
        }

        val requestBody = JSONObject().apply {
            put("input", query)
            put("sessionToken", sessionToken)
            put("languageCode", languageCode)
            put("includedPrimaryTypes", org.json.JSONArray().put("(cities)"))
            put("includedRegionCodes", org.json.JSONArray().put("in"))
            put("includeQueryPredictions", true)
        }

        val request = Request.Builder()
            .url("https://places.googleapis.com/v1/places:autocomplete")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Goog-Api-Key", apiKey)
            .addHeader(
                "X-Goog-FieldMask",
                "suggestions.placePrediction.text.text,suggestions.queryPrediction.text.text"
            )
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use emptyList()
                }

                val body = response.body?.string().orEmpty()
                val suggestions = JSONObject(body).optJSONArray("suggestions")
                    ?: return@use emptyList()

                val cityNames = linkedSetOf<String>()

                for (index in 0 until suggestions.length()) {
                    val placePredictionText = suggestions.optJSONObject(index)
                        ?.optJSONObject("placePrediction")
                        ?.optJSONObject("text")
                        ?.optString("text")
                        .orEmpty()
                        .trim()

                    if (placePredictionText.isNotBlank()) {
                        cityNames.add(placePredictionText)
                    }

                    val queryPredictionText = suggestions.optJSONObject(index)
                        ?.optJSONObject("queryPrediction")
                        ?.optJSONObject("text")
                        ?.optString("text")
                        .orEmpty()
                        .trim()

                    if (queryPredictionText.isNotBlank()) {
                        cityNames.add(queryPredictionText)
                    }
                }

                cityNames.toList()
            }
        }.getOrDefault(emptyList())
    }
}
