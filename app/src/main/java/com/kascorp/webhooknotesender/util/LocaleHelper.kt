package com.kascorp.webhooknotesender.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

/**
 * Helper for applying and persisting locale changes.
 * Uses SharedPreferences for synchronous access in attachBaseContext().
 */
object LocaleHelper {

    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "language"

    /**
     * Wrap the given context with the saved locale (or default).
     * Call from Activity.attachBaseContext().
     */
    fun wrapContext(context: Context): Context {
        val language = getSavedLanguage(context)
        if (language.isEmpty()) return context

        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)

        @Suppress("DEPRECATION")
        val config = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }

        // Directly update resources as a fallback for custom ROMs (e.g. MIUI)
        // that don't properly handle createConfigurationContext.
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        return context.createConfigurationContext(config)
    }

    /**
     * Save the language preference (called from ViewModel after DataStore save).
     */
    fun saveLanguage(context: Context, language: String) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, language).commit()
    }

    /**
     * Read the saved language synchronously (for attachBaseContext).
     */
    private fun getSavedLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_LANGUAGE, "") ?: ""
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
