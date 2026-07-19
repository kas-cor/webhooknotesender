package com.kascorp.webhooknotesender.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun getProfileByIdFlow(id: Long): Flow<ProfileEntity?>

    @Query("SELECT COUNT(*) FROM profiles WHERE name = :name AND id != :excludeId")
    suspend fun countByNameExcept(name: String, excludeId: Long): Int

    @Query("SELECT COUNT(*) FROM profiles WHERE name = :name")
    suspend fun countByName(name: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM profiles ORDER BY use_count DESC LIMIT :limit")
    fun getTopProfiles(limit: Int): Flow<List<ProfileEntity>>

    @Query("UPDATE profiles SET use_count = use_count + 1 WHERE id = :id")
    suspend fun incrementUseCount(id: Long)
}
