package com.fbint.collector.data.repository

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.fbint.collector.data.local.SurveyDao
import com.fbint.collector.data.local.entity.SurveyEntity
import com.fbint.collector.data.remote.FormbricksManagementApi
import com.fbint.collector.data.remote.dto.SurveyDto
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SurveyRepository @Inject constructor(
    private val factory: com.fbint.collector.data.remote.FormbricksApiFactory,
    private val dao: SurveyDao,
    private val config: ConfigRepository,
    @ApplicationContext private val ctx: Context,
    moshi: Moshi,
) {
    private val surveyAdapter: JsonAdapter<SurveyDto> = moshi.adapter(SurveyDto::class.java)
    /**
     * Resolved per-call so a config update (admin entered a new base URL) takes effect on the
     * next API call without restarting the app. Factory caches Retrofit per URL so this is cheap.
     */
    private val api: FormbricksManagementApi
        get() = factory.management { config.baseUrl() ?: "https://app.formbricks.com" }

    fun observeCachedSurveys(): Flow<List<SurveyEntity>> = dao.observeAll()

    fun cachedCount(): Flow<Int> = dao.count()

    /**
     * Refreshes the local cache: lists all surveys in the configured environment, pulls the
     * full detail (questions, etc.) for each, persists the JSON, and pre-warms the image
     * cache so picture-selection / welcome-card images render offline at the venue.
     */
    suspend fun refresh(): Result<Int> = runCatching {
        val key = requireNotNull(config.apiKey()) { "API key not configured" }
        val envId = requireNotNull(config.environmentId()) { "Environment ID not configured" }
        // Skip drafts — surveyors should only see surveys the admin has actually launched.
        val list = api.listSurveys(key).data
            .filter { it.environmentId == envId }
            .filter { it.status?.equals("draft", ignoreCase = true) != true }

        val detailed = list.map { summary ->
            val full = if (summary.questions.isEmpty()) {
                runCatching { api.getSurvey(key, summary.id).data }.getOrDefault(summary)
            } else summary
            full
        }
        dao.upsert(detailed.map { it.toEntity() })
        dao.pruneExcept(detailed.map { it.id })
        prewarmImages(detailed)
        detailed.size
    }

    suspend fun loadFromCache(id: String): SurveyDto? {
        val entity = dao.getById(id) ?: return null
        return surveyAdapter.fromJson(entity.json)
    }

    /**
     * Fire image fetches into Coil's disk cache. We don't await results — failures here are
     * non-fatal (the runner will still render the URL, just possibly with a load error icon
     * if offline at use time).
     */
    private fun prewarmImages(surveys: List<SurveyDto>) {
        val loader: ImageLoader = SingletonImageLoader.get(ctx)
        val urls = surveys.flatMap { s ->
            buildList {
                s.welcomeCard?.fileUrl?.let { add(it) }
                s.endings.forEach { it.imageUrl?.let { url -> add(url) } }
                s.questions.forEach { q ->
                    q.imageUrl?.let { add(it) }
                    q.choices?.forEach { c -> c.imageUrl?.let { add(it) } }
                }
            }
        }.filter { it.isNotBlank() }.distinct()

        urls.forEach { url ->
            loader.enqueue(ImageRequest.Builder(ctx).data(url).build())
        }
    }

    private fun SurveyDto.toEntity(): SurveyEntity = SurveyEntity(
        id = id,
        name = name,
        status = status,
        type = type,
        environmentId = environmentId,
        updatedAt = updatedAt,
        cachedAt = System.currentTimeMillis(),
        json = surveyAdapter.toJson(this),
    )
}
