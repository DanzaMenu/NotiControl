package com.danza.noticontrol.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "silence_sessions")
data class SilenceSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Int,
    val endedEarly: Boolean = false
)
