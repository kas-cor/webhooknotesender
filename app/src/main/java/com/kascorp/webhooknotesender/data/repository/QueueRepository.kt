package com.kascorp.webhooknotesender.data.repository

import android.content.Context
import com.kascorp.webhooknotesender.data.local.PayloadFileHelper
import com.kascorp.webhooknotesender.data.local.dao.QueueDao
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRepository @Inject constructor(
    private val queueDao: QueueDao,
    @ApplicationContext private val context: Context
) {

    fun getAllItems(): Flow<List<QueueItemEntity>> = queueDao.getAllItems()

    suspend fun getItemById(id: Long): QueueItemEntity? = queueDao.getItemById(id)

    suspend fun getPendingItems(): List<QueueItemEntity> =
        queueDao.getItemsByStatus(QueueStatus.PENDING.name)

    fun getPendingCount(): Flow<Int> = queueDao.getPendingCount()

    suspend fun getPendingCountOnce(): Int = queueDao.getPendingCountOnce()

    suspend fun insert(item: QueueItemEntity): Long = queueDao.insert(item)

    suspend fun update(item: QueueItemEntity) = queueDao.update(item)

    suspend fun delete(item: QueueItemEntity) {
        if (item.payloadFilePath != null) {
            PayloadFileHelper.deletePayload(context, item.payloadFilePath)
        }
        queueDao.delete(item)
    }

    suspend fun deleteById(id: Long) {
        queueDao.getItemById(id)?.let { item ->
            if (item.payloadFilePath != null) {
                PayloadFileHelper.deletePayload(context, item.payloadFilePath)
            }
        }
        queueDao.deleteById(id)
    }

    suspend fun updateStatus(id: Long, status: String, attempts: Int, lastError: String?) {
        queueDao.updateStatus(id, status, attempts, lastError)
    }

    suspend fun markAsSent(id: Long) = queueDao.markAsSent(id)

    suspend fun deleteSentItems() {
        queueDao.getItemsByStatus(QueueStatus.SENT.name).forEach { item ->
            if (item.payloadFilePath != null) {
                PayloadFileHelper.deletePayload(context, item.payloadFilePath)
            }
        }
        queueDao.deleteSentItems()
    }

    suspend fun deleteSentItemsOlderThan(beforeTimestamp: Long) {
        queueDao.getItemsByStatus(QueueStatus.SENT.name).forEach { item ->
            if (item.createdAt < beforeTimestamp && item.payloadFilePath != null) {
                PayloadFileHelper.deletePayload(context, item.payloadFilePath)
            }
        }
        queueDao.deleteSentItemsOlderThan(beforeTimestamp)
    }

    suspend fun cleanupOrphanedPayloads() {
        val activeFiles = queueDao.getAllPayloadFilePaths()
            .mapNotNull { it }
            .toSet()
        PayloadFileHelper.cleanupOrphanedFiles(context, activeFiles)
    }
}
