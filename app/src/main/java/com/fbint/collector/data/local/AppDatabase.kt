package com.fbint.collector.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fbint.collector.data.local.entity.QueuedFileEntity
import com.fbint.collector.data.local.entity.QueuedResponseEntity
import com.fbint.collector.data.local.entity.SurveyEntity

@Database(
    entities = [SurveyEntity::class, QueuedResponseEntity::class, QueuedFileEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun surveyDao(): SurveyDao
    abstract fun responseQueueDao(): ResponseQueueDao
    abstract fun queuedFileDao(): QueuedFileDao

    companion object {
        // Real migration (not destructive) so devices with offline-queued responses don't
        // lose them on upgrade. v4 adds queued_responses.sendingAt for client-side dedup.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queued_responses ADD COLUMN sendingAt INTEGER DEFAULT NULL")
            }
        }
    }
}
