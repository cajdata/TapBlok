package com.cj.tapblok.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionHistoryDao {
    @Insert
    suspend fun insert(sessionHistory: SessionHistory): Long

    @Update
    suspend fun update(sessionHistory: SessionHistory)

    @Query("SELECT * FROM session_history ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionHistory>>

    @Query("SELECT * FROM session_history WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionHistory?
}