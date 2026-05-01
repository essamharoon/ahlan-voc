package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fbint.collector.data.remote.dto.QuestionDto

/** Positional `string[]` of length 5 in fixed order: firstName, lastName, email, phone, company. */
@Composable
fun ContactInfoQuestion(
    question: QuestionDto,
    lang: String,
    answer: List<String>?,
    onAnswer: (Any?) -> Unit,
) {
    val current = (answer ?: List(5) { "" }).let {
        if (it.size == 5) it else List(5) { i -> it.getOrNull(i).orEmpty() }
    }
    val configs = listOf(
        question.firstName to "First name",
        question.lastName to "Last name",
        question.email to "Email",
        question.phone to "Phone",
        question.company to "Company",
    )
    configs.forEachIndexed { idx, (cfg, fallback) ->
        if (cfg?.show == true) {
            FieldRow(cfg, fallback, lang, current[idx]) { v ->
                onAnswer(current.toMutableList().also { it[idx] = v })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
