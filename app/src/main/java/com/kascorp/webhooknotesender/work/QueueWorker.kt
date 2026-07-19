package com.kascorp.webhooknotesender.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kascorp.webhooknotesender.data.local.PayloadFileHelper
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.data.remote.WebhookApi
import com.kascorp.webhooknotesender.data.remote.WebhookException
import com.kascorp.webhooknotesender.data.repository.QueueRepository
import java.util.concurrent.TimeUnit

class QueueWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val queueRepository: QueueRepository,
    private val webhookApi: WebhookApi
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "queue_processing"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<QueueWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        queueRepository.cleanupOrphanedPayloads()

        // Auto-delete sent items older than 24 hours
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        queueRepository.deleteSentItemsOlderThan(dayAgo)

        val pendingItems = queueRepository.getPendingItems()

        if (pendingItems.isEmpty()) {
            return Result.success()
        }

        var hasFailures = false

        for (item in pendingItems) {
            val success = processItem(item)
            if (!success) {
                hasFailures = true
            }
        }

        return if (hasFailures) Result.retry() else Result.success()
    }

    /**
     * Process a single queue item. Returns true if successful, false if needs retry.
     */
    private suspend fun processItem(item: QueueItemEntity): Boolean {
        // Mark as SENDING
        queueRepository.updateStatus(
            id = item.id,
            status = QueueStatus.SENDING.name,
            attempts = item.attempts + 1,
            lastError = null
        )

        // Load JSON payload from file if stored externally
        val jsonPayload = if (item.payloadFilePath != null) {
            PayloadFileHelper.loadPayload(applicationContext, item.payloadFilePath)
                ?: item.jsonPayload
        } else {
            item.jsonPayload
        }

        val sendResult = webhookApi.send(
            url = item.url,
            jsonPayload = jsonPayload,
            bearerToken = item.bearerToken
        )

        return sendResult.fold(
            onSuccess = {
                // Delete payload file to free space, keep DB record as SENT
                if (item.payloadFilePath != null) {
                    PayloadFileHelper.deletePayload(applicationContext, item.payloadFilePath)
                }
                queueRepository.markAsSent(item.id)
                true // success, no retry needed
            },
            onFailure = { error ->
                val shouldRetry = if (error is WebhookException) {
                    error.shouldRetry
                } else {
                    true
                }

                if (shouldRetry) {
                    queueRepository.updateStatus(
                        id = item.id,
                        status = QueueStatus.PENDING.name,
                        attempts = item.attempts + 1,
                        lastError = error.message
                    )
                    false // needs retry
                } else {
                    // Clean up payload file — 4xx errors will never succeed
                    if (item.payloadFilePath != null) {
                        PayloadFileHelper.deletePayload(applicationContext, item.payloadFilePath)
                    }
                    queueRepository.updateStatus(
                        id = item.id,
                        status = QueueStatus.FAILED.name,
                        attempts = item.attempts + 1,
                        lastError = error.message
                    )
                    true // no retry needed for this item (it's failed permanently)
                }
            }
        )
    }
}
