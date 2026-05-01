package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fbint.collector.data.remote.dto.QuestionDto
import com.fbint.collector.domain.localized

/**
 * Rows × columns grid with one radio per row. Per Formbricks server convention the answer is
 * `Map<rowLabel, columnLabel>` keyed by **localized labels**, not ids.
 */
@Composable
fun MatrixQuestion(
    question: QuestionDto,
    lang: String,
    answer: Map<String, String>?,
    onAnswer: (Any?) -> Unit,
) {
    val rows = question.rows.orEmpty()
    val columns = question.columns.orEmpty()
    val current = answer.orEmpty()
    val cellWidth = 90.dp
    val rowLabelWidth = 160.dp

    Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(rowLabelWidth))
            columns.forEach { col ->
                Box(modifier = Modifier.width(cellWidth), contentAlignment = Alignment.Center) {
                    Text(
                        text = col.label.localized(lang),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        HorizontalDivider()
        rows.forEach { row ->
            val rowLabel = row.label.localized(lang)
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = rowLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.widthIn(max = rowLabelWidth).width(rowLabelWidth),
                )
                columns.forEach { col ->
                    val colLabel = col.label.localized(lang)
                    val selected = current[rowLabel] == colLabel
                    Box(
                        modifier = Modifier.width(cellWidth),
                        contentAlignment = Alignment.Center,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onAnswer(current + (rowLabel to colLabel)) },
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}
