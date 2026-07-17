package com.kascorp.webhooknotesender.ui.queue

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Queue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.ui.theme.StatusFailed
import com.kascorp.webhooknotesender.ui.theme.StatusPending
import com.kascorp.webhooknotesender.ui.theme.StatusSending
import com.kascorp.webhooknotesender.ui.theme.StatusSent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QueueScreen(
    viewModel: QueueViewModel
) {
    val items by viewModel.queueItems.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()

    Scaffold { padding ->
        if (items.isEmpty()) {
            EmptyQueueContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Queue",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (pendingCount > 0) {
                            Text(
                                text = "$pendingCount pending",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StatusPending
                            )
                        }
                    }
                    if (items.any { it.status == QueueStatus.FAILED.name }) {
                        TextButton(onClick = { viewModel.retryAllFailed() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry All")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        SwipeableQueueItem(
                            item = item,
                            onDelete = { viewModel.deleteItem(item) },
                            onRetry = { viewModel.retryItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyQueueContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Queue,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Queue is empty",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Captured media will appear here and be sent automatically",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun SwipeableQueueItem(
    item: QueueItemEntity,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        QueueItemCard(item = item, onRetry = onRetry)
    }
}

@Composable
fun QueueItemCard(
    item: QueueItemEntity,
    onRetry: () -> Unit
) {
    val statusColor = when (item.status) {
        QueueStatus.PENDING.name -> StatusPending
        QueueStatus.SENDING.name -> StatusSending
        QueueStatus.SENT.name -> StatusSent
        QueueStatus.FAILED.name -> StatusFailed
        else -> Color.Gray
    }

    val statusLabel = when (item.status) {
        QueueStatus.PENDING.name -> "Pending"
        QueueStatus.SENDING.name -> "Sending..."
        QueueStatus.SENT.name -> "Sent"
        QueueStatus.FAILED.name -> "Failed"
        else -> item.status
    }

    val typeIcon = when (item.mediaType.lowercase()) {
        "audio" -> Icons.Filled.Mic
        "video" -> Icons.Filled.Videocam
        else -> Icons.Filled.CameraAlt
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Type icon
            Icon(
                imageVector = typeIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.profileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.attempts > 0) {
                        Text(
                            text = " · ${item.attempts} attempts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = dateFormat.format(Date(item.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (item.lastError != null) {
                    Text(
                        text = item.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusFailed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Retry button for failed/pending items
            if (item.status == QueueStatus.FAILED.name || item.status == QueueStatus.PENDING.name) {
                IconButton(onClick = onRetry) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Retry",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
