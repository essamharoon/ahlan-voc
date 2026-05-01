package com.fbint.collector.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached survey definition. The full server JSON is stored verbatim in [json] so the runner
 * can deserialize it without us inventing a parallel schema; the columns above are denormalized
 * for cheap list queries.
 */
@Entity(tableName = "surveys")
data class SurveyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val status: String?,
    val type: String?,
    val environmentId: String,
    val updatedAt: String?,
    val cachedAt: Long,
    val json: String,
)
