package com.kascorp.webhooknotesender.ui.queue

import android.content.Context
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.data.repository.QueueRepository
import com.kascorp.webhooknotesender.work.QueueWorker
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var queueRepository: QueueRepository
    private lateinit var context: Context
    private lateinit var viewModel: QueueViewModel

    private val testPendingItem = QueueItemEntity(
        id = 1,
        profileName = "Office Camera",
        url = "https://example.com/webhook",
        bearerToken = "test-token",
        jsonPayload = """{"messages":[{"name":"test","data":"base64data"}]}""",
        mediaType = "image",
        status = QueueStatus.PENDING.name,
        createdAt = System.currentTimeMillis()
    )

    private val testFailedItem = QueueItemEntity(
        id = 2,
        profileName = "Failed Cam",
        url = "https://example.com/fail",
        bearerToken = null,
        jsonPayload = "{}",
        mediaType = "image",
        status = QueueStatus.FAILED.name,
        attempts = 3,
        lastError = "HTTP 500",
        createdAt = System.currentTimeMillis()
    )

    private val testSentItem = QueueItemEntity(
        id = 3,
        profileName = "Sent Item",
        url = "https://example.com/sent",
        bearerToken = null,
        jsonPayload = "{}",
        payloadFilePath = "payload_3.json",
        mediaType = "audio",
        status = QueueStatus.SENT.name,
        createdAt = System.currentTimeMillis()
    )

    private val allItems = listOf(testPendingItem, testFailedItem, testSentItem)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        queueRepository = mockk()
        context = mockk(relaxed = true)
        mockkObject(QueueWorker.Companion)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { QueueWorker.enqueue(any()) } just runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(): QueueViewModel {
        coEvery { queueRepository.getAllItems() } returns flowOf(allItems)
        coEvery { queueRepository.getPendingCount() } returns flowOf(1)

        return QueueViewModel(queueRepository, context)
    }

    // ===================== init =====================

    @Test
    fun `init loads queue items from repository`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        val items = viewModel.queueItems.first { it.isNotEmpty() }

        assert(items == allItems) {
            "Expected $allItems but got $items"
        }
        coVerify(exactly = 1) { queueRepository.getAllItems() }
    }

    @Test
    fun `init loads pending count from repository`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        val count = viewModel.pendingCount.first { it > 0 }

        assert(count == 1) {
            "Expected 1 but got $count"
        }
        coVerify(exactly = 1) { queueRepository.getPendingCount() }
    }

    // ===================== deleteItem =====================

    @Test
    fun `deleteItem calls repository delete`() = runTest(testDispatcher) {
        coEvery { queueRepository.delete(testPendingItem) } returns Unit
        viewModel = createViewModel()

        viewModel.deleteItem(testPendingItem)
        advanceUntilIdle()

        coVerify(exactly = 1) { queueRepository.delete(testPendingItem) }
    }

    @Test
    fun `deleteItem handles deleted item with payload file`() = runTest(testDispatcher) {
        coEvery { queueRepository.delete(testSentItem) } returns Unit
        viewModel = createViewModel()

        viewModel.deleteItem(testSentItem)
        advanceUntilIdle()

        coVerify(exactly = 1) { queueRepository.delete(testSentItem) }
    }

    // ===================== deleteItemById =====================

    @Test
    fun `deleteItemById calls repository deleteById`() = runTest(testDispatcher) {
        coEvery { queueRepository.deleteById(1L) } returns Unit
        viewModel = createViewModel()

        viewModel.deleteItemById(1L)
        advanceUntilIdle()

        coVerify(exactly = 1) { queueRepository.deleteById(1L) }
    }

    @Test
    fun `deleteItemById handles non-existent ID`() = runTest(testDispatcher) {
        coEvery { queueRepository.deleteById(999L) } returns Unit
        viewModel = createViewModel()

        viewModel.deleteItemById(999L)
        advanceUntilIdle()

        coVerify(exactly = 1) { queueRepository.deleteById(999L) }
    }

    // ===================== retryItem =====================

    @Test
    fun `retryItem updates status to PENDING and enqueues worker`() = runTest(testDispatcher) {
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        viewModel = createViewModel()

        viewModel.retryItem(testFailedItem)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            queueRepository.updateStatus(
                id = testFailedItem.id,
                status = QueueStatus.PENDING.name,
                attempts = testFailedItem.attempts,
                lastError = null
            )
        }
        verify(exactly = 1) { QueueWorker.enqueue(context) }
    }

    @Test
    fun `retryItem preserves item attempts count`() = runTest(testDispatcher) {
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        viewModel = createViewModel()

        viewModel.retryItem(testFailedItem)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            queueRepository.updateStatus(2L, QueueStatus.PENDING.name, 3, null)
        }
        verify(exactly = 1) { QueueWorker.enqueue(context) }
    }

    // ===================== retryAllFailed =====================

    @Test
    fun `retryAllFailed retries all failed items and enqueues worker`() = runTest(testDispatcher) {
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        viewModel = createViewModel()
        // Force subscription so queueItems.value has data
        viewModel.queueItems.first { it.isNotEmpty() }

        viewModel.retryAllFailed()
        advanceUntilIdle()

        // Only the FAILED item should be retried
        coVerify(exactly = 1) {
            queueRepository.updateStatus(2L, QueueStatus.PENDING.name, 3, null)
        }
        // PENDING and SENT items should NOT be retried
        coVerify(exactly = 0) {
            queueRepository.updateStatus(1L, any(), any(), any())
        }
        coVerify(exactly = 0) {
            queueRepository.updateStatus(3L, any(), any(), any())
        }
        // QueueWorker triggered
        verify(exactly = 1) { QueueWorker.enqueue(context) }
    }

    @Test
    fun `retryAllFailed does nothing when no failed items`() = runTest(testDispatcher) {
        val allSent = listOf(testPendingItem, testSentItem)
        coEvery { queueRepository.getAllItems() } returns flowOf(allSent)
        coEvery { queueRepository.getPendingCount() } returns flowOf(1)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        viewModel = QueueViewModel(queueRepository, context)
        viewModel.queueItems.first { it.isNotEmpty() }

        viewModel.retryAllFailed()
        advanceUntilIdle()

        coVerify(exactly = 0) { queueRepository.updateStatus(any(), any(), any(), any()) }
        verify(exactly = 0) { QueueWorker.enqueue(any()) }
    }

    @Test
    fun `retryAllFailed handles mixed items correctly`() = runTest(testDispatcher) {
        val failed2 = testFailedItem.copy(id = 4, attempts = 1)
        val items = listOf(testPendingItem, testFailedItem, testSentItem, failed2)
        coEvery { queueRepository.getAllItems() } returns flowOf(items)
        coEvery { queueRepository.getPendingCount() } returns flowOf(1)
        coEvery { queueRepository.updateStatus(any(), any(), any(), any()) } returns Unit
        viewModel = QueueViewModel(queueRepository, context)
        viewModel.queueItems.first { it.isNotEmpty() }

        viewModel.retryAllFailed()
        advanceUntilIdle()

        // Both failed items retried
        coVerify(exactly = 1) {
            queueRepository.updateStatus(2L, QueueStatus.PENDING.name, 3, null)
        }
        coVerify(exactly = 1) {
            queueRepository.updateStatus(4L, QueueStatus.PENDING.name, 1, null)
        }
        coVerify(exactly = 2) { queueRepository.updateStatus(any(), any(), any(), any()) }
        // QueueWorker triggered once (not per item)
        verify(exactly = 1) { QueueWorker.enqueue(context) }
    }

    // ===================== clearQueue =====================

    @Test
    fun `clearQueue deletes sent items from repository`() = runTest(testDispatcher) {
        coEvery { queueRepository.deleteSentItems() } returns Unit
        viewModel = createViewModel()

        viewModel.clearQueue()
        advanceUntilIdle()

        coVerify(exactly = 1) { queueRepository.deleteSentItems() }
    }
}
