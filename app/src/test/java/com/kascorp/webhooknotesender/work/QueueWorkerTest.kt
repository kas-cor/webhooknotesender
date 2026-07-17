package com.kascorp.webhooknotesender.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.data.remote.WebhookApi
import com.kascorp.webhooknotesender.data.remote.WebhookException
import com.kascorp.webhooknotesender.data.repository.QueueRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class QueueWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var queueRepository: QueueRepository
    private lateinit var webhookApi: WebhookApi

    private val testItem = QueueItemEntity(
        id = 1,
        profileName = "Office Camera",
        url = "https://example.com/webhook",
        bearerToken = "test-token",
        jsonPayload = """{"messages":[{"name":"test","data":"base64data"}]}""",
        mediaType = "image",
        status = QueueStatus.PENDING.name,
        attempts = 0,
        createdAt = System.currentTimeMillis()
    )

    private val testItemNoAuth = QueueItemEntity(
        id = 2,
        profileName = "Audio Note",
        url = "https://example.com/audio-webhook",
        bearerToken = null,
        jsonPayload = """{"messages":[{"name":"audio","data":"base64audio"}]}""",
        mediaType = "audio",
        status = QueueStatus.PENDING.name,
        attempts = 0,
        createdAt = System.currentTimeMillis()
    )

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        queueRepository = mockk()
        webhookApi = mockk()
    }

    /**
     * Creates a QueueWorker with mocked dependencies.
     * Uses the @AssistedInject constructor directly (no Hilt needed in unit tests).
     */
    private fun createWorker(): QueueWorker {
        return QueueWorker(
            appContext = context,
            workerParams = workerParams,
            queueRepository = queueRepository,
            webhookApi = webhookApi
        )
    }

    @Test
    fun `doWork returns success when queue is empty`() = runTest {
        // given
        coEvery { queueRepository.getPendingItems() } returns emptyList()

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then
        assert(result == ListenableWorker.Result.success()) {
            "Expected Result.success() but got $result"
        }
        coVerify(exactly = 1) { queueRepository.getPendingItems() }
        coVerify(exactly = 0) { webhookApi.send(any(), any(), any()) }
    }

    @Test
    fun `doWork returns success when single item sends successfully`() = runTest {
        // given
        coEvery { queueRepository.getPendingItems() } returns listOf(testItem)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery { webhookApi.send(testItem.url, testItem.jsonPayload, testItem.bearerToken) } returns
                kotlin.Result.success("OK")
        coEvery { queueRepository.markAsSent(1L) } returns Unit
        coEvery { queueRepository.deleteById(1L) } returns Unit

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then
        assert(result == ListenableWorker.Result.success()) {
            "Expected Result.success() but got $result"
        }
        coVerify(exactly = 1) { queueRepository.getPendingItems() }
        coVerify(exactly = 1) {
            queueRepository.updateStatus(1L, QueueStatus.SENDING.name, 1, null)
        }
        coVerify(exactly = 1) {
            webhookApi.send(testItem.url, testItem.jsonPayload, testItem.bearerToken)
        }
        coVerify(exactly = 1) { queueRepository.markAsSent(1L) }
        coVerify(exactly = 1) { queueRepository.deleteById(1L) }
    }

    @Test
    fun `doWork marks item as FAILED on 4xx client error and returns success`() = runTest {
        // given — 4xx client error (except 408, 429) => shouldRetry = false
        coEvery { queueRepository.getPendingItems() } returns listOf(testItem)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery { webhookApi.send(testItem.url, testItem.jsonPayload, testItem.bearerToken) } returns
                kotlin.Result.failure(
                    WebhookException(
                        message = "HTTP 400: Bad Request",
                        code = 400,
                        shouldRetry = false
                    )
                )

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then — FAILED items don't need retry, so overall success
        assert(result == ListenableWorker.Result.success()) {
            "Expected Result.success() (4xx is not retried) but got $result"
        }
        coVerify(exactly = 1) {
            queueRepository.updateStatus(
                1L,
                QueueStatus.FAILED.name,
                1,
                "HTTP 400: Bad Request"
            )
        }
    }

    @Test
    fun `doWork returns retry on 5xx server error`() = runTest {
        // given — 5xx server error => shouldRetry = true
        coEvery { queueRepository.getPendingItems() } returns listOf(testItem)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery { webhookApi.send(testItem.url, testItem.jsonPayload, testItem.bearerToken) } returns
                kotlin.Result.failure(
                    WebhookException(
                        message = "HTTP 500: Internal Server Error",
                        code = 500,
                        shouldRetry = true
                    )
                )

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then
        assert(result == ListenableWorker.Result.retry()) {
            "Expected Result.retry() but got $result"
        }
        coVerify(exactly = 1) {
            queueRepository.updateStatus(
                1L,
                QueueStatus.PENDING.name,
                1,
                "HTTP 500: Internal Server Error"
            )
        }
    }

    @Test
    fun `doWork returns retry on network exception`() = runTest {
        // given — network error => shouldRetry = true
        coEvery { queueRepository.getPendingItems() } returns listOf(testItem)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery { webhookApi.send(testItem.url, testItem.jsonPayload, testItem.bearerToken) } returns
                kotlin.Result.failure(
                    WebhookException(
                        message = "No internet connection",
                        code = -1,
                        shouldRetry = true
                    )
                )

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then
        assert(result == ListenableWorker.Result.retry()) {
            "Expected Result.retry() but got $result"
        }
        coVerify(exactly = 1) {
            queueRepository.updateStatus(
                1L,
                QueueStatus.PENDING.name,
                1,
                "No internet connection"
            )
        }
    }

    @Test
    fun `doWork returns retry when items have mixed results`() = runTest {
        // given — one succeeds, one needs retry => overall must retry
        coEvery { queueRepository.getPendingItems() } returns listOf(testItem, testItemNoAuth)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit

        // First item succeeds
        coEvery { webhookApi.send(testItem.url, testItem.jsonPayload, testItem.bearerToken) } returns
                kotlin.Result.success("OK")
        coEvery { queueRepository.markAsSent(1L) } returns Unit
        coEvery { queueRepository.deleteById(1L) } returns Unit

        // Second item fails with server error (should retry)
        coEvery {
            webhookApi.send(
                testItemNoAuth.url,
                testItemNoAuth.jsonPayload,
                testItemNoAuth.bearerToken
            )
        } returns kotlin.Result.failure(
            WebhookException(
                message = "HTTP 503: Service Unavailable",
                code = 503,
                shouldRetry = true
            )
        )

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then — at least one item needs retry => Result.retry()
        assert(result == ListenableWorker.Result.retry()) {
            "Expected Result.retry() but got $result"
        }
        coVerify(exactly = 1) { queueRepository.markAsSent(1L) }
        coVerify(exactly = 1) { queueRepository.deleteById(1L) }
        coVerify(exactly = 1) {
            queueRepository.updateStatus(
                2L,
                QueueStatus.PENDING.name,
                1,
                "HTTP 503: Service Unavailable"
            )
        }
    }

    @Test
    fun `doWork handles non-WebhookException as shouldRetry=true`() = runTest {
        // given — generic exception (not WebhookException) => shouldRetry = true
        coEvery { queueRepository.getPendingItems() } returns listOf(testItem)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery { webhookApi.send(testItem.url, testItem.jsonPayload, testItem.bearerToken) } returns
                kotlin.Result.failure(Exception("Unknown error"))

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then — non-WebhookException defaults to shouldRetry = true
        assert(result == ListenableWorker.Result.retry()) {
            "Expected Result.retry() but got $result"
        }
        coVerify(exactly = 1) {
            queueRepository.updateStatus(1L, QueueStatus.PENDING.name, 1, "Unknown error")
        }
    }

    @Test
    fun `doWork handles 429 rate limit as shouldRetry=true`() = runTest {
        // given — 429 Too Many Requests => shouldRetry = true
        coEvery { queueRepository.getPendingItems() } returns listOf(testItem)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery { webhookApi.send(testItem.url, testItem.jsonPayload, testItem.bearerToken) } returns
                kotlin.Result.failure(
                    WebhookException(
                        message = "HTTP 429: Too Many Requests",
                        code = 429,
                        shouldRetry = true
                    )
                )

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then
        assert(result == ListenableWorker.Result.retry()) {
            "Expected Result.retry() but got $result"
        }
        coVerify(exactly = 1) {
            queueRepository.updateStatus(
                1L,
                QueueStatus.PENDING.name,
                1,
                "HTTP 429: Too Many Requests"
            )
        }
    }

    @Test
    fun `doWork handles item without bearer token`() = runTest {
        // given — item without bearer token
        coEvery { queueRepository.getPendingItems() } returns listOf(testItemNoAuth)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery {
            webhookApi.send(testItemNoAuth.url, testItemNoAuth.jsonPayload, null)
        } returns kotlin.Result.success("OK")
        coEvery { queueRepository.markAsSent(2L) } returns Unit
        coEvery { queueRepository.deleteById(2L) } returns Unit

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then
        assert(result == ListenableWorker.Result.success()) {
            "Expected Result.success() but got $result"
        }
        coVerify(exactly = 1) {
            webhookApi.send(testItemNoAuth.url, testItemNoAuth.jsonPayload, null)
        }
        coVerify(exactly = 1) { queueRepository.markAsSent(2L) }
    }
}
