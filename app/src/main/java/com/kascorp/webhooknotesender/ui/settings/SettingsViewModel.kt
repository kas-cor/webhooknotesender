package com.kascorp.webhooknotesender.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kascorp.webhooknotesender.data.model.ThemeMode
import com.kascorp.webhooknotesender.util.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
    }

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    /**
     * One-shot event emitted when the language changes and the Activity should recreate.
     */
    private val _restartEvent = Channel<Unit>(Channel.CONFLATED)
    val restartEvent = _restartEvent.receiveAsFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val savedTheme = dataStore.data.map { prefs ->
                prefs[THEME_MODE_KEY] ?: "system"
            }.first()
            _themeMode.value = when (savedTheme) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }

            val savedLanguage = dataStore.data.map { prefs ->
                prefs[LANGUAGE_KEY] ?: "en"
            }.first()
            _selectedLanguage.value = savedLanguage
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[THEME_MODE_KEY] = when (mode) {
                    ThemeMode.LIGHT -> "light"
                    ThemeMode.DARK -> "dark"
                    ThemeMode.SYSTEM -> "system"
                }
            }
        }
    }

    fun setLanguage(language: String) {
        _selectedLanguage.value = language
        // Save synchronously for LocaleHelper (attachBaseContext)
        LocaleHelper.saveLanguage(context, language)

        // On API 33+, use the official LocaleManager API (most reliable on modern devices)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                try {
                    localeManager.applicationLocales = LocaleList(Locale.forLanguageTag(language))
                } catch (_: Exception) {
                    // Some ROMs (e.g. MIUI) may not fully implement LocaleManager
                }
            }
        }

        // Save to DataStore asynchronously (best-effort, doesn't block restart)
        viewModelScope.launch {
            try {
                dataStore.edit { prefs ->
                    prefs[LANGUAGE_KEY] = language
                }
            } catch (_: Exception) {
                // DataStore save is optional — locale is already persisted via SharedPreferences
            }
        }

        // Signal the Activity to recreate with the new locale.
        // Using a non-suspending trySend to avoid coroutine cancellation issues.
        _restartEvent.trySend(Unit)
    }
}
