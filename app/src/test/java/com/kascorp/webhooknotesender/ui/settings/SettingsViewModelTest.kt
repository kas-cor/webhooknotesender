package com.kascorp.webhooknotesender.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kascorp.webhooknotesender.data.model.ThemeMode
import com.kascorp.webhooknotesender.util.LocaleHelper
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var context: Context
    private lateinit var viewModel: SettingsViewModel

    private val themeKey = stringPreferencesKey("theme_mode")
    private val langKey = stringPreferencesKey("language")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Mock DataStore with relaxed = true so internal updateData() returns default
        dataStore = mockk(relaxed = true)

        // Mock Context
        context = mockk(relaxed = true)

        // Mock LocaleHelper object
        mockkObject(LocaleHelper)
        every { LocaleHelper.saveLanguage(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun emptyPrefs(): Preferences {
        val prefs = mockk<Preferences>(relaxed = true)
        every { prefs.get(any<Preferences.Key<String>>()) } returns null
        return prefs
    }

    private fun prefsWith(
        theme: String? = null,
        language: String? = null
    ): Preferences {
        val prefs = mockk<Preferences>(relaxed = true)
        every { prefs.get(any<Preferences.Key<String>>()) } answers {
            when {
                theme != null && arg<Preferences.Key<String>>(0) == themeKey -> theme
                language != null && arg<Preferences.Key<String>>(0) == langKey -> language
                else -> null
            }
        }
        return prefs
    }

    // ===================== init / loadSettings =====================

    @Test
    fun `init defaults to SYSTEM theme when no saved theme`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())

        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        assert(viewModel.themeMode.value == ThemeMode.SYSTEM) {
            "Expected SYSTEM but got ${viewModel.themeMode.value}"
        }
    }

    @Test
    fun `init defaults to EN language when no saved language`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())

        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        assert(viewModel.selectedLanguage.value == "en") {
            "Expected 'en' but got ${viewModel.selectedLanguage.value}"
        }
    }

    @Test
    fun `init loads saved theme from DataStore`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(prefsWith(theme = "dark"))

        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        assert(viewModel.themeMode.value == ThemeMode.DARK) {
            "Expected DARK but got ${viewModel.themeMode.value}"
        }
    }

    @Test
    fun `init loads saved language from DataStore`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(prefsWith(language = "ru"))

        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        assert(viewModel.selectedLanguage.value == "ru") {
            "Expected 'ru' but got ${viewModel.selectedLanguage.value}"
        }
    }

    @Test
    fun `init falls back to SYSTEM for unknown theme value`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(prefsWith(theme = "unknown"))

        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        assert(viewModel.themeMode.value == ThemeMode.SYSTEM) {
            "Expected SYSTEM fallback but got ${viewModel.themeMode.value}"
        }
    }

    // ===================== setThemeMode =====================

    @Test
    fun `setThemeMode updates theme state immediately`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.DARK)

        assert(viewModel.themeMode.value == ThemeMode.DARK) {
            "Expected DARK but got ${viewModel.themeMode.value}"
        }
    }

    @Test
    fun `setThemeMode cycles through all modes`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.LIGHT)
        assert(viewModel.themeMode.value == ThemeMode.LIGHT)

        viewModel.setThemeMode(ThemeMode.DARK)
        assert(viewModel.themeMode.value == ThemeMode.DARK)

        viewModel.setThemeMode(ThemeMode.SYSTEM)
        assert(viewModel.themeMode.value == ThemeMode.SYSTEM)
    }

    @Test
    fun `setThemeMode triggers DataStore update`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        coVerify(atLeast = 1) {
            dataStore.updateData(any<suspend (Preferences) -> Preferences>())
        }
    }

    @Test
    fun `setThemeMode does not send restart event`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        // Should NOT emit a restart event (only language changes trigger restart)
        val event = withTimeoutOrNull(100) { viewModel.restartEvent.first() }
        assert(event == null) { "Expected no restart event for theme change, but got one" }
    }

    // ===================== setLanguage =====================

    @Test
    fun `setLanguage updates language state immediately`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        viewModel.setLanguage("ru")

        assert(viewModel.selectedLanguage.value == "ru") {
            "Expected 'ru' but got ${viewModel.selectedLanguage.value}"
        }
    }

    @Test
    fun `setLanguage cycles between en and ru`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        viewModel.setLanguage("ru")
        assert(viewModel.selectedLanguage.value == "ru")

        viewModel.setLanguage("en")
        assert(viewModel.selectedLanguage.value == "en")
    }

    @Test
    fun `setLanguage saves to SharedPreferences synchronously`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        viewModel.setLanguage("ru")

        verify(exactly = 1) { LocaleHelper.saveLanguage(context, "ru") }
    }

    @Test
    fun `setLanguage triggers DataStore update`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        viewModel.setLanguage("ru")
        advanceUntilIdle()

        coVerify(atLeast = 1) {
            dataStore.updateData(any<suspend (Preferences) -> Preferences>())
        }
    }

    @Test
    fun `setLanguage does not crash when DataStore update throws`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        coEvery {
            dataStore.updateData(any<suspend (Preferences) -> Preferences>())
        } throws RuntimeException("Simulated failure")

        viewModel.setLanguage("ru")
        advanceUntilIdle()

        assert(viewModel.selectedLanguage.value == "ru") {
            "Expected 'ru' but got ${viewModel.selectedLanguage.value}"
        }
    }

    @Test
    fun `setLanguage sends restart event`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        viewModel.setLanguage("ru")

        // trySend is non-suspending — the event is already in the CONFLATED channel
        val event = viewModel.restartEvent.first()
        assert(event == Unit) { "Expected restart event to be emitted" }
    }

    @Test
    fun `setLanguage sends restart event even when DataStore update throws`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        coEvery {
            dataStore.updateData(any<suspend (Preferences) -> Preferences>())
        } throws RuntimeException("Simulated failure")

        viewModel.setLanguage("ru")
        advanceUntilIdle()

        // Restart event should be sent regardless of DataStore failure
        val event = viewModel.restartEvent.first()
        assert(event == Unit) { "Expected restart event even when DataStore throws" }
    }

    @Test
    fun `setLanguage sends restart event only once per call`() = runTest(testDispatcher) {
        coEvery { dataStore.data } returns flowOf(emptyPrefs())
        viewModel = SettingsViewModel(dataStore, context)
        advanceUntilIdle()

        viewModel.setLanguage("ru")
        val firstEvent = viewModel.restartEvent.first()
        assert(firstEvent == Unit)

        // Second call should also send event
        viewModel.setLanguage("en")
        val secondEvent = viewModel.restartEvent.first()
        assert(secondEvent == Unit)
    }
}
