package com.fbint.collector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fbint.collector.data.local.entity.QueuedResponseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResponseQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueuedResponseEntity)

    @Query("SELECT * FROM queued_responses WHERE syncedAt IS NULL ORDER BY capturedAt ASC")
    suspend fun pendingOnce(): List<QueuedResponseEntity>

    @Query("UPDATE queued_responses SET attempts = attempts + 1, lastError = :error WHERE clientUuid = :id")
    suspend fun markFailure(id: String, error: String)

    @Query("UPDATE queued_responses SET syncedAt = :ts, serverResponseId = :serverId, lastError = NULL WHERE clientUuid = :id")
    suspend fun markSynced(id: String, ts: Long, serverId: String)

    @Query("SELECT COUNT(*) FROM queued_responses WHERE syncedAt IS NULL")
    fun pendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM queued_responses WHERE syncedAt IS NOT NULL")
    fun syncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM queued_responses WHERE syncedAt IS NULL AND attempts >= :threshold")
    fun strugglingCount(threshold: Int): Flow<Int>

    @Query("SELECT * FROM queued_responses ORDER BY capturedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<QueuedResponseEntity>>

    @Query("SELECT surveyId, COUNT(*) AS total, SUM(CASE WHEN syncedAt IS NULL THEN 1 ELSE 0 END) AS pending FROM queued_responses GROUP BY surveyId")
    fun observePerSurveyCounts(): Flow<List<PerSurveyCount>>

    @Query("SELECT COUNT(*) FROM queued_responses WHERE surveyorId = :surveyorId AND capturedAt >= :sinceMs")
    suspend fun countSince(surveyorId: String, sinceMs: Long): Int
}

data class PerSurveyCount(val surveyId: String, val total: Int, val pending: Int)
