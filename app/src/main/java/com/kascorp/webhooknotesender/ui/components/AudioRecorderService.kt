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
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
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
class AudioRecorderService : Service() {

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var base64Encoder: Base64Encoder

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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                profileId = intent.getLongExtra("profile_id", -1L)
                profileName = intent.getStringExtra("profile_name") ?: ""
                profilePrompt = intent.getStringExtra("profile_prompt") ?: ""
                profileUrl = intent.getStringExtra("profile_url") ?: ""
                bearerToken = intent.getStringExtra("bearer_token")
                profileType = intent.getStringExtra("profile_type") ?: "audio"

                // If profile details not provided (shortcut flow), load from database
                if (profileName.isEmpty() && profileId > 0) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val profile = database.profileDao().getProfileById(profileId)
                        if (profile != null) {
                            profileName = profile.name
                            profilePrompt = profile.prompt
                            profileUrl = profile.url
                            bearerToken = profile.bearerToken
                            profileType = profile.type
                        }
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
                setAudioSamplingRate(44100)
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
                val base64Data = base64Encoder.encodeFile(file)

                val message = JsonObject(
                    mapOf(
                        "name" to JsonPrimitive(profileName),
                        "prompt" to JsonPrimitive(profilePrompt),
                        "datetime" to JsonPrimitive(DateTimeUtils.nowUtcIso8601()),
                        "type" to JsonPrimitive(profileType),
                        "data" to JsonPrimitive(base64Data)
                    )
                )
                val payload = JsonObject(
                    mapOf(
                        "messages" to JsonArray(listOf(message))
                    )
                )
                val jsonPayload = json.encodeToString(JsonObject.serializer(), payload)

                database.queueDao().insert(
                    QueueItemEntity(
                        profileName = profileName,
                        url = profileUrl,
                        bearerToken = bearerToken,
                        jsonPayload = jsonPayload,
                        mediaType = profileType,
                        status = QueueStatus.PENDING.name,
                        attempts = 0,
                        lastError = null,
                        createdAt = System.currentTimeMillis()
                    )
                )

                if (file.exists()) {
                    file.delete()
                }

                QueueWorker.enqueue(this@AudioRecorderService)

                mainHandler.post {
                    Toast.makeText(this@AudioRecorderService, getString(R.string.added_to_queue), Toast.LENGTH_SHORT).show()
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

    companion object {
        const val ACTION_START_RECORDING = "com.kascorp.webhooknotesender.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.kascorp.webhooknotesender.STOP_RECORDING"
        private const val CHANNEL_ID = "audio_recording"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "AudioRecorderService"
    }
}
