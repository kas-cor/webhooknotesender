package com.kascorp.webhooknotesender.ui.audio

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kascorp.webhooknotesender.R
import com.kascorp.webhooknotesender.ui.components.AudioRecorderService
import com.kascorp.webhooknotesender.ui.theme.RecordRed
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecordingScreen(
    profileId: Long,
    profileName: String,
    profilePrompt: String,
    profileUrl: String,
    bearerToken: String?,
    profileType: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    // Start recording when screen opens
    LaunchedEffect(Unit) {
        val intent = Intent(context, AudioRecorderService::class.java).apply {
            putExtra("profile_id", profileId)
            putExtra("profile_name", profileName)
            putExtra("profile_prompt", profilePrompt)
            putExtra("profile_url", profileUrl)
            putExtra("bearer_token", bearerToken)
            putExtra("profile_type", profileType)
            action = AudioRecorderService.ACTION_START_RECORDING
        }
        context.startForegroundService(intent)
        isRecording = true
    }

    // Timer
    LaunchedEffect(isRecording, isPaused) {
        if (isRecording && !isPaused) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audio_recording_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        val intent = Intent(context, AudioRecorderService::class.java).apply {
                            action = AudioRecorderService.ACTION_STOP_RECORDING
                        }
                        context.startService(intent)
                        isRecording = false
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Profile name
            Text(
                text = profileName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = profilePrompt,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Status icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                            isRecording -> RecordRed.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.cd_microphone),
                    modifier = Modifier.size(60.dp),
                    tint = when {
                        isPaused -> MaterialTheme.colorScheme.tertiary
                        isRecording -> RecordRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status text
            Text(
                text = when {
                    isPaused -> stringResource(R.string.recording_paused)
                    isRecording -> stringResource(R.string.recording)
                    else -> stringResource(R.string.preparing)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    isPaused -> MaterialTheme.colorScheme.tertiary
                    isRecording -> RecordRed
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Timer
            Text(
                text = formatDuration(elapsedSeconds),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause / Resume button
                if (isRecording) {
                    Button(
                        onClick = {
                            if (isPaused) {
                                val intent = Intent(context, AudioRecorderService::class.java).apply {
                                    action = AudioRecorderService.ACTION_RESUME_RECORDING
                                }
                                context.startService(intent)
                                isPaused = false
                            } else {
                                val intent = Intent(context, AudioRecorderService::class.java).apply {
                                    action = AudioRecorderService.ACTION_PAUSE_RECORDING
                                }
                                context.startService(intent)
                                isPaused = true
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaused) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isPaused) MaterialTheme.colorScheme.onTertiary
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (isPaused) stringResource(R.string.cd_resume) else stringResource(R.string.cd_pause),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Stop button
                Button(
                    onClick = {
                        val intent = Intent(context, AudioRecorderService::class.java).apply {
                            action = AudioRecorderService.ACTION_STOP_RECORDING
                        }
                        context.startService(intent)
                        isRecording = false
                        onNavigateBack()
                    },
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RecordRed,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.stop),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isRecording) {
                Text(
                    text = if (isPaused) stringResource(R.string.tap_play_to_continue) else stringResource(R.string.tap_pause_to_pause),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
