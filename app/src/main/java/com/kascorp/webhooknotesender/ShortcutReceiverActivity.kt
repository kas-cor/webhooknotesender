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
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.data.model.MediaType
import com.kascorp.webhooknotesender.ui.components.AudioRecorderService
import com.kascorp.webhooknotesender.util.Base64Encoder
import com.kascorp.webhooknotesender.util.DateTimeUtils
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
        val audioFile = File(cacheDir, "audio_${System.currentTimeMillis()}.aac")
        currentAudioFile = audioFile
        val intent = Intent(this, AudioRecorderService::class.java).apply {
            putExtra("output_file", audioFile.absolutePath)
            putExtra("profile_id", profile.id)
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, getString(R.string.audio_recording_started), Toast.LENGTH_SHORT).show()
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

                val base64Data = base64Encoder.encodeFile(file)
                val jsonPayload = buildJsonPayload(profile, base64Data)

                // Delete temp file before inserting to queue
                if (file.exists()) {
                    file.delete()
                }

                database.queueDao().insert(
                    QueueItemEntity(
                        profileName = profile.name,
                        url = profile.url,
                        bearerToken = profile.bearerToken,
                        jsonPayload = jsonPayload,
                        mediaType = profile.type,
                        status = QueueStatus.PENDING.name,
                        attempts = 0,
                        lastError = null,
                        createdAt = System.currentTimeMillis()
                    )
                )

                // Trigger queue processing
                QueueWorker.enqueue(this@ShortcutReceiverActivity)

                runOnUiThread {
                    Toast.makeText(this@ShortcutReceiverActivity, getString(R.string.added_to_queue), Toast.LENGTH_SHORT).show()
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

    private fun buildJsonPayload(profile: ProfileEntity, base64Data: String): String {
        val message = JsonObject(
            mapOf(
                "name" to JsonPrimitive(profile.name),
                "prompt" to JsonPrimitive(profile.prompt),
                "datetime" to JsonPrimitive(DateTimeUtils.nowUtcIso8601()),
                "type" to JsonPrimitive(profile.type),
                "data" to JsonPrimitive(base64Data)
            )
        )
        val payload = JsonObject(
            mapOf(
                "messages" to JsonArray(listOf(message))
            )
        )
        return json.encodeToString(JsonObject.serializer(), payload)
    }
}
