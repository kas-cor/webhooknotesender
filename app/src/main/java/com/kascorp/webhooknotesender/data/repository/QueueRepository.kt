package com.kascorp.webhooknotesender.data.repository

import com.kascorp.webhooknotesender.data.local.dao.QueueDao
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRepository @Inject constructor(
    private val queueDao: QueueDao
) {

    fun getAllItems(): Flow<List<QueueItemEntity>> = queueDao.getAllItems()

    suspend fun getItemById(id: Long): QueueItemEntity? = queueDao.getItemById(id)

    suspend fun getPendingItems(): List<QueueItemEntity> =
        queueDao.getItemsByStatus(com.kascorp.webhooknotesender.data.local.entity.QueueStatus.PENDING.name)

    fun getPendingCount(): Flow<Int> = queueDao.getPendingCount()

    suspend fun getPendingCountOnce(): Int = queueDao.getPendingCountOnce()

    suspend fun insert(item: QueueItemEntity): Long = queueDao.insert(item)

    suspend fun update(item: QueueItemEntity) = queueDao.update(item)

    suspend fun delete(item: QueueItemEntity) = queueDao.delete(item)

    suspend fun deleteById(id: Long) = queueDao.deleteById(id)

    suspend fun updateStatus(id: Long, status: String, attempts: Int, lastError: String?) {
        queueDao.updateStatus(id, status, attempts, lastError)
    }

    suspend fun markAsSent(id: Long) = queueDao.markAsSent(id)

    suspend fun deleteSentItems() = queueDao.deleteSentItems()
}
