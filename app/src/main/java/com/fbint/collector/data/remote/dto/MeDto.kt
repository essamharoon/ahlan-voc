package com.fbint.collector.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Note: unlike most management endpoints, /me does NOT wrap its response in `{data: ...}`.
 * Verified live against ksa.formbricks.com 2026-05-01 — response is the raw user object.
 * Also: the field that older docs called `product` is now `project` in current Formbricks
 * versions.
 */
@JsonClass(generateAdapter = true)
data class MeDto(
    val id: String,
    val project: ProjectDto? = null,
    val type: String? = null,
    val appSetupCompleted: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class ProjectDto(
    val id: String,
    val name: String,
)
