package com.example.urban.loginSingUp

import android.content.Context

object SessionManager {

    const val EXTRA_SESSION_EXPIRED = "session_expired"
    const val EXTRA_SESSION_MESSAGE = "session_message"
    const val TIMEOUT_MS = 15L * 60L * 1000L

    private const val PREF_SESSION = "session_prefs"
    private const val KEY_LAST_ACTIVITY = "last_activity"

    fun markAuthenticated(context: Context) {
        refreshActivity(context)
    }

    fun refreshActivity(context: Context) {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            .apply()
    }

    fun isExpired(context: Context): Boolean {
        val lastActivity = context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_ACTIVITY, 0L)
        if (lastActivity <= 0L) return false
        return System.currentTimeMillis() - lastActivity > TIMEOUT_MS
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
