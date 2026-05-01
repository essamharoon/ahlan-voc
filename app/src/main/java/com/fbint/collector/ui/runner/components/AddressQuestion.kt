package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fbint.collector.data.remote.dto.QuestionDto
import com.fbint.collector.data.remote.dto.ToggleInputDto
import com.fbint.collector.domain.localized

/** Positional `string[]` of length 6 in fixed order: addressLine1..country. */
@Composable
fun AddressQuestion(
    question: QuestionDto,
    lang: String,
    answer: List<String>?,
    onAnswer: (Any?) -> Unit,
) {
    val current = (answer ?: List(6) { "" }).let {
        if (it.size == 6) it else List(6) { i -> it.getOrNull(i).orEmpty() }
    }
    val configs = listOf(
        question.addressLine1 to "Address line 1",
        question.addressLine2 to "Address line 2",
        question.city to "City",
        question.state to "State",
        question.zip to "ZIP / Postal code",
        question.country to "Country",
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

@Composable
internal fun FieldRow(
    cfg: ToggleInputDto,
    fallbackLabel: String,
    lang: String,
    value: String,
    onChange: (String) -> Unit,
) {
    val placeholder = cfg.placeholder.localized(lang).ifBlank { fallbackLabel }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(placeholder + if (cfg.required) " *" else "") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
