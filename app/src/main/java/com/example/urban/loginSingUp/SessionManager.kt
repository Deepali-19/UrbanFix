package com.example.urban.loginSingUp

import android.content.Context

object SessionManager {

    const val EXTRA_SESSION_EXPIRED = "session_expired"
    const val EXTRA_SESSION_MESSAGE = "session_message"

    private const val PREF_SESSION = "session_prefs"
    private const val KEY_LAST_ACTIVITY = "last_activity"
    private const val KEY_LAST_BACKGROUND_AT = "last_background_at"
    private const val KEY_APP_LOCK_REQUIRED = "app_lock_required"

    // Marks login as active.
    fun markAuthenticated(context: Context) {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            .putLong(KEY_LAST_BACKGROUND_AT, 0L)
            .putBoolean(KEY_APP_LOCK_REQUIRED, false)
            .apply()
    }

    // Refreshes the active session time.
    fun refreshActivity(context: Context) {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            .apply()
    }

    // Saves when the app went to background and asks for app unlock next time.
    fun markBackgrounded(context: Context) {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_BACKGROUND_AT, System.currentTimeMillis())
            .putBoolean(KEY_APP_LOCK_REQUIRED, true)
            .apply()
    }

    // Session expiry is turned off now, so logged-in users stay signed in.
    fun isExpired(context: Context): Boolean {
        return false
    }

    // Returns whether the app should ask for biometric or device lock now.
    fun isAppLockRequired(context: Context): Boolean {
        return context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_LOCK_REQUIRED, false)
    }

    // Marks the app as unlocked after a successful biometric or device credential check.
    fun markUnlocked(context: Context) {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_LOCK_REQUIRED, false)
            .putLong(KEY_LAST_BACKGROUND_AT, 0L)
            .apply()
    }

    // Clears saved session data.
    fun clear(context: Context) {
        context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
