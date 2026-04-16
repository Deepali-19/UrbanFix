package com.example.urban.bottomNavigation.alert

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AlertStorage {

    private const val PREFS_NAME = "alert_storage"
    private const val KEY_ALERTS = "alerts_json"

    // This function reads all locally saved alerts so the alert center can show past notifications.
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

    // This function saves one new alert at the top of the list and avoids duplicate ids.
    fun addAlert(context: Context, alert: AlertItem) {
        val updatedAlerts = mutableListOf<AlertItem>()
        updatedAlerts.add(alert)
        updatedAlerts.addAll(getAlerts(context).filterNot { it.id == alert.id })
        saveAlerts(context, updatedAlerts.take(100))
    }

    // This function marks a single alert as read after the user opens it.
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

    // This function marks every saved alert as read in one action.
    fun markAllRead(context: Context) {
        saveAlerts(context, getAlerts(context).map { it.copy(isRead = true) })
    }

    // This function removes all saved alerts from local storage.
    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_ALERTS).apply()
    }

    // This function returns the unread alert count used in badges and counters.
    fun unreadCount(context: Context): Int = getAlerts(context).count { !it.isRead }

    // This function converts the alert list into JSON and saves it in shared preferences.
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

    // This function returns the shared preferences instance used by the alert storage object.
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
