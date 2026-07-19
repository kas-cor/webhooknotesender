package com.kascorp.webhooknotesender.repository

import android.content.Context
import com.kascorp.webhooknotesender.data.local.PayloadFileHelper
import com.kascorp.webhooknotesender.data.local.dao.QueueDao
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
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
import java.io.File

class QueueRepositoryTest {

    private lateinit var queueDao: QueueDao
    private lateinit var context: Context
    private lateinit var repository: QueueRepository

    private val testItem = QueueItemEntity(
        id = 1,
        profileName = "Office Camera",
        url = "https://example.com/webhook",
        bearerToken = "test-token",
        jsonPayload = """{"messages":[{"name":"test","data":"base64data"}]}""",
        mediaType = "image",
        status = QueueStatus.PENDING.name
    )

    private val testItemWithFile = QueueItemEntity(
        id = 2,
        profileName = "Door Camera",
        url = "https://example.com/door-webhook",
        bearerToken = null,
        jsonPayload = "",
        payloadFilePath = "payload_uuid.json",
        mediaType = "image",
        status = QueueStatus.PENDING.name
    )

    private val testSentItem = QueueItemEntity(
        id = 3,
        profileName = "Sent Item",
        url = "https://example.com/sent-webhook",
        bearerToken = null,
        jsonPayload = "",
        payloadFilePath = "payload_sent.json",
        mediaType = "audio",
        status = QueueStatus.SENT.name
    )

    @Before
    fun setUp() {
        queueDao = mockk()
        context = mockk(relaxed = true)
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir"))
        mockkObject(PayloadFileHelper)
        repository = QueueRepository(queueDao, context)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ===================== delete(item) =====================

    @Test
    fun `delete cleans up payload file when item has payloadFilePath`() = runTest {
        // given
        coEvery { queueDao.delete(testItemWithFile) } returns Unit

        // when
        repository.delete(testItemWithFile)

        // then — file cleaned up BEFORE DB delete
        verify(exactly = 1) { PayloadFileHelper.deletePayload(context, "payload_uuid.json") }
        coVerify(exactly = 1) { queueDao.delete(testItemWithFile) }
    }

    @Test
    fun `delete does not clean up when item has no payloadFilePath`() = runTest {
        // given
        coEvery { queueDao.delete(testItem) } returns Unit

        // when
        repository.delete(testItem)

        // then — no file cleanup for items without payload file
        verify(exactly = 0) { PayloadFileHelper.deletePayload(any(), any()) }
        coVerify(exactly = 1) { queueDao.delete(testItem) }
    }

    @Test
    fun `delete cleans up payload file then calls DAO delete`() = runTest {
        // given — verify ordering: cleanup before delete
        coEvery { queueDao.delete(testItemWithFile) } returns Unit

        // when
        repository.delete(testItemWithFile)

        // then — both called
        verify(exactly = 1) { PayloadFileHelper.deletePayload(context, "payload_uuid.json") }
        coVerify(exactly = 1) { queueDao.delete(testItemWithFile) }
    }

    // ===================== deleteById =====================

    @Test
    fun `deleteById cleans up payload file when item exists`() = runTest {
        // given
        coEvery { queueDao.getItemById(2L) } returns testItemWithFile
        coEvery { queueDao.deleteById(2L) } returns Unit

        // when
        repository.deleteById(2L)

        // then — loaded item, cleaned up file, deleted from DB
        coVerify(exactly = 1) { queueDao.getItemById(2L) }
        verify(exactly = 1) { PayloadFileHelper.deletePayload(context, "payload_uuid.json") }
        coVerify(exactly = 1) { queueDao.deleteById(2L) }
    }

    @Test
    fun `deleteById skips cleanup when item not found`() = runTest {
        // given
        coEvery { queueDao.getItemById(999L) } returns null
        coEvery { queueDao.deleteById(999L) } returns Unit

        // when
        repository.deleteById(999L)

        // then — no file cleanup, just DB delete
        coVerify(exactly = 1) { queueDao.getItemById(999L) }
        verify(exactly = 0) { PayloadFileHelper.deletePayload(any(), any()) }
        coVerify(exactly = 1) { queueDao.deleteById(999L) }
    }

    @Test
    fun `deleteById skips cleanup when item has no payloadFilePath`() = runTest {
        // given — item exists but has no payload file
        coEvery { queueDao.getItemById(1L) } returns testItem
        coEvery { queueDao.deleteById(1L) } returns Unit

        // when
        repository.deleteById(1L)

        // then — no file cleanup
        coVerify(exactly = 1) { queueDao.getItemById(1L) }
        verify(exactly = 0) { PayloadFileHelper.deletePayload(any(), any()) }
        coVerify(exactly = 1) { queueDao.deleteById(1L) }
    }

    // ===================== deleteSentItems =====================

    @Test
    fun `deleteSentItems cleans up payload files for all sent items`() = runTest {
        // given — multiple sent items with payload files
        val sentItem1 = testSentItem.copy(id = 3, payloadFilePath = "file1.json")
        val sentItem2 = testSentItem.copy(id = 4, payloadFilePath = "file2.json")
        val sentItems = listOf(sentItem1, sentItem2)

        coEvery { queueDao.getItemsByStatus(QueueStatus.SENT.name) } returns sentItems
        coEvery { queueDao.deleteSentItems() } returns Unit

        // when
        repository.deleteSentItems()

        // then — each file cleaned up
        coVerify(exactly = 1) { queueDao.getItemsByStatus(QueueStatus.SENT.name) }
        verify(exactly = 1) { PayloadFileHelper.deletePayload(context, "file1.json") }
        verify(exactly = 1) { PayloadFileHelper.deletePayload(context, "file2.json") }
        coVerify(exactly = 1) { queueDao.deleteSentItems() }
    }

    @Test
    fun `deleteSentItems skips items without payloadFilePath`() = runTest {
        // given — one sent item with file, one without
        val sentWithFile = testSentItem.copy(id = 3, payloadFilePath = "file3.json")
        val sentWithoutFile = testSentItem.copy(id = 4, payloadFilePath = null)
        val sentItems = listOf(sentWithFile, sentWithoutFile)

        coEvery { queueDao.getItemsByStatus(QueueStatus.SENT.name) } returns sentItems
        coEvery { queueDao.deleteSentItems() } returns Unit

        // when
        repository.deleteSentItems()

        // then — only the item with payload file gets cleanup
        verify(exactly = 1) { PayloadFileHelper.deletePayload(context, "file3.json") }
        verify(exactly = 0) { PayloadFileHelper.deletePayload(context, "nonexistent.json") }
        coVerify(exactly = 1) { queueDao.deleteSentItems() }
    }

    @Test
    fun `deleteSentItems does nothing when no sent items exist`() = runTest {
        // given — no sent items
        coEvery { queueDao.getItemsByStatus(QueueStatus.SENT.name) } returns emptyList()
        coEvery { queueDao.deleteSentItems() } returns Unit

        // when
        repository.deleteSentItems()

        // then — no file cleanup, just DB delete
        coVerify(exactly = 1) { queueDao.getItemsByStatus(QueueStatus.SENT.name) }
        verify(exactly = 0) { PayloadFileHelper.deletePayload(any(), any()) }
        coVerify(exactly = 1) { queueDao.deleteSentItems() }
    }

    // ===================== existing methods still work =====================

    @Test
    fun `getAllItems returns flow from DAO`() = runTest {
        // verify the method still delegates correctly
        coEvery { queueDao.getItemById(1L) } returns null
        coEvery { queueDao.deleteById(1L) } returns Unit

        repository.deleteById(1L)

        coVerify(exactly = 1) { queueDao.getItemById(1L) }
        coVerify(exactly = 1) { queueDao.deleteById(1L) }
    }
}
