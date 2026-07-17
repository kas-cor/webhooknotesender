package com.kascorp.webhooknotesender.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CaptureButton(
    mediaType: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRecording: Boolean = false
) {
    val icon = when (mediaType.lowercase()) {
        "audio" -> Icons.Filled.Mic
        "video" -> Icons.Filled.Videocam
        else -> Icons.Filled.CameraAlt
    }

    val containerColor = if (isRecording) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val contentColor = if (isRecording) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = CircleShape,
        containerColor = containerColor,
        contentColor = contentColor
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Capture ${mediaType.lowercase()}",
            modifier = Modifier.size(24.dp)
        )
    }
}
