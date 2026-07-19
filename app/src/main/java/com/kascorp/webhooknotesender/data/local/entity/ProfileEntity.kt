package com.kascorp.webhooknotesender.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profiles",
    indices = [Index(value = ["name"], unique = true)]
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: String, // "image", "audio", "video"

    @ColumnInfo(name = "prompt")
    val prompt: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "bearer_token")
    val bearerToken: String? = null,

    @ColumnInfo(name = "compress_enabled")
    val compressEnabled: Boolean = true,

    @ColumnInfo(name = "compression_quality")
    val compressionQuality: Int = 70
)
