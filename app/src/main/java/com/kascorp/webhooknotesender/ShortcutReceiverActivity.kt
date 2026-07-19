package com.kascorp.webhooknotesender

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.kascorp.webhooknotesender.data.local.AppDatabase
import com.kascorp.webhooknotesender.data.local.PayloadFileHelper
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.data.model.MediaType
import com.kascorp.webhooknotesender.util.Base64Encoder
import com.kascorp.webhooknotesender.util.DateTimeUtils
import com.kascorp.webhooknotesender.util.MediaCompressor
import com.kascorp.webhooknotesender.work.QueueWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ShortcutReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var base64Encoder: Base64Encoder

    @Inject
    lateinit var mediaCompressor: MediaCompressor

    @Inject
    lateinit var json: Json

    private var profileId: Long = -1L
    private var currentProfile: ProfileEntity? = null
    private var currentPhotoFile: File? = null
    private var currentVideoFile: File? = null
    private var currentAudioFile: File? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoFile != null) {
            processCapturedFile(currentPhotoFile!!, MediaType.IMAGE)
        } else {
            Toast.makeText(this, getString(R.string.capture_cancelled), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val captureVideoLauncher = registerForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && currentVideoFile != null) {
            processCapturedFile(currentVideoFile!!, MediaType.VIDEO)
        } else {
            Toast.makeText(this, getString(R.string.capture_cancelled), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            loadProfileAndCapture()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            loadProfileAndCapture()
        } else {
            Toast.makeText(this, getString(R.string.microphone_permission_denied), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileId = intent.getLongExtra("profile_id", -1L)
        if (profileId == -1L) {
            Toast.makeText(this, getString(R.string.profile_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        loadProfileAndCapture()
    }

    private fun loadProfileAndCapture() {
        CoroutineScope(Dispatchers.IO).launch {
            val profile = database.profileDao().getProfileById(profileId)
            if (profile == null) {
                runOnUiThread {
                    Toast.makeText(this@ShortcutReceiverActivity, getString(R.string.profile_not_found), Toast.LENGTH_SHORT).show()
                    finish()
                }
                return@launch
            }
            currentProfile = profile

            val mediaType = MediaType.valueOf(profile.type.uppercase())
            runOnUiThread {
                when (mediaType) {
                    MediaType.IMAGE -> requestCameraAndCapture(profile, MediaType.IMAGE)
                    MediaType.VIDEO -> requestCameraAndCapture(profile, MediaType.VIDEO)
                    MediaType.AUDIO -> requestAudioAndRecord(profile)
                }
            }
        }
    }

    private fun requestCameraAndCapture(profile: ProfileEntity, mediaType: MediaType) {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (permissions.isEmpty()) {
            startMediaCapture(profile, mediaType)
        } else {
            cameraPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun requestAudioAndRecord(profile: ProfileEntity) {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isEmpty()) {
            startAudioRecording(profile)
        } else {
            audioPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startMediaCapture(profile: ProfileEntity, mediaType: MediaType) {
        val file = File(cacheDir, "${mediaType.name.lowercase()}_${System.currentTimeMillis()}.${if (mediaType == MediaType.IMAGE) "jpg" else "mp4"}")
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        when (mediaType) {
            MediaType.IMAGE -> {
                currentPhotoFile = file
                takePictureLauncher.launch(uri)
            }
            MediaType.VIDEO -> {
                currentVideoFile = file
                captureVideoLauncher.launch(uri)
            }
            else -> {}
        }
    }

    private fun startAudioRecording(profile: ProfileEntity) {
        // Launch MainActivity which will navigate to the AudioRecordingScreen
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("shortcut_audio", true)
            putExtra("profile_id", profile.id)
            putExtra("profile_name", profile.name)
            putExtra("profile_prompt", profile.prompt)
            putExtra("profile_url", profile.url)
            putExtra("bearer_token", profile.bearerToken)
        }
        startActivity(launchIntent)
        finish()
    }

    private fun processCapturedFile(file: File, mediaType: MediaType) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profile = currentProfile
                if (profile == null) {
                    runOnUiThread {
                        Toast.makeText(this@ShortcutReceiverActivity, getString(R.string.profile_not_found), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                val base64Data: String
                val encoding: String?
                var originalSize = 0L
                var compressedSize = 0L
                if (profile.compressEnabled) {
                    val result = mediaCompressor.compressFile(file, profile.type, profile.compressionQuality)
                    base64Data = base64Encoder.encode(result.data)
                    encoding = result.encoding
                    originalSize = result.originalSize
                    compressedSize = result.compressedSize
                } else {
                    base64Data = base64Encoder.encodeFile(file)
                    encoding = null
                    originalSize = file.length()
                    compressedSize = originalSize
                }
                val jsonPayload = buildJsonPayload(profile, base64Data, encoding)

                // Delete temp file before inserting to queue
                if (file.exists()) {
                    file.delete()
                }

                // Save payload to file FIRST (before DB insert) to avoid race condition
                val fileName = PayloadFileHelper.savePayload(this@ShortcutReceiverActivity, jsonPayload)
                val queueItem = QueueItemEntity(
                    profileName = profile.name,
                    url = profile.url,
                    bearerToken = profile.bearerToken,
                    jsonPayload = "",
                    payloadFilePath = fileName,
                    mediaType = profile.type,
                    status = QueueStatus.PENDING.name,
                    attempts = 0,
                    lastError = null,
                    createdAt = System.currentTimeMillis()
                )
                database.queueDao().insert(queueItem)

                // Trigger queue processing
                QueueWorker.enqueue(this@ShortcutReceiverActivity)

                runOnUiThread {
                    val msg = if (profile.compressEnabled && compressedSize > 0 && compressedSize < originalSize) {
                        val saved = (100L - compressedSize * 100L / originalSize)
                        getString(R.string.compressed_format, formatSize(originalSize), formatSize(compressedSize), saved.toInt())
                    } else {
                        getString(R.string.added_to_queue)
                    }
                    Toast.makeText(this@ShortcutReceiverActivity, msg, Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ShortcutReceiverActivity, "${getString(R.string.error_processing)}: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000 -> getString(R.string.size_mb, bytes / 1_000_000.0)
            bytes >= 1_000 -> getString(R.string.size_kb, bytes / 1_000.0)
            else -> getString(R.string.size_b, bytes)
        }
    }

    private fun buildJsonPayload(profile: ProfileEntity, base64Data: String, encoding: String? = null): String {
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
}
