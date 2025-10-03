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

    @Delete
    suspend fun delete(blockedApp: BlockedApp)

    // This version is for the UI, providing a real-time stream
    @Query("SELECT * FROM blocked_apps")
    fun getAllBlockedApps(): Flow<List<BlockedApp>>

    // ADD THIS NEW FUNCTION: This is for the service to get a simple list
    @Query("SELECT * FROM blocked_apps")
    suspend fun getAllBlockedAppsList(): List<BlockedApp>
}