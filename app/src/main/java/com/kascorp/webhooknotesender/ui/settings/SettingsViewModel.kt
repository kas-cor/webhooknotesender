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
import com.kascorp.webhooknotesender.BuildConfig
import com.kascorp.webhooknotesender.data.model.ThemeMode
import com.kascorp.webhooknotesender.util.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit
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

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    private val updateClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

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
                prefs[LANGUAGE_KEY]
            }.first()
            _selectedLanguage.value = savedLanguage
                ?: LocaleHelper.getSavedLanguage(context)
                    .ifEmpty { "en" }
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
        LocaleHelper.saveLanguage(context, language)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                try {
                    localeManager.applicationLocales = LocaleList(Locale.forLanguageTag(language))
                } catch (_: Exception) { }
            }
        }

        viewModelScope.launch {
            try {
                dataStore.edit { prefs ->
                    prefs[LANGUAGE_KEY] = language
                }
            } catch (_: Exception) { }
        }

        _restartEvent.trySend(Unit)
    }

    fun checkForUpdates(releasesUrl: String) {
        if (_updateCheckState.value is UpdateCheckState.Checking) return

        _updateCheckState.value = UpdateCheckState.Checking
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = okhttp3.Request.Builder()
                        .url(releasesUrl)
                        .head()
                        .build()

                    val response = updateClient.newCall(request).execute()
                    val location = response.header("Location")

                    if (location == null || !response.isRedirect) {
                        return@withContext UpdateCheckState.Error(
                            context.getString(com.kascorp.webhooknotesender.R.string.update_check_error)
                        )
                    }

                    // Location: /kas-cor/webhooknotesender/releases/tag/v0.2
                    val tagVersion = location.substringAfterLast("/v").removePrefix("v")
                    if (tagVersion.isEmpty()) {
                        return@withContext UpdateCheckState.Error(
                            context.getString(com.kascorp.webhooknotesender.R.string.update_check_error)
                        )
                    }

                    val currentVersion = BuildConfig.VERSION_NAME
                    if (isVersionNewer(tagVersion, currentVersion)) {
                        val downloadUrl = "https://github.com${location}"
                        UpdateCheckState.Available(
                            latestVersion = tagVersion,
                            downloadUrl = downloadUrl
                        )
                    } else {
                        UpdateCheckState.UpToDate
                    }
                } catch (e: Exception) {
                    UpdateCheckState.Error(
                        context.getString(com.kascorp.webhooknotesender.R.string.update_check_error)
                    )
                }
            }
            _updateCheckState.value = result
        }
    }

    fun resetUpdateCheck() {
        _updateCheckState.value = UpdateCheckState.Idle
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }
}

sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data object UpToDate : UpdateCheckState()
    data class Available(val latestVersion: String, val downloadUrl: String) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}
