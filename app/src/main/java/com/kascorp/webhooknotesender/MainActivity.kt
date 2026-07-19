package com.kascorp.webhooknotesender

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kascorp.webhooknotesender.ui.navigation.AppNavigation
import com.kascorp.webhooknotesender.ui.navigation.DetailScreen
import com.kascorp.webhooknotesender.ui.settings.SettingsViewModel
import com.kascorp.webhooknotesender.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Shared with AppNavigation via hiltViewModel() — same Activity scope, same instance
    private val settingsViewModel: SettingsViewModel by viewModels()

    // Used to trigger navigation from shortcut intents (audio recording)
    private var pendingNavigationRoute by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLocaleDirectly()

        handleIntent(intent)

        setContent {
            AppNavigation(
                initialAudioRoute = pendingNavigationRoute,
                onNavigated = { pendingNavigationRoute = null }
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.restartEvent.collect {
                    recreate()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("shortcut_audio", false) == true) {
            val profileId = intent.getLongExtra("profile_id", -1L)
            val profileName = intent.getStringExtra("profile_name") ?: ""
            val profilePrompt = intent.getStringExtra("profile_prompt") ?: ""
            val profileUrl = intent.getStringExtra("profile_url") ?: ""
            val bearerToken = intent.getStringExtra("bearer_token")
            if (profileId != -1L) {
                pendingNavigationRoute = DetailScreen.AudioRecording.createRoute(
                    profileId = profileId,
                    profileName = profileName,
                    profilePrompt = profilePrompt,
                    profileUrl = profileUrl,
                    bearerToken = bearerToken,
                    isFromShortcut = true
                )
            }
            // Clear extras so recreate (e.g. locale change) won't re-trigger navigation
            intent.removeExtra("shortcut_audio")
            intent.removeExtra("profile_id")
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
