package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fbint.collector.data.remote.dto.QuestionDto
import com.fbint.collector.domain.localized

/**
 * Up/down reorder list. We avoid drag-and-drop because in the field one-handed thumb taps on
 * arrows are more reliable than drag gestures with gloves / wet hands.
 *
 * Stored value is a `string[]` of localized **labels** in the user's chosen order.
 */
@Composable
fun RankingQuestion(
    question: QuestionDto,
    lang: String,
    answer: List<String>?,
    onAnswer: (Any?) -> Unit,
) {
    val labels = question.choices.orEmpty().map { it.label.localized(lang) }
    val current = answer.takeIf { it != null && it.toSet() == labels.toSet() && it.size == labels.size } ?: labels

    LaunchedEffect(question.id, labels.joinToString()) {
        if (answer == null || answer.toSet() != labels.toSet() || answer.size != labels.size) {
            onAnswer(labels)
        }
    }

    current.forEachIndexed { idx, label ->
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${idx + 1}.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(28.dp),
                )
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    IconButton(
                        onClick = {
                            if (idx == 0) return@IconButton
                            onAnswer(current.toMutableList().apply {
                                this[idx] = this[idx - 1].also { this[idx - 1] = this[idx] }
                            })
                        },
                        enabled = idx > 0,
                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                    IconButton(
                        onClick = {
                            if (idx == current.lastIndex) return@IconButton
                            onAnswer(current.toMutableList().apply {
                                this[idx] = this[idx + 1].also { this[idx + 1] = this[idx] }
                            })
                        },
                        enabled = idx < current.lastIndex,
                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                }
            }
        }
    }
}
