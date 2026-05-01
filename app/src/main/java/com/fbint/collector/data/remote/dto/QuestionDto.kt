package com.fbint.collector.data.remote.dto

import com.squareup.moshi.JsonClass

/**
 * Loose v1 schema covering every question type Formbricks emits today. Type-specific fields
 * are nullable; the runner switches on [type]. Source of truth: Formbricks
 * `packages/types/surveys/types.ts` on `main` (verified 2026-04-30).
 *
 * v1 type discriminators: openText, multipleChoiceSingle, multipleChoiceMulti, nps, cta,
 * rating, consent, pictureSelection, date, fileUpload, cal, matrix, address, contactInfo,
 * ranking.
 */
@JsonClass(generateAdapter = true)
data class QuestionDto(
    val id: String,
    val type: String,
    val headline: Map<String, String>? = null,
    val subheader: Map<String, String>? = null,
    val required: Boolean = true,
    val buttonLabel: Map<String, String>? = null,
    val backButtonLabel: Map<String, String>? = null,
    val imageUrl: String? = null,
    val videoUrl: String? = null,

    // openText
    val placeholder: Map<String, String>? = null,
    val inputType: String? = null,
    val longAnswer: Boolean? = null,
    val charLimit: CharLimitDto? = null,

    // choice / ranking
    val choices: List<ChoiceDto>? = null,
    val shuffleOption: String? = null,
    val otherOptionPlaceholder: Map<String, String>? = null,

    // rating / nps
    val scale: String? = null,
    val range: Int? = null,
    val lowerLabel: Map<String, String>? = null,
    val upperLabel: Map<String, String>? = null,
    val isColorCodingEnabled: Boolean? = null,

    // cta / consent
    val html: Map<String, String>? = null,
    val label: Map<String, String>? = null,
    val dismissButtonLabel: Map<String, String>? = null,
    val buttonUrl: String? = null,
    val buttonExternal: Boolean? = null,

    // date
    val format: String? = null,

    // pictureSelection
    val allowMulti: Boolean? = null,

    // fileUpload
    val allowMultipleFiles: Boolean? = null,
    val maxSizeInMB: Int? = null,
    val allowedFileExtensions: List<String>? = null,

    // cal
    val calUserName: String? = null,
    val calHost: String? = null,

    // matrix
    val rows: List<MatrixRowDto>? = null,
    val columns: List<MatrixColumnDto>? = null,

    // address (six fields, fixed order: addressLine1, addressLine2, city, state, zip, country)
    val addressLine1: ToggleInputDto? = null,
    val addressLine2: ToggleInputDto? = null,
    val city: ToggleInputDto? = null,
    val state: ToggleInputDto? = null,
    val zip: ToggleInputDto? = null,
    val country: ToggleInputDto? = null,

    // contactInfo (five fields, fixed order: firstName, lastName, email, phone, company)
    val firstName: ToggleInputDto? = null,
    val lastName: ToggleInputDto? = null,
    val email: ToggleInputDto? = null,
    val phone: ToggleInputDto? = null,
    val company: ToggleInputDto? = null,

    // logic
    val logic: List<LogicRuleDto>? = null,
    val logicFallback: String? = null,
)

@JsonClass(generateAdapter = true)
data class ChoiceDto(
    val id: String,
    val label: Map<String, String>? = null,
    val imageUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class CharLimitDto(
    val enabled: Boolean = false,
    val min: Int? = null,
    val max: Int? = null,
)

@JsonClass(generateAdapter = true)
data class MatrixRowDto(val id: String, val label: Map<String, String>? = null)

@JsonClass(generateAdapter = true)
data class MatrixColumnDto(val id: String, val label: Map<String, String>? = null)

@JsonClass(generateAdapter = true)
data class ToggleInputDto(
    val show: Boolean = true,
    val required: Boolean = false,
    val placeholder: Map<String, String>? = null,
)
