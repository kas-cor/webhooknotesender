package com.kascorp.webhooknotesender.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kascorp.webhooknotesender.R
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.model.MediaType
import com.kascorp.webhooknotesender.data.model.QueueStatus
import com.kascorp.webhooknotesender.data.repository.ProfileRepository
import com.kascorp.webhooknotesender.data.repository.QueueRepository
import com.kascorp.webhooknotesender.data.local.PayloadFileHelper
import com.kascorp.webhooknotesender.util.Base64Encoder
import com.kascorp.webhooknotesender.util.DateTimeUtils
import com.kascorp.webhooknotesender.util.MediaCompressor
import com.kascorp.webhooknotesender.util.ShortcutHelper
import com.kascorp.webhooknotesender.work.QueueWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ProfileFormState(
    val name: String = "",
    val type: String = "image",
    val prompt: String = "",
    val url: String = "",
    val bearerToken: String = "",
    val nameError: String? = null,
    val promptError: String? = null,
    val urlError: String? = null,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val compressEnabled: Boolean = true,
    val compressionQuality: Int = 70
)

data class ProfileEditState(
    val isEditing: Boolean = false,
    val originalProfile: ProfileEntity? = null
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val application: Application,
    private val profileRepository: ProfileRepository,
    private val queueRepository: QueueRepository,
    val base64Encoder: Base64Encoder,
    private val mediaCompressor: MediaCompressor,
    private val shortcutHelper: ShortcutHelper,
    private val json: Json
) : AndroidViewModel(application) {

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _formState = MutableStateFlow(ProfileFormState())
    val formState: StateFlow<ProfileFormState> = _formState.asStateFlow()

    private val _editState = MutableStateFlow(ProfileEditState())
    val editState: StateFlow<ProfileEditState> = _editState.asStateFlow()

    private val testClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun loadProfile(profileId: Long) {
        if (profileId == -1L) {
            _editState.value = ProfileEditState(isEditing = false)
            _formState.value = ProfileFormState()
            return
        }
        viewModelScope.launch {
            val profile = profileRepository.getProfileById(profileId)
            if (profile != null) {
                _editState.value = ProfileEditState(isEditing = true, originalProfile = profile)
                _formState.value = ProfileFormState(
                    name = profile.name,
                    type = profile.type,
                    prompt = profile.prompt,
                    url = profile.url,
                    bearerToken = profile.bearerToken ?: "",
                    compressEnabled = profile.compressEnabled,
                    compressionQuality = profile.compressionQuality
                )
            }
        }
    }

    fun updateName(name: String) {
        _formState.value = _formState.value.copy(name = name, nameError = null)
    }

    fun updateType(type: String) {
        _formState.value = _formState.value.copy(type = type)
    }

    fun updatePrompt(prompt: String) {
        _formState.value = _formState.value.copy(prompt = prompt, promptError = null)
    }

    fun updateUrl(url: String) {
        _formState.value = _formState.value.copy(url = url, urlError = null)
    }

    fun updateBearerToken(token: String) {
        _formState.value = _formState.value.copy(bearerToken = token)
    }

    fun updateCompressEnabled(enabled: Boolean) {
        _formState.value = _formState.value.copy(compressEnabled = enabled)
    }

    fun updateCompressionQuality(quality: Int) {
        _formState.value = _formState.value.copy(compressionQuality = quality.coerceIn(1, 100))
    }

    fun validate(): Boolean {
        val state = _formState.value
        var hasError = false

        val nameError = when {
            state.name.isBlank() -> { hasError = true; application.getString(R.string.name_required) }
            state.name.length < 2 -> { hasError = true; application.getString(R.string.name_min_length) }
            else -> null
        }

        val promptError = when {
            state.prompt.isBlank() -> { hasError = true; application.getString(R.string.prompt_required) }
            else -> null
        }

        val urlError = when {
            state.url.isBlank() -> { hasError = true; application.getString(R.string.url_required) }
            !state.url.startsWith("http://") && !state.url.startsWith("https://") ->
                { hasError = true; application.getString(R.string.url_invalid) }
            state.url.startsWith("http://") -> application.getString(R.string.url_http_warning)
            else -> null
        }

        _formState.value = _formState.value.copy(
            nameError = nameError,
            promptError = promptError,
            urlError = urlError
        )

        return !hasError
    }

    fun saveProfile(onSuccess: () -> Unit) {
        if (!validate()) return

        val state = _formState.value
        _formState.value = state.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val editInfo = _editState.value
                if (editInfo.isEditing && editInfo.originalProfile != null) {
                    val profile = editInfo.originalProfile.copy(
                        name = state.name.trim(),
                        type = state.type,
                        prompt = state.prompt.trim(),
                        url = state.url.trim(),
                        bearerToken = state.bearerToken.trim().ifEmpty { null },
                        compressEnabled = state.compressEnabled,
                        compressionQuality = state.compressionQuality
                    )
                    profileRepository.update(profile)
                    // Remove stale shortcut entry so it can be re-created with updated info
                    shortcutHelper.removeShortcut(profile.id)
                    // Update app shortcuts to reflect renamed profile
                    val topProfiles = profileRepository.getTopProfiles(5).first()
                    shortcutHelper.updateAppShortcuts(topProfiles)
                } else {
                    val profile = ProfileEntity(
                        name = state.name.trim(),
                        type = state.type,
                        prompt = state.prompt.trim(),
                        url = state.url.trim(),
                        bearerToken = state.bearerToken.trim().ifEmpty { null },
                        compressEnabled = state.compressEnabled,
                        compressionQuality = state.compressionQuality
                    )
                    profileRepository.insert(profile)
                }
                onSuccess()
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    nameError = if (e.message?.contains("UNIQUE") == true) application.getString(R.string.name_already_exists) else null,
                    isSaving = false
                )
            }
        }
    }

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            shortcutHelper.removeShortcut(profile.id)
            profileRepository.delete(profile)
            // Update app shortcuts to remove the deleted profile
            val topProfiles = profileRepository.getTopProfiles(5).first()
            shortcutHelper.updateAppShortcuts(topProfiles)
        }
    }

    fun createShortcut(profile: ProfileEntity) {
        shortcutHelper.requestPinShortcut(profile)
    }

    fun isShortcutCreated(profileId: Long): Boolean {
        return shortcutHelper.isShortcutCreated(profileId)
    }

    fun removeShortcut(profileId: Long) {
        shortcutHelper.removeShortcut(profileId)
    }

    fun buildJsonPayload(profile: ProfileEntity, base64Data: String, encoding: String? = null): String {
        val messageMap = mutableMapOf(
            "name" to JsonPrimitive(profile.name),
            "prompt" to JsonPrimitive(profile.prompt),
            "datetime" to JsonPrimitive(DateTimeUtils.nowUtcIso8601()),
            "type" to JsonPrimitive(profile.type),
            "data" to JsonPrimitive(base64Data)
        )
        if (encoding != null) {
            messageMap["encoding"] = JsonPrimitive(encoding)
        }
        val payload = JsonObject(
            mapOf(
                "messages" to JsonArray(listOf(JsonObject(messageMap)))
            )
        )
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    data class CompressAndEncodeResult(
        val base64: String,
        val encoding: String?,
        val originalSize: Long = 0L,
        val compressedSize: Long = 0L
    )

    fun compressAndEncode(profile: ProfileEntity, bytes: ByteArray): CompressAndEncodeResult {
        if (!profile.compressEnabled) {
            val encoded = base64Encoder.encode(bytes)
            return CompressAndEncodeResult(encoded, null, bytes.size.toLong(), bytes.size.toLong())
        }
        val result = mediaCompressor.compress(bytes, profile.type, profile.compressionQuality)
        return CompressAndEncodeResult(
            base64 = base64Encoder.encode(result.data),
            encoding = result.encoding,
            originalSize = result.originalSize,
            compressedSize = result.compressedSize
        )
    }

    fun enqueueCapturedMedia(profile: ProfileEntity, base64Data: String, encoding: String? = null) {
        viewModelScope.launch {
            val payload = buildJsonPayload(profile, base64Data, encoding)
            // Save payload to file FIRST (before DB insert) to avoid race condition:
            // QueueWorker could pick up the item before the payload file is saved.
            val fileName = PayloadFileHelper.savePayload(application, payload)
            val queueItem = QueueItemEntity(
                profileName = profile.name,
                url = profile.url,
                bearerToken = profile.bearerToken,
                jsonPayload = "",
                payloadFilePath = fileName,
                mediaType = profile.type,
                status = QueueStatus.PENDING.name
            )
            queueRepository.insert(queueItem)
            // Trigger queue processing
            QueueWorker.enqueue(application)
            // Track usage for app shortcuts ranking
            profileRepository.incrementUseCount(profile.id)
            // Update app shortcuts (long-press app icon) with new rankings
            val topProfiles = profileRepository.getTopProfiles(5).first()
            shortcutHelper.updateAppShortcuts(topProfiles)
        }
    }

    fun testWebhook(url: String, bearerToken: String?) {
        viewModelScope.launch {
            _formState.value = _formState.value.copy(isTesting = true, testResult = null)
            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")

                if (!bearerToken.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $bearerToken")
                }

                val testPayload = JsonObject(
                    mapOf(
                        "messages" to JsonArray(
                            listOf(
                                JsonObject(
                                    mapOf(
                                        "name" to JsonPrimitive("test"),
                                        "prompt" to JsonPrimitive("test"),
                                        "datetime" to JsonPrimitive(DateTimeUtils.nowUtcIso8601()),
                                        "type" to JsonPrimitive("image"),
                                        "data" to JsonPrimitive("")
                                    )
                                )
                            )
                        )
                    )
                )
                val jsonBody = json.encodeToString(JsonObject.serializer(), testPayload)
                val body = jsonBody.toRequestBody("application/json".toMediaType())

                val request = requestBuilder.post(body).build()
                val response = testClient.newCall(request).execute()

                _formState.value = _formState.value.copy(
                    isTesting = false,
                    testResult = if (response.isSuccessful) {
                        application.getString(R.string.test_success_format, response.code)
                    } else {
                        application.getString(R.string.test_send_failed, "HTTP ${response.code}")
                    }
                )
            } catch (e: Exception) {
                _formState.value = _formState.value.copy(
                    isTesting = false,
                    testResult = application.getString(R.string.test_send_failed, e.message ?: "Unknown")
                )
            }
        }
    }
}
