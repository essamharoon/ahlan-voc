package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fbint.collector.data.remote.dto.QuestionDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tappable card that opens the Material 3 date picker. We avoid OutlinedTextField with a
 * clickable wrapper — read-only text fields still consume touch events, so the picker never
 * opened. OutlinedCard.onClick is the clean Compose pattern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateQuestion(
    question: QuestionDto,
    answer: String?,
    onAnswer: (Any?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val display = answer?.takeIf { it.isNotBlank() }?.let { formatForDisplay(it, question.format) }
        ?: "Tap to pick a date"

    OutlinedCard(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = display,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    if (open) {
        val initialMillis = answer
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.atStartOfDay(ZoneId.of("UTC"))
            ?.toInstant()
            ?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val iso = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                            .toString()
                        onAnswer(iso)
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private fun formatForDisplay(iso: String, format: String?): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val pattern = when (format) {
        "M-d-y" -> "MM-dd-yyyy"
        "d-M-y" -> "dd-MM-yyyy"
        "y-M-d" -> "yyyy-MM-dd"
        else -> "yyyy-MM-dd"
    }
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
