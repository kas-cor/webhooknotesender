package com.kascorp.webhooknotesender.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaType {
    IMAGE,
    AUDIO,
    VIDEO
}
