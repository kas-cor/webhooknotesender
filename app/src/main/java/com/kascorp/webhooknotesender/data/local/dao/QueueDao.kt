package com.kascorp.webhooknotesender.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {

    @Query("SELECT * FROM queue_items ORDER BY created_at DESC")
    fun getAllItems(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items WHERE status = :status ORDER BY created_at ASC")
    suspend fun getItemsByStatus(status: String): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items WHERE id = :id")
    suspend fun getItemById(id: Long): QueueItemEntity?

    @Query("SELECT COUNT(*) FROM queue_items WHERE status = 'PENDING' OR status = 'SENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM queue_items WHERE status = 'PENDING' OR status = 'SENDING'")
    suspend fun getPendingCountOnce(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueueItemEntity): Long

    @Update
    suspend fun update(item: QueueItemEntity)

    @Delete
    suspend fun delete(item: QueueItemEntity)

    @Query("DELETE FROM queue_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE queue_items SET status = :status, attempts = :attempts, last_error = :lastError WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, attempts: Int, lastError: String?)

    @Query("UPDATE queue_items SET status = 'SENT' WHERE id = :id")
    suspend fun markAsSent(id: Long)

    @Query("DELETE FROM queue_items WHERE status = 'SENT'")
    suspend fun deleteSentItems()

    @Query("SELECT payload_file_path FROM queue_items")
    suspend fun getAllPayloadFilePaths(): List<String?>
}
