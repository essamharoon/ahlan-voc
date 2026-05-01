package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fbint.collector.data.remote.dto.QuestionDto
import com.fbint.collector.domain.localized

@Composable
fun NpsQuestion(
    question: QuestionDto,
    lang: String,
    answer: Int?,
    onAnswer: (Any?) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (0..10).forEach { v ->
                FilterChip(
                    selected = answer == v,
                    onClick = { onAnswer(v) },
                    label = { Text(v.toString()) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(question.lowerLabel.localized(lang).ifBlank { "Not at all likely" }, style = MaterialTheme.typography.bodySmall)
            Text(question.upperLabel.localized(lang).ifBlank { "Extremely likely" }, style = MaterialTheme.typography.bodySmall)
        }
    }
}
