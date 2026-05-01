package com.fbint.collector.data.remote

import com.squareup.moshi.Moshi
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Builds Retrofit instances whose base URL is resolved at call time. The base URL is
 * configured per device (admin enters it during setup), so we wrap it in a lambda and rebuild
 * the [Retrofit] only when the URL changes.
 */
class FormbricksApiFactory(
    private val client: OkHttpClient,
    private val moshi: Moshi,
) {
    private var managementCacheUrl: String? = null
    private var managementCache: FormbricksManagementApi? = null
    private var clientCacheUrl: String? = null
    private var clientCache: FormbricksClientApi? = null

    @Synchronized
    fun management(baseUrlProvider: () -> String): FormbricksManagementApi {
        val url = baseUrlProvider().normalizeBaseUrl()
        if (managementCacheUrl != url || managementCache == null) {
            managementCacheUrl = url
            managementCache = build(url).create(FormbricksManagementApi::class.java)
        }
        return managementCache!!
    }

    @Synchronized
    fun client(baseUrlProvider: () -> String): FormbricksClientApi {
        val url = baseUrlProvider().normalizeBaseUrl()
        if (clientCacheUrl != url || clientCache == null) {
            clientCacheUrl = url
            clientCache = build(url).create(FormbricksClientApi::class.java)
        }
        return clientCache!!
    }

    private fun build(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.toHttpUrl())
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private fun String.normalizeBaseUrl(): String = if (endsWith("/")) this else "$this/"
}
