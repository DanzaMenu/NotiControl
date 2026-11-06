package com.danza.noticontrol.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "silenced_notifications")
data class SilencedNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val appName: String,
    val appPackage: String,
    val title: String,
    val text: String,
    val receivedAt: Long
)
