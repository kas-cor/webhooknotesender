package com.kascorp.webhooknotesender

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kascorp.webhooknotesender.ui.navigation.AppNavigation
import com.kascorp.webhooknotesender.ui.settings.SettingsViewModel
import com.kascorp.webhooknotesender.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Shared with AppNavigation via hiltViewModel() — same Activity scope, same instance
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLocaleDirectly()
        setContent {
            AppNavigation()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.restartEvent.collect {
                    recreate()
                }
            }
        }
    }

    private fun applyLocaleDirectly() {
        val lang = LocaleHelper.cachedLanguage
        if (lang.isEmpty()) return
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        try {
            val config = Configuration(resources.configuration)
            config.setLocales(LocaleList(locale))
            resources.updateConfiguration(config, resources.displayMetrics)
        } catch (_: Exception) {
        }
    }
}
