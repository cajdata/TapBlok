package com.cj.tapblok.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_history")
data class SessionHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    var endTime: Long?,
    var blockedAppAttempts: Int
)