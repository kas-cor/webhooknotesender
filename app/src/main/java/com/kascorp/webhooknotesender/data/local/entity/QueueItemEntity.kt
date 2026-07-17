package com.kascorp.webhooknotesender.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "profile_name")
    val profileName: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "bearer_token")
    val bearerToken: String? = null,

    @ColumnInfo(name = "json_payload")
    val jsonPayload: String,

    @ColumnInfo(name = "media_type")
    val mediaType: String,

    @ColumnInfo(name = "status")
    val status: String = QueueStatus.PENDING.name,

    @ColumnInfo(name = "attempts")
    val attempts: Int = 0,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

enum class QueueStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED
}
