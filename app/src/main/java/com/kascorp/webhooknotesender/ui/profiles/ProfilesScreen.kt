package com.kascorp.webhooknotesender.ui.profiles

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.kascorp.webhooknotesender.R
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel = hiltViewModel(),
    onEditProfile: (Long) -> Unit = {},
    onCreateProfile: () -> Unit = {},
    onAudioCapture: (
        profileId: Long,
        profileName: String,
        profilePrompt: String,
        profileUrl: String,
        bearerToken: String?
    ) -> Unit = { _, _, _, _, _ -> }
) {
    val profiles by viewModel.profiles.collectAsState()
    val context = LocalContext.current

    var pendingCaptureProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000 -> context.getString(R.string.size_mb, bytes / 1_000_000.0)
            bytes >= 1_000 -> context.getString(R.string.size_kb, bytes / 1_000.0)
            else -> context.getString(R.string.size_b, bytes)
        }
    }

    val imageCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val profile = pendingCaptureProfile
        val uri = pendingCaptureUri
        pendingCaptureProfile = null
        pendingCaptureUri = null
        if (success && profile != null && uri != null) {
            var result: ProfilesViewModel.CompressAndEncodeResult? = null
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                result = viewModel.compressAndEncode(profile, bytes)
                viewModel.enqueueCapturedMedia(profile, result!!.base64, result.encoding)
            }
            // Clean up temp file from cache dir
            val fileName = uri.lastPathSegment
            if (fileName != null) {
                val tempFile = File(context.cacheDir, fileName)
                if (tempFile.exists()) tempFile.delete()
            }
            val r = result
            val msg = if (r != null && profile.compressEnabled && r.compressedSize > 0 && r.compressedSize < r.originalSize) {
                val saved = (100L - r.compressedSize * 100L / r.originalSize)
                context.getString(R.string.compressed_format, formatSize(r.originalSize), formatSize(r.compressedSize), saved.toInt())
            } else {
                context.getString(R.string.added_to_queue)
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, context.getString(R.string.capture_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    val videoCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        val profile = pendingCaptureProfile
        val uri = pendingCaptureUri
        pendingCaptureProfile = null
        pendingCaptureUri = null
        if (success && profile != null && uri != null) {
            var result: ProfilesViewModel.CompressAndEncodeResult? = null
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                result = viewModel.compressAndEncode(profile, bytes)
                viewModel.enqueueCapturedMedia(profile, result!!.base64, result.encoding)
            }
            // Clean up temp file from cache dir
            val fileName = uri.lastPathSegment
            if (fileName != null) {
                val tempFile = File(context.cacheDir, fileName)
                if (tempFile.exists()) tempFile.delete()
            }
            val r = result
            val msg = if (r != null && profile.compressEnabled && r.compressedSize > 0 && r.compressedSize < r.originalSize) {
                val saved = (100L - r.compressedSize * 100L / r.originalSize)
                context.getString(R.string.compressed_format, formatSize(r.originalSize), formatSize(r.compressedSize), saved.toInt())
            } else {
                context.getString(R.string.added_to_queue)
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, context.getString(R.string.capture_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val profile = pendingCaptureProfile
        if (granted && profile != null) {
            val type = profile.type.lowercase()
            val file = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.${if (type == "video") "mp4" else "jpg"}")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCaptureUri = uri
            if (type == "video") {
                videoCaptureLauncher.launch(uri)
            } else {
                imageCaptureLauncher.launch(uri)
            }
        } else {
            pendingCaptureProfile = null
            Toast.makeText(context, context.getString(R.string.permission_required, "Camera"), Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val profile = pendingCaptureProfile
        pendingCaptureProfile = null
        if (granted && profile != null) {
            // Navigate to the new AudioRecordingScreen instead of starting the service directly
            onAudioCapture(
                profile.id,
                profile.name,
                profile.prompt,
                profile.url,
                profile.bearerToken
            )
        } else {
            Toast.makeText(context, context.getString(R.string.permission_required, "Microphone"), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateProfile,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_profile))
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
                        text = stringResource(R.string.your_profiles),
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
                                    onAudioCapture(
                                        profile.id,
                                        profile.name,
                                        profile.prompt,
                                        profile.url,
                                        profile.bearerToken
                                    )
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                val file = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.${if (type == "video") "mp4" else "jpg"}")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                pendingCaptureUri = uri
                                if (type == "video") {
                                    videoCaptureLauncher.launch(uri)
                                } else {
                                    imageCaptureLauncher.launch(uri)
                                }
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_profiles_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_profiles_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onCreateProfile) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.create_profile))
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val typeIcon = when (profile.type.lowercase()) {
        "audio" -> Icons.Filled.Mic
        "video" -> Icons.Filled.Videocam
        else -> Icons.Filled.CameraAlt
    }

    val typeLabelRes = when (profile.type.lowercase()) {
        "audio" -> R.string.audio_type
        "video" -> R.string.video_type
        else -> R.string.image_type
    }

    val (cardAccentColor, cardGradient) = when (profile.type.lowercase()) {
        "audio" -> Color(0xFF7C4DFF) to listOf(Color(0xFF7C4DFF), Color(0xFFB388FF))
        "video" -> Color(0xFF00C853) to listOf(Color(0xFF00C853), Color(0xFF69F0AE))
        else -> Color(0xFF448AFF) to listOf(Color(0xFF448AFF), Color(0xFF82B1FF))
    }

    val cardElevation by animateDpAsState(
        targetValue = if (isPressed) 8.dp else 3.dp,
        animationSpec = tween(150),
        label = "cardElevation"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(100),
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onCapture()
            }
            .combinedClickable(
                onLongClick = { showMenu = true },
                onClick = {}
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation)
    ) {
        // Colorful top strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    brush = Brush.horizontalGradient(cardGradient)
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colorful icon circle
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.horizontalGradient(cardGradient)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = stringResource(typeLabelRes),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Type chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(cardAccentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(typeLabelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = cardAccentColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (hasShortcut) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Shortcut,
                            contentDescription = stringResource(R.string.cd_has_shortcut),
                            modifier = Modifier.size(14.dp),
                            tint = cardAccentColor.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.tap_to_capture),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_edit)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(if (hasShortcut) stringResource(R.string.remove_shortcut) else stringResource(R.string.create_shortcut)) },
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
                        text = { Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error) },
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
