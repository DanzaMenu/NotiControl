package com.danza.noticontrol.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotiControlDao {

    // Sessions
    @Insert
    suspend fun insertSession(session: SilenceSession): Long

    @Update
    suspend fun updateSession(session: SilenceSession)

    @Query("SELECT * FROM silence_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SilenceSession>>

    @Query("SELECT * FROM silence_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SilenceSession?

    @Query("SELECT * FROM silence_sessions WHERE endTime > :now AND endedEarly = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(now: Long): SilenceSession?

    @Delete
    suspend fun deleteSession(session: SilenceSession)

    @Query("DELETE FROM silenced_notifications WHERE sessionId = :sessionId")
    suspend fun deleteNotificationsForSession(sessionId: Long)

    // Notifications
    @Insert
    suspend fun insertNotification(notification: SilencedNotification)

    @Query("SELECT * FROM silenced_notifications WHERE sessionId = :sessionId ORDER BY receivedAt ASC")
    fun getNotificationsForSession(sessionId: Long): Flow<List<SilencedNotification>>

    @Query("SELECT COUNT(*) FROM silenced_notifications WHERE sessionId = :sessionId")
    suspend fun getNotificationCountForSession(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM silenced_notifications WHERE sessionId = :sessionId")
    fun getNotificationCountForSessionFlow(sessionId: Long): Flow<Int>
}
