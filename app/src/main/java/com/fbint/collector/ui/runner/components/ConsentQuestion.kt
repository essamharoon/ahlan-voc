package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fbint.collector.data.remote.dto.QuestionDto
import com.fbint.collector.domain.localized

@Composable
fun ConsentQuestion(
    question: QuestionDto,
    lang: String,
    answer: String?,
    onAnswer: (Any?) -> Unit,
) {
    val accepted = answer == "accepted"
    val label = question.label.localized(lang).ifBlank { "I agree" }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = accepted,
                onValueChange = { onAnswer(if (it) "accepted" else null) },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = accepted, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}
