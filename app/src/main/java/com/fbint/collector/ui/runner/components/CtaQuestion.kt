package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fbint.collector.data.remote.dto.QuestionDto
import com.fbint.collector.domain.localized

@Composable
fun CtaQuestion(
    question: QuestionDto,
    lang: String,
    answer: String?,
    onAnswer: (Any?) -> Unit,
) {
    val primary = question.buttonLabel.localized(lang).ifBlank { "Continue" }
    val dismiss = question.dismissButtonLabel.localized(lang)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!question.required && dismiss.isNotBlank()) {
            OutlinedButton(
                onClick = { onAnswer("dismissed") },
                modifier = Modifier.weight(1f),
            ) { Text(dismiss) }
        }
        Button(
            onClick = { onAnswer("clicked") },
            modifier = Modifier.weight(1f),
        ) { Text(if (answer == "clicked") "✓ $primary" else primary) }
    }
}
