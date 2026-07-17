package com.kascorp.webhooknotesender.ui.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kascorp.webhooknotesender.data.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        // Load saved theme mode and language from DataStore
        // For now, default values are used
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        // Save to DataStore
        viewModelScope.launch {
            // Persist theme preference
        }
    }

    fun setLanguage(language: String) {
        _selectedLanguage.value = language
        // Save to DataStore
        viewModelScope.launch {
            // Persist language preference
        }
    }
}
