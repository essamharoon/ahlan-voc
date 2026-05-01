package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fbint.collector.data.remote.dto.QuestionDto

/**
 * Cal.com scheduling. The web survey embeds a Cal.com iframe that posts back "booked" once
 * the user picks a slot. In an offline stadium runner we can't load the iframe, so we surface
 * the cal username (and host) for the surveyor to read out, then let them mark the booking
 * as completed manually. The stored value matches the server's expected `"booked"` literal.
 */
@Composable
fun CalQuestion(
    question: QuestionDto,
    answer: String?,
    onAnswer: (Any?) -> Unit,
) {
    val booked = answer == "booked"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Cal.com booking — offline mode",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val handle = listOfNotNull(
                question.calHost?.takeIf { it.isNotBlank() },
                question.calUserName,
            ).joinToString("/")
            if (handle.isNotBlank()) {
                Text("Direct the respondent to: cal.com/$handle", modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            if (booked) {
                OutlinedButton(
                    onClick = { onAnswer(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("✓ Marked as booked — tap to undo") }
            } else {
                Button(
                    onClick = { onAnswer("booked") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Mark as booked") }
            }
        }
    }
}
