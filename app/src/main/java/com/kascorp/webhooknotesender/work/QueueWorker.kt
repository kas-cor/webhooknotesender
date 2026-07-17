package com.kascorp.webhooknotesender.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.data.remote.WebhookApi
import com.kascorp.webhooknotesender.data.remote.WebhookException
import com.kascorp.webhooknotesender.data.repository.QueueRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class QueueWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
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

        val sendResult = webhookApi.send(
            url = item.url,
            jsonPayload = item.jsonPayload,
            bearerToken = item.bearerToken
        )

        return sendResult.fold(
            onSuccess = {
                // Success - mark as sent and delete
                queueRepository.markAsSent(item.id)
                queueRepository.deleteById(item.id)
                true // success, no retry needed
            },
            onFailure = { error ->
                val shouldRetry = if (error is WebhookException) {
                    error.shouldRetry
                } else {
                    true
                }

                if (shouldRetry) {
                    // Network error or server error - keep as PENDING for retry
                    queueRepository.updateStatus(
                        id = item.id,
                        status = QueueStatus.PENDING.name,
                        attempts = item.attempts + 1,
                        lastError = error.message
                    )
                    false // needs retry
                } else {
                    // Client error (4xx except 408/429) - mark as FAILED, no retry
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
