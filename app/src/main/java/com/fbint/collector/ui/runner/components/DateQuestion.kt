package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateQuestion(
    question: QuestionDto,
    answer: String?,
    onAnswer: (Any?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = answer?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() }.getOrNull() }
    )
    val display = answer?.let { formatForDisplay(it, question.format) } ?: ""

    OutlinedTextField(
        value = display,
        onValueChange = {},
        readOnly = true,
        label = { Text("Tap to pick a date") },
        modifier = Modifier.fillMaxWidth().clickable { open = true },
    )

    if (open) {
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = state.selectedDateMillis
                        if (millis != null) {
                            val iso = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                            onAnswer(iso)
                        }
                        open = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state, modifier = Modifier.padding(0.dp))
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
