package com.kascorp.webhooknotesender.ui.settings

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kascorp.webhooknotesender.BuildConfig
import com.kascorp.webhooknotesender.R
import com.kascorp.webhooknotesender.data.model.ThemeMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createViewModel(
        theme: ThemeMode = ThemeMode.SYSTEM,
        language: String = "en"
    ): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.themeMode } returns MutableStateFlow(theme).asStateFlow()
        every { vm.selectedLanguage } returns MutableStateFlow(language).asStateFlow()
        return vm
    }

    // Helper: get context from inside the compose tree and use it for string lookups.
    // We store it in a mutable ref so tests can access it after setContent.
    private var ctx: Context? = null

    private fun str(id: Int): String = ctx!!.getString(id)
    private fun str(id: Int, vararg args: Any?): String = ctx!!.getString(id, *args)

    // ===================== Screen structure =====================

    @Test
    fun settingsScreen_displaysTitle() {
        val vm = createViewModel()
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.nav_settings)).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAppearanceSection() {
        val vm = createViewModel()
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.appearance)).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysThemeOptions() {
        val vm = createViewModel()
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.theme_light)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.theme_dark)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.theme_system)).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysLanguageSection() {
        val vm = createViewModel()
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.language)).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysLanguageOptions() {
        val vm = createViewModel()
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        // Click the language selector to expand the dropdown
        composeTestRule.onNodeWithText(str(R.string.language_english)).performClick()
        composeTestRule.onNodeWithText(str(R.string.language_english)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.language_russian)).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAboutSection() {
        val vm = createViewModel()
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.about)).assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAppNameAndVersion() {
        val vm = createViewModel()
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.app_name)).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            str(R.string.version_format, BuildConfig.VERSION_NAME)
        ).assertIsDisplayed()
    }

    // ===================== Theme interactions =====================

    @Test
    fun clickingLight_callsSetThemeModeWithLight() {
        val vm = createViewModel()
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.theme_light)).performClick()
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.LIGHT) }
    }

    @Test
    fun clickingDark_callsSetThemeModeWithDark() {
        val vm = createViewModel()
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.theme_dark)).performClick()
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.DARK) }
    }

    @Test
    fun clickingSystem_callsSetThemeModeWithSystem() {
        val vm = createViewModel()
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.theme_system)).performClick()
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.SYSTEM) }
    }

    @Test
    fun clickingAllThemeOptions_callsAllThreeSetThemeModeVariants() {
        val vm = createViewModel()
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.theme_light)).performClick()
        composeTestRule.onNodeWithText(str(R.string.theme_dark)).performClick()
        composeTestRule.onNodeWithText(str(R.string.theme_system)).performClick()
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.LIGHT) }
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.DARK) }
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.SYSTEM) }
    }

    // ===================== Language interactions =====================

    @Test
    fun clickingEnglish_callsSetLanguageWithEn() {
        // Start with Russian so "English" appears only in the dropdown after expanding
        val vm = createViewModel(language = "ru")
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        // Click the Russian button to expand the dropdown
        composeTestRule.onNodeWithText(str(R.string.language_russian)).performClick()
        // Click English in the dropdown
        composeTestRule.onNodeWithText(str(R.string.language_english)).performClick()
        verify(atLeast = 1) { vm.setLanguage("en") }
    }

    @Test
    fun clickingRussian_callsSetLanguageWithRu() {
        val vm = createViewModel(language = "en")
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        // Click the English button to expand the dropdown
        composeTestRule.onNodeWithText(str(R.string.language_english)).performClick()
        // Click Russian in the dropdown
        composeTestRule.onNodeWithText(str(R.string.language_russian)).performClick()
        verify(atLeast = 1) { vm.setLanguage("ru") }
    }

    // ===================== Visual state =====================

    @Test
    fun systemThemeOption_displaysAndClickable_whenThemeIsSystem() {
        val vm = createViewModel(theme = ThemeMode.SYSTEM)
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.theme_system)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.theme_system)).performClick()
        verify(atLeast = 1) { vm.setThemeMode(ThemeMode.SYSTEM) }
    }

    @Test
    fun darkThemeOption_displaysAndClickable_whenThemeIsDark() {
        val vm = createViewModel(theme = ThemeMode.DARK)
        composeTestRule.setContent {
            ctx = LocalContext.current
            SettingsScreen(viewModel = vm)
        }
        composeTestRule.onNodeWithText(str(R.string.theme_dark)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.theme_dark)).performClick()
        verify(atLeast = 1) { vm.setThemeMode(ThemeMode.DARK) }
    }
}
