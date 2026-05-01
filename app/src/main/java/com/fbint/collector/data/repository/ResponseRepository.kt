package com.fbint.collector.data.repository

import com.fbint.collector.data.local.ResponseQueueDao
import com.fbint.collector.data.local.entity.QueuedResponseEntity
import com.fbint.collector.data.remote.FormbricksClientApi
import com.fbint.collector.data.remote.dto.CreateResponseRequest
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResponseRepository @Inject constructor(
    private val dao: ResponseQueueDao,
    private val api: FormbricksClientApi,
    private val config: ConfigRepository,
    private val files: FileQueueRepository,
    moshi: Moshi,
) {
    private val mapAdapter: JsonAdapter<Map<String, Any?>> =
        moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
    private val variableMapAdapter: JsonAdapter<Map<String, Any?>> = mapAdapter
    private val hiddenFieldsAdapter: JsonAdapter<Map<String, Any?>> = mapAdapter

    fun pendingCount(): Flow<Int> = dao.pendingCount()
    fun syncedCount(): Flow<Int> = dao.syncedCount()
    fun strugglingCount(): Flow<Int> = dao.strugglingCount(STRUGGLE_THRESHOLD)
    fun recent(limit: Int = 50): Flow<List<QueuedResponseEntity>> = dao.recent(limit)

    /**
     * Capture a response into the local queue. If [data] contains file-upload placeholders
     * (`fbint-file:<uuid>`), the matching file rows are bound to this response so the upload
     * pipeline can resolve them later.
     */
    suspend fun enqueue(
        surveyId: String,
        environmentId: String,
        data: Map<String, Any?>,
        finished: Boolean,
        language: String?,
        variables: Map<String, Any?> = emptyMap(),
        hiddenFields: Map<String, Any?> = emptyMap(),
    ): String {
        val uuid = UUID.randomUUID().toString()
        val placeholderIds = files.extractFilePlaceholders(data)
        if (placeholderIds.isNotEmpty()) files.bindFilesToResponse(placeholderIds, uuid)
        dao.insert(
            QueuedResponseEntity(
                clientUuid = uuid,
                surveyId = surveyId,
                environmentId = environmentId,
                surveyorId = config.surveyorId(),
                finished = finished,
                language = language,
                dataJson = mapAdapter.toJson(data),
                variablesJson = variableMapAdapter.toJson(variables),
                hiddenFieldsJson = hiddenFieldsAdapter.toJson(hiddenFields),
                capturedAt = System.currentTimeMillis(),
            )
        )
        return uuid
    }

    /**
     * Sync everything currently pending. Skips responses whose bound files haven't all
     * uploaded yet (those will succeed on a later run after FileUploadWorker completes).
     */
    suspend fun syncPending(): SyncOutcome {
        val pending = dao.pendingOnce()
        if (pending.isEmpty()) return SyncOutcome(0, 0, false)
        var synced = 0
        var failed = 0
        var retry = false

        for (item in pending) {
            try {
                val rawData: Map<String, Any?> = mapAdapter.fromJson(item.dataJson) ?: emptyMap()
                val placeholderIds = files.extractFilePlaceholders(rawData)
                val resolvedFiles = if (placeholderIds.isEmpty()) emptyMap()
                    else files.getByIds(placeholderIds).associateBy { it.clientUuid }

                if (placeholderIds.any { id -> resolvedFiles[id]?.uploadedFileUrl.isNullOrBlank() }) {
                    // Files not yet uploaded — skip without marking failure so the worker re-runs.
                    retry = true
                    continue
                }

                val finalData = rawData.mapValues { (_, v) -> substitutePlaceholders(v, resolvedFiles) }
                val variables = variableMapAdapter.fromJson(item.variablesJson.orEmpty().ifBlank { "{}" }) ?: emptyMap()
                val hidden = hiddenFieldsAdapter.fromJson(item.hiddenFieldsJson.orEmpty().ifBlank { "{}" }) ?: emptyMap()

                val req = CreateResponseRequest(
                    surveyId = item.surveyId,
                    finished = item.finished,
                    data = finalData,
                    userId = item.surveyorId?.takeIf { it.isNotBlank() },
                    meta = mapOf(
                        "source" to "fbint:${item.clientUuid}",
                        "surveyor" to item.surveyorId.orEmpty(),
                    ),
                    language = item.language,
                    variables = variables.takeIf { it.isNotEmpty() },
                    hiddenFields = hidden.takeIf { it.isNotEmpty() },
                )
                val resp = api.createResponse(item.environmentId, req)
                dao.markSynced(item.clientUuid, System.currentTimeMillis(), resp.data.id)
                synced++
                resolvedFiles.keys.forEach { files.purgeUploadedFile(it) }
            } catch (t: Throwable) {
                failed++
                dao.markFailure(item.clientUuid, (t.message ?: t.javaClass.simpleName).take(500))
                if (!isFatal(t)) retry = true
            }
        }
        return SyncOutcome(synced, failed, retry)
    }

    @Suppress("UNCHECKED_CAST")
    private fun substitutePlaceholders(
        value: Any?,
        files: Map<String, com.fbint.collector.data.local.entity.QueuedFileEntity>,
    ): Any? = when (value) {
        is String -> if (value.startsWith(FILE_PLACEHOLDER_PREFIX))
            files[value.removePrefix(FILE_PLACEHOLDER_PREFIX)]?.uploadedFileUrl ?: value
            else value
        is List<*> -> value.map { substitutePlaceholders(it, files) }
        else -> value
    }

    private fun isFatal(t: Throwable): Boolean {
        val msg = t.message.orEmpty()
        return Regex("HTTP 4[0-9]{2}").containsMatchIn(msg) && !msg.contains("HTTP 429")
    }

    data class SyncOutcome(val synced: Int, val failed: Int, val retry: Boolean)

    private companion object {
        const val STRUGGLE_THRESHOLD = 3
    }
}
