package com.kascorp.webhooknotesender.ui.profiles

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Shortcut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import com.kascorp.webhooknotesender.ui.components.AudioRecorderService
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel = hiltViewModel(),
    onEditProfile: (Long) -> Unit = {},
    onCreateProfile: () -> Unit = {}
) {
    val profiles by viewModel.profiles.collectAsState()
    val context = LocalContext.current

    var pendingCaptureProfile by remember { mutableStateOf<ProfileEntity?>(null) }

    val imageCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val profile = pendingCaptureProfile
        pendingCaptureProfile = null
        if (success && profile != null) {
            val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                val base64 = viewModel.base64Encoder.encode(bytes)
                viewModel.enqueueCapturedMedia(profile, base64)
            }
            if (file.exists()) file.delete()
            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Capture cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    val videoCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        val profile = pendingCaptureProfile
        pendingCaptureProfile = null
        if (success && profile != null) {
            val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.mp4")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                val base64 = viewModel.base64Encoder.encode(bytes)
                viewModel.enqueueCapturedMedia(profile, base64)
            }
            if (file.exists()) file.delete()
            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Capture cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateProfile,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create profile")
            }
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            EmptyProfilesContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onCreateProfile = onCreateProfile
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Your Profiles",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onEdit = { onEditProfile(profile.id) },
                        onDelete = { viewModel.deleteProfile(profile) },
                        onCapture = {
                            pendingCaptureProfile = profile
                            val type = profile.type.lowercase()
                            if (type == "audio") {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                    == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val intent = android.content.Intent(context, AudioRecorderService::class.java).apply {
                                        putExtra("profile_id", profile.id)
                                        putExtra("profile_name", profile.name)
                                        putExtra("profile_prompt", profile.prompt)
                                        putExtra("profile_url", profile.url)
                                        putExtra("bearer_token", profile.bearerToken)
                                        putExtra("profile_type", profile.type)
                                        action = AudioRecorderService.ACTION_START_RECORDING
                                    }
                                    ContextCompat.startForegroundService(context, intent)
                                    Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
                                }
                            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                val file = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.${if (type == "video") "mp4" else "jpg"}")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                if (type == "video") {
                                    videoCaptureLauncher.launch(uri)
                                } else {
                                    imageCaptureLauncher.launch(uri)
                                }
                            } else {
                                Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCreateShortcut = { viewModel.createShortcut(profile) },
                        hasShortcut = viewModel.isShortcutCreated(profile.id)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProfilesContent(
    modifier: Modifier = Modifier,
    onCreateProfile: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No profiles yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create a profile to start sending media to webhooks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onCreateProfile) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Profile")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileCard(
    profile: ProfileEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCapture: () -> Unit,
    onCreateShortcut: () -> Unit,
    hasShortcut: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }

    val typeIcon = when (profile.type.lowercase()) {
        "audio" -> Icons.Filled.Mic
        "video" -> Icons.Filled.Videocam
        else -> Icons.Filled.CameraAlt
    }

    val typeLabel = when (profile.type.lowercase()) {
        "audio" -> "Audio"
        "video" -> "Video"
        else -> "Image"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onCapture,
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = typeLabel,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasShortcut) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Shortcut,
                            contentDescription = "Has shortcut",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            IconButton(
                onClick = onCapture,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = when (profile.type.lowercase()) {
                        "audio" -> Icons.Filled.Mic
                        "video" -> Icons.Filled.Videocam
                        else -> Icons.Filled.CameraAlt
                    },
                    contentDescription = "Capture ${typeLabel.lowercase()}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (hasShortcut) "Remove Shortcut" else "Create Shortcut") },
                        onClick = {
                            showMenu = false
                            onCreateShortcut()
                        },
                        leadingIcon = {
                            Icon(
                                if (hasShortcut) Icons.Outlined.Shortcut else Icons.Filled.Shortcut,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}
