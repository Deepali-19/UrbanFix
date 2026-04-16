package com.example.urban

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLocaleManager {

    private const val PREF_PROFILE = "profile_preferences"
    private const val KEY_LANGUAGE = "language"

    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_HINDI = "hi"

    // Applies saved app language.
    fun applySavedLocale(context: Context) {
        applyLanguageCode(currentLanguageCode(context))
    }

    // Returns saved language code.
    fun currentLanguageCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_PROFILE, Context.MODE_PRIVATE)
        val storedValue = prefs.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH).orEmpty()
        return normalizeLanguageCode(storedValue)
    }

    // Saves selected language.
    fun saveLanguageCode(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREF_PROFILE, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, normalizeLanguageCode(languageCode)).apply()
    }

    // Applies app locale.
    fun applyLanguageCode(languageCode: String) {
        val normalizedCode = normalizeLanguageCode(languageCode)
        val languageTags = when (normalizedCode) {
            LANGUAGE_HINDI -> LANGUAGE_HINDI
            LANGUAGE_ENGLISH -> LANGUAGE_ENGLISH
            else -> ""
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTags))
    }

    // Gets label for a language code.
    fun labelForCode(context: Context, languageCode: String): String {
        return when (normalizeLanguageCode(languageCode)) {
            LANGUAGE_HINDI -> context.getString(R.string.language_option_hindi)
            LANGUAGE_SYSTEM -> context.getString(R.string.language_option_system)
            else -> context.getString(R.string.language_option_english)
        }
    }

    // Converts label back to code.
    fun codeForLabel(context: Context, label: String): String {
        return when (label.trim()) {
            context.getString(R.string.language_option_hindi) -> LANGUAGE_HINDI
            context.getString(R.string.language_option_system) -> LANGUAGE_SYSTEM
            "Hindi" -> LANGUAGE_HINDI
            "Regional" -> LANGUAGE_SYSTEM
            "System Default" -> LANGUAGE_SYSTEM
            else -> LANGUAGE_ENGLISH
        }
    }

    // Normalizes language values.
    private fun normalizeLanguageCode(rawValue: String): String {
        return when (rawValue.trim().lowercase()) {
            "hi", "hindi" -> LANGUAGE_HINDI
            "system", "regional", "system default" -> LANGUAGE_SYSTEM
            else -> LANGUAGE_ENGLISH
        }
    }
}
