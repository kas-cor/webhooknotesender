package com.kascorp.webhooknotesender.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import java.io.File
import com.kascorp.webhooknotesender.data.local.PayloadFileHelper
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.data.remote.WebhookApi
import com.kascorp.webhooknotesender.data.remote.WebhookException
import com.kascorp.webhooknotesender.data.repository.QueueRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class QueueWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var queueRepository: QueueRepository
    private lateinit var webhookApi: WebhookApi

    private val testPayload = """{"messages":[{"name":"test","data":"base64data"}]}"""

    private val tempDir = File(System.getProperty("java.io.tmpdir"))

    private val testItem = QueueItemEntity(
        id = 1,
        profileName = "Office Camera",
        url = "https://example.com/webhook",
        bearerToken = "test-token",
        jsonPayload = testPayload,
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
        jsonPayload = testPayload,
        mediaType = "audio",
        status = QueueStatus.PENDING.name,
        attempts = 0,
        createdAt = System.currentTimeMillis()
    )

    /**
     * Item with payload saved to a file (payloadFilePath set, jsonPayload empty).
     * This is the format used after the race condition fix (save-to-file-first).
     */
    private val testItemWithFile = QueueItemEntity(
        id = 3,
        profileName = "Door Camera",
        url = "https://example.com/door-webhook",
        bearerToken = "file-token",
        jsonPayload = "",
        payloadFilePath = "payload_uuid.json",
        mediaType = "image",
        status = QueueStatus.PENDING.name,
        attempts = 0,
        createdAt = System.currentTimeMillis()
    )

    /**
     * Item with file but also has fallback jsonPayload (when file load fails).
     */
    private val testItemWithFileAndFallback = QueueItemEntity(
        id = 4,
        profileName = "File Fallback",
        url = "https://example.com/fallback-webhook",
        bearerToken = null,
        jsonPayload = testPayload,
        payloadFilePath = "missing_payload.json",
        mediaType = "audio",
        status = QueueStatus.PENDING.name,
        attempts = 0,
        createdAt = System.currentTimeMillis()
    )

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.cacheDir } returns tempDir
        workerParams = mockk(relaxed = true)
        queueRepository = mockk()
        coEvery { queueRepository.cleanupOrphanedPayloads() } returns Unit
        webhookApi = mockk()
        mockkObject(PayloadFileHelper)
    }

    @After
    fun tearDown() {
        clearAllMocks()
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
        coVerify(exactly = 1) { queueRepository.deleteById(2L) }
    }

    // ===================== PayloadFileHelper integration =====================

    @Test
    fun `doWork loads payload from file when payloadFilePath is set`() = runTest {
        // given — item with payload stored in file
        every { PayloadFileHelper.loadPayload(context, "payload_uuid.json") } returns testPayload
        coEvery { queueRepository.getPendingItems() } returns listOf(testItemWithFile)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery { webhookApi.send(testItemWithFile.url, testPayload, testItemWithFile.bearerToken) } returns
                kotlin.Result.success("OK")
        coEvery { queueRepository.deleteById(3L) } returns Unit

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then — sends payload loaded from file, not from jsonPayload field
        assert(result == ListenableWorker.Result.success()) {
            "Expected Result.success() but got $result"
        }
        // Payload was loaded from file (not from jsonPayload which is empty)
        verify(exactly = 1) { PayloadFileHelper.loadPayload(context, "payload_uuid.json") }
        coVerify(exactly = 1) {
            webhookApi.send(testItemWithFile.url, testPayload, testItemWithFile.bearerToken)
        }
        // File is cleaned up by deleteById internally — just verify deletion
        coVerify(exactly = 1) { queueRepository.deleteById(3L) }
    }

    @Test
    fun `doWork falls back to jsonPayload when PayloadFileHelper returns null`() = runTest {
        // given — file doesn't exist (loadPayload returns null), falls back to jsonPayload
        every { PayloadFileHelper.loadPayload(context, "missing_payload.json") } returns null
        coEvery { queueRepository.getPendingItems() } returns listOf(testItemWithFileAndFallback)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery {
            webhookApi.send(testItemWithFileAndFallback.url, testPayload, null)
        } returns kotlin.Result.success("OK")
        coEvery { queueRepository.deleteById(4L) } returns Unit

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then — uses fallback jsonPayload since file load failed
        assert(result == ListenableWorker.Result.success()) {
            "Expected Result.success() but got $result"
        }
        verify(exactly = 1) { PayloadFileHelper.loadPayload(context, "missing_payload.json") }
        coVerify(exactly = 1) {
            webhookApi.send(testItemWithFileAndFallback.url, testPayload, null)
        }
        // Item is deleted on success (file cleanup handled internally by deleteById)
        coVerify(exactly = 1) { queueRepository.deleteById(4L) }
    }

    @Test
    fun `doWork does not delete payload file on retriable network error`() = runTest {
        // given — item with file fails with network error (should retry)
        every { PayloadFileHelper.loadPayload(context, "payload_uuid.json") } returns testPayload
        coEvery { queueRepository.getPendingItems() } returns listOf(testItemWithFile)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery { webhookApi.send(testItemWithFile.url, testPayload, testItemWithFile.bearerToken) } returns
                kotlin.Result.failure(
                    WebhookException(
                        message = "Timeout",
                        code = -1,
                        shouldRetry = true
                    )
                )

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then — file is NOT deleted on failure (needs retry)
        assert(result == ListenableWorker.Result.retry()) {
            "Expected Result.retry() but got $result"
        }
        verify(exactly = 1) { PayloadFileHelper.loadPayload(context, "payload_uuid.json") }
        verify(exactly = 0) { PayloadFileHelper.deletePayload(context, any()) }
        coVerify(exactly = 1) {
            queueRepository.updateStatus(3L, QueueStatus.PENDING.name, 1, "Timeout")
        }
    }

    @Test
    fun `doWork does not delete payload file on permanent client error`() = runTest {
        // given — item with file fails with 4xx client error (no retry)
        every { PayloadFileHelper.loadPayload(context, "payload_uuid.json") } returns testPayload
        coEvery { queueRepository.getPendingItems() } returns listOf(testItemWithFile)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery { webhookApi.send(testItemWithFile.url, testPayload, testItemWithFile.bearerToken) } returns
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

        // then — file is deleted on permanent failure (4xx will never succeed)
        assert(result == ListenableWorker.Result.success()) {
            "Expected Result.success() (4xx is not retried) but got $result"
        }
        verify(exactly = 1) { PayloadFileHelper.loadPayload(context, "payload_uuid.json") }
        verify(exactly = 1) { PayloadFileHelper.deletePayload(context, "payload_uuid.json") }
        coVerify(exactly = 1) {
            queueRepository.updateStatus(3L, QueueStatus.FAILED.name, 1, "HTTP 400: Bad Request")
        }
    }

    @Test
    fun `doWork loads payload from file for item without bearer token`() = runTest {
        // given — item with file, no bearer token
        every { PayloadFileHelper.loadPayload(context, "missing_payload.json") } returns testPayload
        coEvery { queueRepository.getPendingItems() } returns listOf(testItemWithFileAndFallback)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        coEvery { webhookApi.send(testItemWithFileAndFallback.url, testPayload, null) } returns
                kotlin.Result.success("OK")
        coEvery { queueRepository.deleteById(4L) } returns Unit

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then
        assert(result == ListenableWorker.Result.success()) {
            "Expected Result.success() but got $result"
        }
        verify(exactly = 1) { PayloadFileHelper.loadPayload(context, "missing_payload.json") }
        coVerify(exactly = 1) {
            webhookApi.send(testItemWithFileAndFallback.url, testPayload, null)
        }
        coVerify(exactly = 1) { queueRepository.deleteById(4L) }
    }

    @Test
    fun `doWork loads payload from file for multiple items with mixed file-payload configs`() = runTest {
        // given — one item with direct jsonPayload, one with file
        coEvery { queueRepository.getPendingItems() } returns listOf(testItem, testItemWithFile)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit

        // Item 1 (direct jsonPayload) succeeds
        coEvery { webhookApi.send(testItem.url, testItem.jsonPayload, testItem.bearerToken) } returns
                kotlin.Result.success("OK")
        coEvery { queueRepository.deleteById(1L) } returns Unit

        // Item 2 (file) also succeeds
        every { PayloadFileHelper.loadPayload(context, "payload_uuid.json") } returns testPayload
        coEvery { webhookApi.send(testItemWithFile.url, testPayload, testItemWithFile.bearerToken) } returns
                kotlin.Result.success("OK")
        coEvery { queueRepository.deleteById(3L) } returns Unit

        // when
        val worker = createWorker()
        val result = worker.doWork()

        // then — both succeed
        assert(result == ListenableWorker.Result.success()) {
            "Expected Result.success() but got $result"
        }
        // File loaded for second item
        verify(exactly = 1) { PayloadFileHelper.loadPayload(context, "payload_uuid.json") }
        // Both items sent with correct payloads
        coVerify(exactly = 1) {
            webhookApi.send(testItem.url, testItem.jsonPayload, testItem.bearerToken)
        }
        coVerify(exactly = 1) {
            webhookApi.send(testItemWithFile.url, testPayload, testItemWithFile.bearerToken)
        }
        coVerify(exactly = 1) { queueRepository.deleteById(1L) }
        coVerify(exactly = 1) { queueRepository.deleteById(3L) }
    }
}
