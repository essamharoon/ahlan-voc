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
    private val factory: com.fbint.collector.data.remote.FormbricksApiFactory,
    private val config: ConfigRepository,
    private val files: FileQueueRepository,
    private val surveyRepo: SurveyRepository,
    moshi: Moshi,
) {
    /** Resolved per-call so config updates take effect immediately. */
    private val api: FormbricksClientApi
        get() = factory.client { config.baseUrl() ?: "https://app.formbricks.com" }
    private val mapAdapter: JsonAdapter<Map<String, Any?>> =
        moshi.adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
    private val variableMapAdapter: JsonAdapter<Map<String, Any?>> = mapAdapter
    private val hiddenFieldsAdapter: JsonAdapter<Map<String, Any?>> = mapAdapter

    fun pendingCount(): Flow<Int> = dao.pendingCount()
    fun syncedCount(): Flow<Int> = dao.syncedCount()
    fun strugglingCount(): Flow<Int> = dao.strugglingCount(STRUGGLE_THRESHOLD)
    fun recent(limit: Int = 50): Flow<List<QueuedResponseEntity>> = dao.recent(limit)

    /**
     * Capture a response into the local queue. Auto-stamp candidates ([autoStampCandidates])
     * are filtered against the survey's declared hidden field IDs ([allowedHiddenFieldIds]) —
     * keys not present in that whitelist are silently dropped, so a candidate like
     * `time_to_complete_seconds` is sent only when the admin actually added that hidden field
     * in Formbricks. User-entered hidden fields take precedence over auto-stamps.
     */
    suspend fun enqueue(
        surveyId: String,
        environmentId: String,
        data: Map<String, Any?>,
        finished: Boolean,
        language: String?,
        variables: Map<String, Any?> = emptyMap(),
        hiddenFields: Map<String, Any?> = emptyMap(),
        autoStampCandidates: Map<String, String> = emptyMap(),
        @Suppress("UNUSED_PARAMETER") allowedHiddenFieldIds: Set<String> = emptySet(),
    ): String {
        val uuid = UUID.randomUUID().toString()
        val placeholderIds = files.extractFilePlaceholders(data)
        if (placeholderIds.isNotEmpty()) files.bindFilesToResponse(placeholderIds, uuid)
        // Store auto-stamps UNFILTERED. Sync filters against the survey's current cached
        // fieldIds, so a stale-at-capture cache doesn't permanently drop these values —
        // they survive until the survey definition catches up.
        val nonBlankAutoStamps = autoStampCandidates.filterValues { it.isNotBlank() }
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
                autoStampsJson = mapAdapter.toJson(nonBlankAutoStamps),
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

                // Sanitize the magic "default" language at sync time too — old responses
                // queued before the language fix shipped still carry it and the server 400s.
                val sanitizedLang = sanitizeLanguageForServer(item.language, item.surveyId)
                // Filter auto-stamps against the survey's CURRENT cached fieldIds (may have
                // grown since capture). Then merge into data map — public API ignores the
                // top-level `hiddenFields` field, values must ride in `data` keyed by field
                // name. Question-answer keys are CUIDs, hidden field names are admin-chosen
                // strings, so no collision; explicit answers win over hidden if any clash.
                val autoStamps: Map<String, Any?> = mapAdapter.fromJson(item.autoStampsJson.orEmpty().ifBlank { "{}" }) ?: emptyMap()
                val survey = surveyRepo.loadFromCache(item.surveyId)
                val allowed = survey?.hiddenFields?.fieldIds.orEmpty().toSet()
                val filteredAutoStamps = autoStamps.filterKeys { it in allowed }
                val mergedData = filteredAutoStamps + hidden.filterValues { it != null } + finalData
                val req = CreateResponseRequest(
                    surveyId = item.surveyId,
                    finished = item.finished,
                    data = mergedData,
                    userId = item.surveyorId?.takeIf { it.isNotBlank() },
                    meta = mapOf(
                        "source" to "fbint:${item.clientUuid}",
                        "surveyor" to item.surveyorId.orEmpty(),
                    ),
                    language = sanitizedLang,
                    variables = variables.takeIf { it.isNotEmpty() },
                    hiddenFields = null,
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

    /**
     * The runner's i18n lookup uses `"default"` as a magic key, but Formbricks rejects that
     * as an unknown language code. Resolve it to the survey's real default language code (e.g.
     * `en-GB`) at sync time so old queued responses don't 400 forever.
     */
    private suspend fun sanitizeLanguageForServer(stored: String?, surveyId: String): String? {
        if (stored.isNullOrBlank()) return null
        if (stored != "default") return stored
        val survey = surveyRepo.loadFromCache(surveyId) ?: return null
        return survey.languages.firstOrNull { it.default }?.language?.code
    }

    data class SyncOutcome(val synced: Int, val failed: Int, val retry: Boolean)

    private companion object {
        const val STRUGGLE_THRESHOLD = 3
    }
}
