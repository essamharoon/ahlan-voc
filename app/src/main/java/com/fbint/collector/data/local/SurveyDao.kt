package com.fbint.collector.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fbint.collector.data.local.entity.SurveyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyDao {

    @Query("SELECT * FROM surveys ORDER BY name ASC")
    fun observeAll(): Flow<List<SurveyEntity>>

    @Query("SELECT * FROM surveys WHERE id = :id")
    suspend fun getById(id: String): SurveyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(surveys: List<SurveyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(survey: SurveyEntity)

    @Query("DELETE FROM surveys WHERE id NOT IN (:keepIds)")
    suspend fun pruneExcept(keepIds: List<String>)

    @Query("SELECT COUNT(*) FROM surveys")
    fun count(): Flow<Int>
}
