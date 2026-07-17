package com.kascorp.webhooknotesender.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class QueueStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED
}
