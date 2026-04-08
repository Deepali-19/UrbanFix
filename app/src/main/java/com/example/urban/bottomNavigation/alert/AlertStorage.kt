package com.example.urban.bottomNavigation.alert

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AlertStorage {

    private const val PREFS_NAME = "alert_storage"
    private const val KEY_ALERTS = "alerts_json"

    // Alerts are stored locally so FCM notifications remain visible inside the app after delivery.
    fun getAlerts(context: Context): List<AlertItem> {
        val rawJson = prefs(context).getString(KEY_ALERTS, null).orEmpty()
        if (rawJson.isBlank()) return emptyList()

        val jsonArray = JSONArray(rawJson)
        val alerts = mutableListOf<AlertItem>()
        for (index in 0 until jsonArray.length()) {
            val json = jsonArray.optJSONObject(index) ?: continue
            alerts.add(
                AlertItem(
                    id = json.optString("id"),
                    title = json.optString("title"),
                    body = json.optString("body"),
                    type = json.optString("type"),
                    timestamp = json.optLong("timestamp"),
                    isRead = json.optBoolean("isRead"),
                    complaintKey = json.optString("complaintKey"),
                    complaintDisplayId = json.optString("complaintDisplayId")
                )
            )
        }

        return alerts.sortedByDescending { it.timestamp }
    }

    fun addAlert(context: Context, alert: AlertItem) {
        val updatedAlerts = mutableListOf<AlertItem>()
        updatedAlerts.add(alert)
        updatedAlerts.addAll(getAlerts(context).filterNot { it.id == alert.id })
        saveAlerts(context, updatedAlerts.take(100))
    }

    fun markRead(context: Context, alertId: String) {
        val updatedAlerts = getAlerts(context).map { alert ->
            if (alert.id == alertId) {
                alert.copy(isRead = true)
            } else {
                alert
            }
        }
        saveAlerts(context, updatedAlerts)
    }

    fun markAllRead(context: Context) {
        saveAlerts(context, getAlerts(context).map { it.copy(isRead = true) })
    }

    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_ALERTS).apply()
    }

    fun unreadCount(context: Context): Int = getAlerts(context).count { !it.isRead }

    private fun saveAlerts(context: Context, alerts: List<AlertItem>) {
        val jsonArray = JSONArray()
        alerts.forEach { alert ->
            jsonArray.put(
                JSONObject().apply {
                    put("id", alert.id)
                    put("title", alert.title)
                    put("body", alert.body)
                    put("type", alert.type)
                    put("timestamp", alert.timestamp)
                    put("isRead", alert.isRead)
                    put("complaintKey", alert.complaintKey)
                    put("complaintDisplayId", alert.complaintDisplayId)
                }
            )
        }

        prefs(context).edit().putString(KEY_ALERTS, jsonArray.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
