package com.kascorp.webhooknotesender.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "language"

    private const val TAG = "LocaleHelper"

    @Volatile
    var cachedLanguage: String = ""
        private set

    fun init(context: Context) {
        val lang = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""
        cachedLanguage = lang
        Log.d(TAG, "init: cachedLanguage='$cachedLanguage'")
    }

    fun createLocaleConfig(): Configuration? {
        val language = cachedLanguage
        Log.d(TAG, "createLocaleConfig: cachedLanguage='$language'")
        if (language.isEmpty()) return null

        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)

        val config = Configuration()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.setLocale(locale)
        }
        Log.d(TAG, "createLocaleConfig: config locales=${config.locales}")
        return config
    }

    fun wrapContext(context: Context): Context {
        val config = createLocaleConfig() ?: return context
        Log.d(TAG, "wrapContext: wrapping context")
        return context.createConfigurationContext(config)
    }

    fun saveLanguage(context: Context, language: String) {
        cachedLanguage = language
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .commit()
    }

    fun getSavedLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""
    }
}
