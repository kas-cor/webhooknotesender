package com.kascorp.webhooknotesender.ui.components

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.kascorp.webhooknotesender.MainActivity
import com.kascorp.webhooknotesender.R
import com.kascorp.webhooknotesender.data.local.AppDatabase
import com.kascorp.webhooknotesender.data.local.PayloadFileHelper
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.util.Base64Encoder
import com.kascorp.webhooknotesender.util.DateTimeUtils
import com.kascorp.webhooknotesender.util.MediaCompressor
import com.kascorp.webhooknotesender.work.QueueWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class AudioRecorderService : Service() {

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var base64Encoder: Base64Encoder

    @Inject
    lateinit var mediaCompressor: MediaCompressor

    @Inject
    lateinit var json: Json

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var profileId: Long = -1L
    private var profileName: String = ""
    private var profilePrompt: String = ""
    private var profileUrl: String = ""
    private var bearerToken: String? = null
    private var profileType: String = "audio"
    private var compressEnabled: Boolean = false
    private var compressionQuality: Int = 70

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    var isPaused: Boolean = false
        private set

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                profileId = intent.getLongExtra("profile_id", -1L)
                profileName = intent.getStringExtra("profile_name") ?: ""
                profilePrompt = intent.getStringExtra("profile_prompt") ?: ""
                profileUrl = intent.getStringExtra("profile_url") ?: ""
                bearerToken = intent.getStringExtra("bearer_token")
                profileType = intent.getStringExtra("profile_type") ?: "audio"

                // Load profile from database to ensure latest settings (compression, etc.)
                if (profileId > 0) {
                    val profile = runBlocking(Dispatchers.IO) {
                        database.profileDao().getProfileById(profileId)
                    }
                    if (profile != null) {
                        profileName = profile.name
                        profilePrompt = profile.prompt
                        profileUrl = profile.url
                        bearerToken = profile.bearerToken
                        profileType = profile.type
                        compressEnabled = profile.compressEnabled
                        compressionQuality = profile.compressionQuality
                    }
                }

                val directOutput = intent.getStringExtra("output_file")
                if (directOutput != null) {
                    outputFile = File(directOutput)
                }

                startRecording()
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
            ACTION_PAUSE_RECORDING -> {
                pauseRecording()
            }
            ACTION_RESUME_RECORDING -> {
                resumeRecording()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording() {
        try {
            if (outputFile == null) {
                outputFile = File(cacheDir, "audio_recording_${System.currentTimeMillis()}.aac")
            }

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(if (compressEnabled) 22050 else 44100)
                setAudioEncodingBitRate(if (compressEnabled) 32000 else 128000)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }

            val notification = createRecordingNotification()
            startForeground(NOTIFICATION_ID, notification)

            mainHandler.post {
                Toast.makeText(this@AudioRecorderService, getString(R.string.audio_recording_started), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            mainHandler.post {
                Toast.makeText(this@AudioRecorderService, "${getString(R.string.error_recording)}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            stopSelf()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            mainHandler.post {
                Toast.makeText(this@AudioRecorderService, getString(R.string.audio_recording_stopped), Toast.LENGTH_SHORT).show()
            }

            processAudioFile()

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.message}", e)
            mainHandler.post {
                Toast.makeText(this@AudioRecorderService, "${getString(R.string.error_recording)}: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun processAudioFile() {
        val file = outputFile ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val base64Data: String
                val encoding: String?
                var originalSize = 0L
                var compressedSize = 0L
                if (compressEnabled) {
                    val result = mediaCompressor.compressFile(file, profileType, compressionQuality)
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

                val messageMap = mutableMapOf(
                    "name" to JsonPrimitive(profileName),
                    "prompt" to JsonPrimitive(profilePrompt),
                    "datetime" to JsonPrimitive(DateTimeUtils.nowUtcIso8601()),
                    "type" to JsonPrimitive(profileType),
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
                val jsonPayload = json.encodeToString(JsonObject.serializer(), payload)

                // Save payload to file FIRST (before DB insert) to avoid race condition
                val fileName = PayloadFileHelper.savePayload(this@AudioRecorderService, jsonPayload)
                val queueItem = QueueItemEntity(
                    profileName = profileName,
                    url = profileUrl,
                    bearerToken = bearerToken,
                    jsonPayload = "",
                    payloadFilePath = fileName,
                    mediaType = profileType,
                    status = QueueStatus.PENDING.name,
                    attempts = 0,
                    lastError = null,
                    createdAt = System.currentTimeMillis()
                )
                database.queueDao().insert(queueItem)

                // Track usage for app shortcuts ranking
                if (profileId > 0) {
                    database.profileDao().incrementUseCount(profileId)
                }

                if (file.exists()) {
                    file.delete()
                }

                QueueWorker.enqueue(this@AudioRecorderService)

                mainHandler.post {
                    val msg = if (compressEnabled && compressedSize > 0 && compressedSize < originalSize) {
                        val saved = (100L - compressedSize * 100L / originalSize)
                        getString(R.string.compressed_format, formatSize(originalSize), formatSize(compressedSize), saved.toInt())
                    } else {
                        getString(R.string.added_to_queue)
                    }
                    Toast.makeText(this@AudioRecorderService, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process audio: ${e.message}", e)
                mainHandler.post {
                    Toast.makeText(this@AudioRecorderService, "${getString(R.string.error_processing)}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.audio_recording_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.audio_recording_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createRecordingNotification(): Notification {
        val stopIntent = Intent(this, AudioRecorderService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(getString(R.string.notification_recording_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.stop),
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun pauseRecording() {
        try {
            mediaRecorder?.pause()
            isPaused = true
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause recording: ${e.message}", e)
        }
    }

    private fun resumeRecording() {
        try {
            mediaRecorder?.resume()
            isPaused = false
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume recording: ${e.message}", e)
        }
    }

    private fun updateNotification() {
        val notification = createRecordingNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000 -> getString(R.string.size_mb, bytes / 1_000_000.0)
            bytes >= 1_000 -> getString(R.string.size_kb, bytes / 1_000.0)
            else -> getString(R.string.size_b, bytes)
        }
    }

    companion object {
        const val ACTION_START_RECORDING = "com.kascorp.webhooknotesender.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.kascorp.webhooknotesender.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.kascorp.webhooknotesender.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.kascorp.webhooknotesender.RESUME_RECORDING"
        private const val CHANNEL_ID = "audio_recording"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "AudioRecorderService"
    }
}
