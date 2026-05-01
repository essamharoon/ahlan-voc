package com.fbint.collector.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fbint.collector.data.local.entity.QueuedFileEntity
import com.fbint.collector.data.local.entity.QueuedResponseEntity
import com.fbint.collector.data.local.entity.SurveyEntity

@Database(
    entities = [SurveyEntity::class, QueuedResponseEntity::class, QueuedFileEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun surveyDao(): SurveyDao
    abstract fun responseQueueDao(): ResponseQueueDao
    abstract fun queuedFileDao(): QueuedFileDao
}
