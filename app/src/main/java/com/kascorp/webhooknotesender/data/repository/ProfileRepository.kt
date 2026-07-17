package com.kascorp.webhooknotesender.data.repository

import com.kascorp.webhooknotesender.data.local.dao.ProfileDao
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao
) {

    fun getAllProfiles(): Flow<List<ProfileEntity>> = profileDao.getAllProfiles()

    suspend fun getProfileById(id: Long): ProfileEntity? = profileDao.getProfileById(id)

    fun getProfileByIdFlow(id: Long): Flow<ProfileEntity?> = profileDao.getProfileByIdFlow(id)

    suspend fun isNameTaken(name: String, excludeId: Long? = null): Boolean {
        return if (excludeId != null) {
            profileDao.countByNameExcept(name, excludeId) > 0
        } else {
            profileDao.countByName(name) > 0
        }
    }

    suspend fun insert(profile: ProfileEntity): Long = profileDao.insert(profile)

    suspend fun update(profile: ProfileEntity) = profileDao.update(profile)

    suspend fun delete(profile: ProfileEntity) = profileDao.delete(profile)

    suspend fun deleteById(id: Long) = profileDao.deleteById(id)
}
