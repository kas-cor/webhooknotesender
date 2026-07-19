package com.kascorp.webhooknotesender.ui.profiles

import android.app.Application
import com.kascorp.webhooknotesender.R
import com.kascorp.webhooknotesender.data.local.PayloadFileHelper
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.data.repository.ProfileRepository
import com.kascorp.webhooknotesender.data.repository.QueueRepository
import com.kascorp.webhooknotesender.util.Base64Encoder
import com.kascorp.webhooknotesender.util.MediaCompressor
import com.kascorp.webhooknotesender.util.ShortcutHelper
import com.kascorp.webhooknotesender.work.QueueWorker
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var application: Application
    private lateinit var profileRepository: ProfileRepository
    private lateinit var queueRepository: QueueRepository
    private lateinit var base64Encoder: Base64Encoder
    private lateinit var mediaCompressor: MediaCompressor
    private lateinit var shortcutHelper: ShortcutHelper
    private lateinit var json: Json
    private lateinit var viewModel: ProfilesViewModel

    private val testProfile = ProfileEntity(
        id = 1,
        name = "Office Camera",
        type = "image",
        prompt = "Describe what you see",
        url = "https://example.com/webhook",
        bearerToken = "test-token"
    )

    private val testProfile2 = ProfileEntity(
        id = 2,
        name = "Audio Note",
        type = "audio",
        prompt = "Transcribe this audio",
        url = "https://example.com/audio-webhook",
        bearerToken = null
    )

    private val allProfiles = listOf(testProfile, testProfile2)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        profileRepository = mockk()
        queueRepository = mockk()
        base64Encoder = mockk(relaxed = true)
        mediaCompressor = mockk(relaxed = true)
        shortcutHelper = mockk(relaxed = true)
        json = Json { ignoreUnknownKeys = true }

        // Mock getString for validation resources (tests check content, not just null)
        every { application.getString(R.string.url_http_warning) } returns "Using HTTP is not secure. Consider using HTTPS."
        every { application.getString(R.string.name_already_exists) } returns "Name already exists"

        // Mock static objects
        mockkObject(PayloadFileHelper)
        mockkObject(QueueWorker.Companion)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(): ProfilesViewModel {
        coEvery { profileRepository.getAllProfiles() } returns flowOf(allProfiles)

        return ProfilesViewModel(
            application = application,
            profileRepository = profileRepository,
            queueRepository = queueRepository,
            base64Encoder = base64Encoder,
            mediaCompressor = mediaCompressor,
            shortcutHelper = shortcutHelper,
            json = json
        )
    }

    // ===================== init / profiles state =====================

    @Test
    fun `init loads all profiles from repository`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        // StateFlow initial value is emptyList(); .first() returns it immediately.
        // Use first { it.isNotEmpty() } to wait for upstream emission.
        val profiles = viewModel.profiles.first { it.isNotEmpty() }
        assert(profiles == allProfiles) {
            "Expected $allProfiles but got $profiles"
        }
        coVerify(exactly = 1) { profileRepository.getAllProfiles() }
    }

    @Test
    fun `init shows empty form state`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        val form = viewModel.formState.first()
        assert(form.name == "") { "Expected empty name but got '${form.name}'" }
        assert(form.type == "image") { "Expected 'image' type but got '${form.type}'" }
        assert(form.prompt == "") { "Expected empty prompt but got '${form.prompt}'" }
        assert(form.url == "") { "Expected empty url but got '${form.url}'" }
        assert(form.bearerToken == "") { "Expected empty token but got '${form.bearerToken}'" }
        assert(!form.isSaving) { "Expected isSaving=false" }
        assert(!form.isTesting) { "Expected isTesting=false" }
        assert(form.testResult == null) { "Expected testResult=null" }
    }

    @Test
    fun `init shows not-editing state`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        val edit = viewModel.editState.first()
        assert(!edit.isEditing) { "Expected isEditing=false" }
        assert(edit.originalProfile == null) { "Expected originalProfile=null" }
    }

    // ===================== loadProfile =====================

    @Test
    fun `loadProfile with -1L resets form for new profile`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Pre-fill the form to verify reset
        viewModel.updateName("Test")
        viewModel.updateUrl("https://test.com")

        viewModel.loadProfile(-1L)
        advanceUntilIdle()

        val edit = viewModel.editState.first()
        assert(!edit.isEditing) { "Expected isEditing=false after reset" }
        assert(edit.originalProfile == null) { "Expected originalProfile=null after reset" }

        val form = viewModel.formState.first()
        assert(form.name == "") { "Expected name reset to empty" }
        assert(form.url == "") { "Expected url reset to empty" }
    }

    @Test
    fun `loadProfile with valid ID loads profile into form`() = runTest(testDispatcher) {
        coEvery { profileRepository.getProfileById(1L) } returns testProfile
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadProfile(1L)
        advanceUntilIdle()

        val edit = viewModel.editState.first()
        assert(edit.isEditing) { "Expected isEditing=true" }
        assert(edit.originalProfile == testProfile) { "Expected originalProfile=$testProfile" }

        val form = viewModel.formState.first()
        assert(form.name == testProfile.name) { "Expected name '${testProfile.name}' but got '${form.name}'" }
        assert(form.type == testProfile.type) { "Expected type '${testProfile.type}' but got '${form.type}'" }
        assert(form.prompt == testProfile.prompt) { "Expected prompt '${testProfile.prompt}' but got '${form.prompt}'" }
        assert(form.url == testProfile.url) { "Expected url '${testProfile.url}' but got '${form.url}'" }
        assert(form.bearerToken == testProfile.bearerToken) { "Expected token '${testProfile.bearerToken}' but got '${form.bearerToken}'" }

        coVerify(exactly = 1) { profileRepository.getProfileById(1L) }
    }

    @Test
    fun `loadProfile with non-existent ID keeps empty form`() = runTest(testDispatcher) {
        coEvery { profileRepository.getProfileById(999L) } returns null
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadProfile(999L)
        advanceUntilIdle()

        val edit = viewModel.editState.first()
        assert(!edit.isEditing) { "Expected isEditing=false when profile not found" }
        assert(edit.originalProfile == null) { "Expected originalProfile=null" }
    }

    // ===================== form state updates =====================

    @Test
    fun `updateName updates name and clears error`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        // Set an error first
        viewModel.updateName("")
        viewModel.validate()

        viewModel.updateName("Office Camera")
        val form = viewModel.formState.first()
        assert(form.name == "Office Camera") { "Expected 'Office Camera' but got '${form.name}'" }
        assert(form.nameError == null) { "Expected nameError to be cleared" }
    }

    @Test
    fun `updateType updates media type`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.updateType("video")
        val form = viewModel.formState.first()
        assert(form.type == "video") { "Expected 'video' but got '${form.type}'" }
    }

    @Test
    fun `updatePrompt updates prompt and clears error`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.updatePrompt("New prompt")
        val form = viewModel.formState.first()
        assert(form.prompt == "New prompt") { "Expected 'New prompt' but got '${form.prompt}'" }
        assert(form.promptError == null) { "Expected promptError to be cleared" }
    }

    @Test
    fun `updateUrl updates URL and clears error`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.updateUrl("https://new-url.com")
        val form = viewModel.formState.first()
        assert(form.url == "https://new-url.com") { "Expected 'https://new-url.com' but got '${form.url}'" }
        assert(form.urlError == null) { "Expected urlError to be cleared" }
    }

    @Test
    fun `updateBearerToken updates token`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.updateBearerToken("new-token")
        val form = viewModel.formState.first()
        assert(form.bearerToken == "new-token") { "Expected 'new-token' but got '${form.bearerToken}'" }
    }

    // ===================== validate =====================

    @Test
    fun `validate returns false when name is blank`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.updateName("")
        viewModel.updatePrompt("Valid prompt")
        viewModel.updateUrl("https://example.com")

        val result = viewModel.validate()
        assert(!result) { "Expected validate()=false when name is blank" }

        val form = viewModel.formState.first()
        assert(form.nameError != null) { "Expected nameError to be set" }
    }

    @Test
    fun `validate returns false when name is too short`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.updateName("A")
        viewModel.updatePrompt("Valid prompt")
        viewModel.updateUrl("https://example.com")

        val result = viewModel.validate()
        assert(!result) { "Expected validate()=false when name is too short" }

        val form = viewModel.formState.first()
        assert(form.nameError != null) { "Expected nameError to be set" }
    }

    @Test
    fun `validate returns false when prompt is blank`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.updateName("Valid Name")
        viewModel.updatePrompt("")
        viewModel.updateUrl("https://example.com")

        val result = viewModel.validate()
        assert(!result) { "Expected validate()=false when prompt is blank" }

        val form = viewModel.formState.first()
        assert(form.promptError != null) { "Expected promptError to be set" }
    }

    @Test
    fun `validate returns false when URL is blank`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.updateName("Valid Name")
        viewModel.updatePrompt("Valid prompt")
        viewModel.updateUrl("")

        val result = viewModel.validate()
        assert(!result) { "Expected validate()=false when URL is blank" }

        val form = viewModel.formState.first()
        assert(form.urlError != null) { "Expected urlError to be set" }
    }

    @Test
    fun `validate returns false when URL has no scheme`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.updateName("Valid Name")
        viewModel.updatePrompt("Valid prompt")
        viewModel.updateUrl("example.com")

        val result = viewModel.validate()
        assert(!result) { "Expected validate()=false when URL has no scheme" }
    }

    @Test
    fun `validate returns true for HTTP URL but sets warning`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.updateName("Valid Name")
        viewModel.updatePrompt("Valid prompt")
        viewModel.updateUrl("http://example.com")

        val result = viewModel.validate()
        assert(result) { "Expected validate()=true for HTTP URL (warning only)" }

        val form = viewModel.formState.first()
        assert(form.urlError != null) { "Expected urlError warning for HTTP" }
        assert(form.urlError?.contains("not secure") == true) {
            "Expected warning about security but got '${form.urlError}'"
        }
    }

    @Test
    fun `validate returns true when all fields are valid`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.updateName("Valid Name")
        viewModel.updatePrompt("Valid prompt")
        viewModel.updateUrl("https://example.com")

        val result = viewModel.validate()
        assert(result) { "Expected validate()=true" }

        val form = viewModel.formState.first()
        assert(form.nameError == null) { "Expected no nameError" }
        assert(form.promptError == null) { "Expected no promptError" }
        assert(form.urlError == null) { "Expected no urlError" }
    }

    @Test
    fun `validate accumulates all errors`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        // All fields invalid
        viewModel.updateName("")
        viewModel.updatePrompt("")
        viewModel.updateUrl("")

        val result = viewModel.validate()
        assert(!result) { "Expected validate()=false" }

        val form = viewModel.formState.first()
        assert(form.nameError != null) { "Expected nameError when name is blank" }
        assert(form.promptError != null) { "Expected promptError when prompt is blank" }
        assert(form.urlError != null) { "Expected urlError when URL is blank" }
    }

    // ===================== saveProfile =====================

    @Test
    fun `saveProfile does nothing when validation fails`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        var onSuccessCalled = false
        viewModel.saveProfile { onSuccessCalled = true }
        advanceUntilIdle()

        assert(!onSuccessCalled) { "Expected onSuccess NOT to be called when validation fails" }
        coVerify(exactly = 0) { profileRepository.insert(any()) }
        coVerify(exactly = 0) { profileRepository.update(any()) }
    }

    @Test
    fun `saveProfile inserts new profile and refreshes shortcuts`() = runTest(testDispatcher) {
        coEvery { profileRepository.insert(any<ProfileEntity>()) } returns 3L
        viewModel = createViewModel()
        // Force subscription to trigger upstream emission so profiles.value is not emptyList()
        viewModel.profiles.first { it.isNotEmpty() }

        viewModel.updateName("New Camera")
        viewModel.updateType("image")
        viewModel.updatePrompt("Describe")
        viewModel.updateUrl("https://example.com/new")
        viewModel.updateBearerToken("")

        var onSuccessCalled = false
        viewModel.saveProfile { onSuccessCalled = true }
        advanceUntilIdle()

        assert(onSuccessCalled) { "Expected onSuccess to be called" }

        coVerify(exactly = 1) {
            profileRepository.insert(
                withArg { profile ->
                    assert(profile.name == "New Camera")
                    assert(profile.type == "image")
                    assert(profile.prompt == "Describe")
                    assert(profile.url == "https://example.com/new")
                    assert(profile.bearerToken == null) // empty string -> null
                }
            )
        }
        verify(exactly = 1) { shortcutHelper.updateDynamicShortcuts(allProfiles) }
    }

    @Test
    fun `saveProfile updates existing profile and refreshes shortcuts`() = runTest(testDispatcher) {
        coEvery { profileRepository.getProfileById(1L) } returns testProfile
        coEvery { profileRepository.update(any<ProfileEntity>()) } returns Unit
        viewModel = createViewModel()
        // Force subscription to trigger upstream emission so profiles.value is not emptyList()
        viewModel.profiles.first { it.isNotEmpty() }

        viewModel.loadProfile(1L)
        advanceUntilIdle()

        viewModel.updateName("Updated Camera")
        viewModel.updatePrompt("Updated description")
        viewModel.updateBearerToken("new-token")

        var onSuccessCalled = false
        viewModel.saveProfile { onSuccessCalled = true }
        advanceUntilIdle()

        assert(onSuccessCalled) { "Expected onSuccess to be called" }

        coVerify(exactly = 1) {
            profileRepository.update(
                withArg { profile ->
                    assert(profile.id == 1L)
                    assert(profile.name == "Updated Camera")
                    assert(profile.prompt == "Updated description")
                    assert(profile.bearerToken == "new-token")
                }
            )
        }
        verify(exactly = 1) { shortcutHelper.removeShortcut(1L) }
        verify(exactly = 1) { shortcutHelper.updateDynamicShortcuts(allProfiles) }
    }

    @Test
    fun `saveProfile handles UNIQUE constraint error`() = runTest(testDispatcher) {
        coEvery { profileRepository.insert(any<ProfileEntity>()) } throws Exception("UNIQUE constraint failed")
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateName("Duplicate Name")
        viewModel.updatePrompt("Prompt")
        viewModel.updateUrl("https://example.com")

        var onSuccessCalled = false
        viewModel.saveProfile { onSuccessCalled = true }
        advanceUntilIdle()

        assert(!onSuccessCalled) { "Expected onSuccess NOT to be called on error" }

        val form = viewModel.formState.first()
        assert(form.nameError?.contains("already exists") == true) {
            "Expected 'already exists' error but got '${form.nameError}'"
        }
        assert(!form.isSaving) { "Expected isSaving=false after error" }
    }

    // ===================== deleteProfile =====================

    @Test
    fun `deleteProfile removes shortcut and deletes profile`() = runTest(testDispatcher) {
        coEvery { profileRepository.delete(testProfile) } returns Unit
        viewModel = createViewModel()
        // Force subscription to trigger upstream emission so profiles.value is not emptyList()
        viewModel.profiles.first { it.isNotEmpty() }

        viewModel.deleteProfile(testProfile)
        advanceUntilIdle()

        verify(exactly = 1) { shortcutHelper.removeShortcut(testProfile.id) }
        coVerify(exactly = 1) { profileRepository.delete(testProfile) }
        verify(exactly = 1) { shortcutHelper.updateDynamicShortcuts(allProfiles) }
    }

    // ===================== shortcut methods =====================

    @Test
    fun `createShortcut delegates to shortcutHelper`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.createShortcut(testProfile)

        verify(exactly = 1) { shortcutHelper.requestPinShortcut(testProfile) }
    }

    @Test
    fun `isShortcutCreated delegates to shortcutHelper`() = runTest(testDispatcher) {
        every { shortcutHelper.isShortcutCreated(1L) } returns true
        every { shortcutHelper.isShortcutCreated(2L) } returns false
        viewModel = createViewModel()

        val result1 = viewModel.isShortcutCreated(1L)
        val result2 = viewModel.isShortcutCreated(2L)

        assert(result1) { "Expected true for profile 1" }
        assert(!result2) { "Expected false for profile 2" }
        verify(exactly = 1) { shortcutHelper.isShortcutCreated(1L) }
        verify(exactly = 1) { shortcutHelper.isShortcutCreated(2L) }
    }

    @Test
    fun `removeShortcut delegates to shortcutHelper`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        viewModel.removeShortcut(1L)

        verify(exactly = 1) { shortcutHelper.removeShortcut(1L) }
    }

    // ===================== buildJsonPayload =====================

    @Test
    fun `buildJsonPayload creates valid JSON structure`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        val base64Data = "SGVsbG8gV29ybGQ="
        val payload = viewModel.buildJsonPayload(testProfile, base64Data)

        val jsonObj = Json.parseToJsonElement(payload).jsonObject

        assert(jsonObj.containsKey("messages")) { "Expected 'messages' key" }

        val messages = jsonObj["messages"]!!.jsonArray
        assert(messages.size == 1) { "Expected 1 message but got ${messages.size}" }

        val message = messages[0].jsonObject
        assert(message["name"]!!.jsonPrimitive.content == testProfile.name) {
            "Expected name '${testProfile.name}' but got '${message["name"]}'"
        }
        assert(message["prompt"]!!.jsonPrimitive.content == testProfile.prompt) {
            "Expected prompt '${testProfile.prompt}' but got '${message["prompt"]}'"
        }
        assert(message["type"]!!.jsonPrimitive.content == testProfile.type) {
            "Expected type '${testProfile.type}' but got '${message["type"]}'"
        }
        assert(message["data"]!!.jsonPrimitive.content == base64Data) {
            "Expected data '$base64Data' but got '${message["data"]}'"
        }
        assert(message.containsKey("datetime")) { "Expected 'datetime' key in message" }
        assert(message["datetime"]!!.jsonPrimitive.content.isNotEmpty()) {
            "Expected non-empty datetime"
        }
    }

    @Test
    fun `buildJsonPayload includes bearer token context`() = runTest(testDispatcher) {
        viewModel = createViewModel()

        val base64Data = "dGVzdA=="
        val payload = viewModel.buildJsonPayload(testProfile, base64Data)

        // The payload JSON should not contain the bearer token directly
        // (token is sent as HTTP header, not in payload)
        assert(!payload.contains(testProfile.bearerToken!!)) {
            "Bearer token should NOT be in JSON payload"
        }
    }

    // ===================== enqueueCapturedMedia =====================

    @Test
    fun `enqueueCapturedMedia saves payload first then inserts item with payloadFilePath`() = runTest(testDispatcher) {
        every { PayloadFileHelper.savePayload(application, any<String>()) } returns "payload_temp_uuid.json"
        coEvery { queueRepository.insert(any<QueueItemEntity>()) } returns 1L
        every { QueueWorker.enqueue(application) } just runs

        viewModel = createViewModel()
        advanceUntilIdle()

        val base64Data = "SGVsbG8gV29ybGQ="
        viewModel.enqueueCapturedMedia(testProfile, base64Data)
        advanceUntilIdle()

        // Payload saved BEFORE insert (no race condition)
        verify(exactly = 1) { PayloadFileHelper.savePayload(application, any<String>()) }

        // Item inserted with payloadFilePath already set (no separate update needed)
        coVerify(exactly = 1) {
            queueRepository.insert(
                withArg { item ->
                    assert(item.profileName == testProfile.name)
                    assert(item.url == testProfile.url)
                    assert(item.bearerToken == testProfile.bearerToken)
                    assert(item.mediaType == testProfile.type)
                    assert(item.status == QueueStatus.PENDING.name)
                    assert(item.payloadFilePath == "payload_temp_uuid.json")
                }
            )
        }

        // No UPDATE call — item is complete on insert
        coVerify(exactly = 0) { queueRepository.update(any<QueueItemEntity>()) }

        verify(exactly = 1) { QueueWorker.enqueue(application) }
    }

    @Test
    fun `enqueueCapturedMedia supports profile without bearer token`() = runTest(testDispatcher) {
        every { PayloadFileHelper.savePayload(application, any<String>()) } returns "payload_temp_uuid.json"
        coEvery { queueRepository.insert(any<QueueItemEntity>()) } returns 2L
        every { QueueWorker.enqueue(application) } just runs

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.enqueueCapturedMedia(testProfile2, "YXVkaW8=")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            queueRepository.insert(
                withArg { item ->
                    assert(item.bearerToken == null)
                    assert(item.mediaType == "audio")
                    assert(item.payloadFilePath == "payload_temp_uuid.json")
                }
            )
        }

        coVerify(exactly = 0) { queueRepository.update(any<QueueItemEntity>()) }
    }
}
