package com.cj.tapblok.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedAppDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(blockedApp: BlockedApp)

    // --- START OF CHANGES ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(blockedApps: List<BlockedApp>)

    @Query("DELETE FROM blocked_apps")
    suspend fun deleteAll()
    // --- END OF CHANGES ---

    @Delete
    suspend fun delete(blockedApp: BlockedApp)

    @Query("SELECT * FROM blocked_apps")
    fun getAllBlockedApps(): Flow<List<BlockedApp>>

    @Query("SELECT * FROM blocked_apps")
    suspend fun getAllBlockedAppsList(): List<BlockedApp>
}
