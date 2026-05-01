package com.fbint.collector.domain

import com.fbint.collector.data.remote.dto.QuestionDto
import com.fbint.collector.data.remote.dto.SurveyDto

/** Question-type discriminators emitted by Formbricks v1. */
object QType {
    const val OPEN_TEXT = "openText"
    const val CHOICE_SINGLE = "multipleChoiceSingle"
    const val CHOICE_MULTI = "multipleChoiceMulti"
    const val RATING = "rating"
    const val NPS = "nps"
    const val CTA = "cta"
    const val CONSENT = "consent"
    const val PICTURE_SELECTION = "pictureSelection"
    const val DATE = "date"
    const val FILE_UPLOAD = "fileUpload"
    const val CAL = "cal"
    const val MATRIX = "matrix"
    const val ADDRESS = "address"
    const val CONTACT_INFO = "contactInfo"
    const val RANKING = "ranking"
}

fun SurveyDto.defaultLanguageCode(): String =
    languages.firstOrNull { it.default }?.language?.let { it.alias ?: it.code }
        ?: languages.firstOrNull()?.language?.let { it.alias ?: it.code }
        ?: "default"

fun SurveyDto.enabledLanguageCodes(): List<String> =
    languages.filter { it.enabled }.mapNotNull { it.language?.let { l -> l.alias ?: l.code } }

fun Map<String, String>?.localized(lang: String): String =
    this?.get(lang) ?: this?.get("default") ?: this?.values?.firstOrNull().orEmpty()

/**
 * True if the answer satisfies the question's `required` constraint. Branching logic in
 * Formbricks can dynamically toggle this per session via `requireAnswer` actions, but at
 * v1 we apply only the static `required` flag — `requireAnswer` is unusual in field
 * collection workflows and the safe default is "respect the static schema."
 */
fun QuestionDto.isAnswerValid(answer: Any?): Boolean {
    if (!required) return true
    return when (type) {
        QType.OPEN_TEXT -> (answer as? String)?.isNotBlank() == true
        QType.CHOICE_SINGLE -> (answer as? String)?.isNotBlank() == true
        QType.CHOICE_MULTI -> (answer as? List<*>)?.isNotEmpty() == true
        QType.RATING, QType.NPS -> answer is Number
        QType.CTA -> answer == "clicked"
        QType.CONSENT -> answer == "accepted"
        QType.PICTURE_SELECTION -> (answer as? List<*>)?.isNotEmpty() == true
        QType.DATE -> (answer as? String)?.isNotBlank() == true
        QType.FILE_UPLOAD -> (answer as? List<*>)?.isNotEmpty() == true
        QType.CAL -> answer == "booked"
        QType.MATRIX -> validateMatrix(answer)
        QType.ADDRESS -> validateAddress(answer)
        QType.CONTACT_INFO -> validateContactInfo(answer)
        QType.RANKING -> {
            val list = (answer as? List<*>) ?: return false
            val total = choices?.size ?: 0
            list.size == total && list.all { (it as? String)?.isNotBlank() == true }
        }
        else -> answer != null
    }
}

private fun QuestionDto.validateMatrix(answer: Any?): Boolean {
    val map = (answer as? Map<*, *>) ?: return false
    val rowCount = rows?.size ?: 0
    if (rowCount == 0) return true
    return map.size >= rowCount && map.values.all { (it as? String)?.isNotBlank() == true }
}

/**
 * Address answers are positional `string[]` of length 6. Required sub-fields must be filled;
 * unused slots may be empty strings.
 */
private fun QuestionDto.validateAddress(answer: Any?): Boolean {
    val list = (answer as? List<*>) ?: return false
    val configs = listOf(addressLine1, addressLine2, city, state, zip, country)
    return configs.withIndex().all { (i, cfg) ->
        if (cfg?.show != true || cfg.required != true) true
        else (list.getOrNull(i) as? String)?.isNotBlank() == true
    }
}

private fun QuestionDto.validateContactInfo(answer: Any?): Boolean {
    val list = (answer as? List<*>) ?: return false
    val configs = listOf(firstName, lastName, email, phone, company)
    return configs.withIndex().all { (i, cfg) ->
        if (cfg?.show != true || cfg.required != true) true
        else (list.getOrNull(i) as? String)?.isNotBlank() == true
    }
}

/** Initial values for a freshly opened question. Lets the runner show pre-populated UI. */
fun QuestionDto.initialAnswer(): Any? = when (type) {
    QType.CHOICE_MULTI, QType.PICTURE_SELECTION, QType.RANKING, QType.FILE_UPLOAD -> emptyList<String>()
    QType.MATRIX -> emptyMap<String, String>()
    QType.ADDRESS -> List(6) { "" }
    QType.CONTACT_INFO -> List(5) { "" }
    else -> null
}
