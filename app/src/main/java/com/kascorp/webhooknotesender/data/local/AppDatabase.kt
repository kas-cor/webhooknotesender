package com.kascorp.webhooknotesender.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kascorp.webhooknotesender.data.local.dao.ProfileDao
import com.kascorp.webhooknotesender.data.local.dao.QueueDao
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity

@Database(
    entities = [
        ProfileEntity::class,
        QueueItemEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun queueDao(): QueueDao

    companion object {
        val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL("ALTER TABLE queue_items ADD COLUMN payload_file_path TEXT DEFAULT NULL")
        }

        val MIGRATION_2_3 = Migration(2, 3) { db ->
            // Clear oversized json_payload values that cause SQLiteBlobTooBigException
            // on devices with CursorWindow limits (~2 MB). New items store payload
            // in separate files via PayloadFileHelper instead.
            db.execSQL("UPDATE queue_items SET json_payload = '' WHERE LENGTH(json_payload) > 100000")
            // Also remove stale payload_file_path references to non-existent files
            db.execSQL("DELETE FROM queue_items WHERE status = 'SENT'")
        }

        val MIGRATION_3_4 = Migration(3, 4) { db ->
            db.execSQL("ALTER TABLE profiles ADD COLUMN compress_enabled INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE profiles ADD COLUMN compression_quality INTEGER NOT NULL DEFAULT 70")
        }

        val MIGRATION_4_5 = Migration(4, 5) { db ->
            db.execSQL("ALTER TABLE profiles ADD COLUMN use_count INTEGER NOT NULL DEFAULT 0")
        }
    }
}
