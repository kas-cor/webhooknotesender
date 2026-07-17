package com.kascorp.webhooknotesender.repository

import com.kascorp.webhooknotesender.data.local.dao.ProfileDao
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import com.kascorp.webhooknotesender.data.repository.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ProfileRepositoryTest {

    private lateinit var profileDao: ProfileDao
    private lateinit var repository: ProfileRepository

    private val testProfile = ProfileEntity(
        id = 1,
        name = "Office Camera",
        type = "image",
        prompt = "Describe what you see",
        url = "https://example.com/webhook",
        bearerToken = "test-token"
    )

    private val testProfile2 = ProfileEntity(
        id = 2,
        name = "Audio Note",
        type = "audio",
        prompt = "Transcribe this audio",
        url = "https://example.com/audio-webhook",
        bearerToken = null
    )

    private val allProfiles = listOf(testProfile, testProfile2)

    @Before
    fun setUp() {
        profileDao = mockk()
        repository = ProfileRepository(profileDao)
    }

    @Test
    fun `getAllProfiles returns flow from DAO`() = runTest {
        // given
        coEvery { profileDao.getAllProfiles() } returns flowOf(allProfiles)

        // when
        val result = repository.getAllProfiles().first()

        // then
        assert(result == allProfiles) { "Expected $allProfiles but got $result" }
        coVerify(exactly = 1) { profileDao.getAllProfiles() }
    }

    @Test
    fun `getProfileById returns profile when exists`() = runTest {
        // given
        coEvery { profileDao.getProfileById(1L) } returns testProfile

        // when
        val result = repository.getProfileById(1L)

        // then
        assert(result == testProfile) { "Expected $testProfile but got $result" }
        coVerify(exactly = 1) { profileDao.getProfileById(1L) }
    }

    @Test
    fun `getProfileById returns null when not exists`() = runTest {
        // given
        coEvery { profileDao.getProfileById(999L) } returns null

        // when
        val result = repository.getProfileById(999L)

        // then
        assert(result == null) { "Expected null but got $result" }
        coVerify(exactly = 1) { profileDao.getProfileById(999L) }
    }

    @Test
    fun `getProfileByIdFlow returns flow from DAO`() = runTest {
        // given
        coEvery { profileDao.getProfileByIdFlow(1L) } returns flowOf(testProfile)

        // when
        val result = repository.getProfileByIdFlow(1L).first()

        // then
        assert(result == testProfile) { "Expected $testProfile but got $result" }
        coVerify(exactly = 1) { profileDao.getProfileByIdFlow(1L) }
    }

    @Test
    fun `isNameTaken returns true when name exists`() = runTest {
        // given
        coEvery { profileDao.countByName("Office Camera") } returns 1

        // when
        val result = repository.isNameTaken("Office Camera")

        // then
        assert(result) { "Expected true but got false" }
        coVerify(exactly = 1) { profileDao.countByName("Office Camera") }
        coVerify(exactly = 0) { profileDao.countByNameExcept(any(), any()) }
    }

    @Test
    fun `isNameTaken returns false when name does not exist`() = runTest {
        // given
        coEvery { profileDao.countByName("Non Existent") } returns 0

        // when
        val result = repository.isNameTaken("Non Existent")

        // then
        assert(!result) { "Expected false but got true" }
        coVerify(exactly = 1) { profileDao.countByName("Non Existent") }
    }

    @Test
    fun `isNameTaken with excludeId checks countByNameExcept`() = runTest {
        // given
        coEvery { profileDao.countByNameExcept("Office Camera", 2L) } returns 0

        // when
        val result = repository.isNameTaken("Office Camera", excludeId = 2L)

        // then
        assert(!result) { "Expected false (name belongs to excluded profile) but got true" }
        coVerify(exactly = 1) { profileDao.countByNameExcept("Office Camera", 2L) }
        coVerify(exactly = 0) { profileDao.countByName(any()) }
    }

    @Test
    fun `isNameTaken with excludeId returns true when another profile has the name`() = runTest {
        // given
        coEvery { profileDao.countByNameExcept("Office Camera", 1L) } returns 1

        // when
        val result = repository.isNameTaken("Office Camera", excludeId = 1L)

        // then
        assert(result) { "Expected true (another profile has this name) but got false" }
        coVerify(exactly = 1) { profileDao.countByNameExcept("Office Camera", 1L) }
    }

    @Test
    fun `insert returns generated ID from DAO`() = runTest {
        // given
        coEvery { profileDao.insert(testProfile) } returns 42L

        // when
        val result = repository.insert(testProfile)

        // then
        assert(result == 42L) { "Expected 42 but got $result" }
        coVerify(exactly = 1) { profileDao.insert(testProfile) }
    }

    @Test
    fun `update calls DAO update`() = runTest {
        // given
        coEvery { profileDao.update(testProfile) } returns Unit

        // when
        repository.update(testProfile)

        // then
        coVerify(exactly = 1) { profileDao.update(testProfile) }
    }

    @Test
    fun `delete calls DAO delete`() = runTest {
        // given
        coEvery { profileDao.delete(testProfile) } returns Unit

        // when
        repository.delete(testProfile)

        // then
        coVerify(exactly = 1) { profileDao.delete(testProfile) }
    }

    @Test
    fun `deleteById calls DAO deleteById`() = runTest {
        // given
        coEvery { profileDao.deleteById(1L) } returns Unit

        // when
        repository.deleteById(1L)

        // then
        coVerify(exactly = 1) { profileDao.deleteById(1L) }
    }
}
