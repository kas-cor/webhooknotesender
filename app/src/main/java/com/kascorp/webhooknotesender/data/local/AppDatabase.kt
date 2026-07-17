package com.kascorp.webhooknotesender.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kascorp.webhooknotesender.data.local.dao.ProfileDao
import com.kascorp.webhooknotesender.data.local.dao.QueueDao
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity

@Database(
    entities = [
        ProfileEntity::class,
        QueueItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun queueDao(): QueueDao
}
