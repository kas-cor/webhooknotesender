package com.kascorp.webhooknotesender.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kascorp.webhooknotesender.ui.theme.StatusFailed
import com.kascorp.webhooknotesender.ui.theme.StatusPending
import com.kascorp.webhooknotesender.ui.theme.StatusSending
import com.kascorp.webhooknotesender.ui.theme.StatusSent

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val statusColor = when (status) {
        "PENDING" -> StatusPending
        "SENDING" -> StatusSending
        "SENT" -> StatusSent
        "FAILED" -> StatusFailed
        else -> Color.Gray
    }

    val statusLabel = when (status) {
        "PENDING" -> "Pending"
        "SENDING" -> "Sending"
        "SENT" -> "Sent"
        "FAILED" -> "Failed"
        else -> status
    }

    Row(
        modifier = modifier
            .background(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor
        )
    }
}
