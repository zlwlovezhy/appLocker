package com.applocker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.applocker.data.entity.LockedApp
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedAppDao {

    @Query("SELECT * FROM locked_apps")
    fun getAllLockedApps(): Flow<List<LockedApp>>

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): LockedApp?

    @Query("SELECT * FROM locked_apps WHERE lockEndTimeMillis > :currentTime")
    suspend fun getActiveLockedApps(currentTime: Long = System.currentTimeMillis()): List<LockedApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lockedApp: LockedApp)

    @Query("DELETE FROM locked_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("DELETE FROM locked_apps WHERE lockEndTimeMillis <= :currentTime")
    suspend fun deleteExpiredLocks(currentTime: Long = System.currentTimeMillis())

    @Query("UPDATE locked_apps SET lockEndTimeMillis = :lockEndTimeMillis WHERE packageName = :packageName")
    suspend fun updateLockEndTime(packageName: String, lockEndTimeMillis: Long)
}
