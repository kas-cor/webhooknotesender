package com.kascorp.webhooknotesender.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    // ===================== Screen structure =====================

    @Test
    fun settingsScreen_displaysTitle() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAppearanceSection() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysThemeOptions() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("Light").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
        composeTestRule.onNodeWithText("System").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysLanguageSection() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("Language").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysLanguageOptions() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("Russian").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAboutSection() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAppNameAndVersion() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("Webhook Note Sender").assertIsDisplayed()
        composeTestRule.onNodeWithText("Version 1.0.0").assertIsDisplayed()
    }

    // ===================== Theme interactions =====================

    @Test
    fun clickingLight_callsSetThemeModeWithLight() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("Light").performClick()
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.LIGHT) }
    }

    @Test
    fun clickingDark_callsSetThemeModeWithDark() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("Dark").performClick()
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.DARK) }
    }

    @Test
    fun clickingSystem_callsSetThemeModeWithSystem() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("System").performClick()
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.SYSTEM) }
    }

    @Test
    fun clickingAllThemeOptions_callsAllThreeSetThemeModeVariants() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("Light").performClick()
        composeTestRule.onNodeWithText("Dark").performClick()
        composeTestRule.onNodeWithText("System").performClick()
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.LIGHT) }
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.DARK) }
        verify(exactly = 1) { vm.setThemeMode(ThemeMode.SYSTEM) }
    }

    // ===================== Language interactions =====================

    @Test
    fun clickingEnglish_callsSetLanguageWithEn() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("English").performClick()
        verify(exactly = 1) { vm.setLanguage("en") }
    }

    @Test
    fun clickingRussian_callsSetLanguageWithRu() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("Russian").performClick()
        verify(exactly = 1) { vm.setLanguage("ru") }
    }

    @Test
    fun clickingBothLanguageOptions_callsBothSetLanguageVariants() {
        val vm = createViewModel()
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("English").performClick()
        composeTestRule.onNodeWithText("Russian").performClick()
        verify(exactly = 1) { vm.setLanguage("en") }
        verify(exactly = 1) { vm.setLanguage("ru") }
    }

    // ===================== Visual state =====================

    @Test
    fun systemThemeOption_displaysAndClickable_whenThemeIsSystem() {
        val vm = createViewModel(theme = ThemeMode.SYSTEM)
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("System").assertIsDisplayed()
        composeTestRule.onNodeWithText("System").performClick()
        verify(atLeast = 1) { vm.setThemeMode(ThemeMode.SYSTEM) }
    }

    @Test
    fun darkThemeOption_displaysAndClickable_whenThemeIsDark() {
        val vm = createViewModel(theme = ThemeMode.DARK)
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dark").performClick()
        verify(atLeast = 1) { vm.setThemeMode(ThemeMode.DARK) }
    }

    @Test
    fun englishLanguageOption_displaysAndClickable_whenLanguageIsEn() {
        val vm = createViewModel(language = "en")
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("English").performClick()
        verify(atLeast = 1) { vm.setLanguage("en") }
    }

    @Test
    fun russianLanguageOption_displaysAndClickable_whenLanguageIsRu() {
        val vm = createViewModel(language = "ru")
        composeTestRule.setContent { SettingsScreen(viewModel = vm) }
        composeTestRule.onNodeWithText("Russian").assertIsDisplayed()
        composeTestRule.onNodeWithText("Russian").performClick()
        verify(atLeast = 1) { vm.setLanguage("ru") }
    }
}
