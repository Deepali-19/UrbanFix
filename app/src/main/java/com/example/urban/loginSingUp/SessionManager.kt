package com.example.urban.loginSingUp

import android.content.Context

object SessionManager {

    const val EXTRA_SESSION_EXPIRED = "session_expired"
    const val EXTRA_SESSION_MESSAGE = "session_message"
    const val TIMEOUT_MS = 24L * 60L * 60L * 1000L

    private const val PREF_SESSION = "session_prefs"
    private const val KEY_LAST_ACTIVITY = "last_activity"
    private const val KEY_LAST_BACKGROUND_AT = "last_background_at"

    // Marks login as active.
    fun markAuthenticated(context: Context) {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            .putLong(KEY_LAST_BACKGROUND_AT, 0L)
            .apply()
    }

    // Refreshes the active session time.
    fun refreshActivity(context: Context) {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            .putLong(KEY_LAST_BACKGROUND_AT, 0L)
            .apply()
    }

    // Saves when the app went to background.
    fun markBackgrounded(context: Context) {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_BACKGROUND_AT, System.currentTimeMillis())
            .apply()
    }

    // Checks whether the session expired.
    fun isExpired(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
        val backgroundedAt = prefs.getLong(KEY_LAST_BACKGROUND_AT, 0L)
        if (backgroundedAt > 0L) {
            return System.currentTimeMillis() - backgroundedAt > TIMEOUT_MS
        }

        val lastActivity = prefs.getLong(KEY_LAST_ACTIVITY, 0L)
        if (lastActivity <= 0L) return false
        return false
    }

    // Clears saved session data.
    fun clear(context: Context) {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
