package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.fbint.collector.data.remote.dto.QuestionDto
import com.fbint.collector.domain.localized

@Composable
fun OpenTextQuestion(
    question: QuestionDto,
    lang: String,
    answer: String?,
    onAnswer: (Any?) -> Unit,
) {
    val keyboardType = when (question.inputType) {
        "number" -> KeyboardType.Number
        "email" -> KeyboardType.Email
        "url" -> KeyboardType.Uri
        "phone" -> KeyboardType.Phone
        else -> KeyboardType.Text
    }
    val maxChars = question.charLimit?.takeIf { it.enabled }?.max
    val supporting = if (maxChars != null) "${(answer?.length ?: 0)}/$maxChars" else null
    OutlinedTextField(
        value = answer.orEmpty(),
        onValueChange = { v ->
            val capped = if (maxChars != null && v.length > maxChars) v.substring(0, maxChars) else v
            onAnswer(capped)
        },
        label = { Text(question.placeholder.localized(lang).ifBlank { "Type here" }) },
        singleLine = question.longAnswer != true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = supporting?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}
