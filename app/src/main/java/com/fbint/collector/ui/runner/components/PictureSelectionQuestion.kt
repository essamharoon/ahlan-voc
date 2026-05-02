package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fbint.collector.data.remote.dto.QuestionDto

/**
 * Image grid. Stored value is a `string[]` of choice **ids** (length 1 when allowMulti=false).
 * Built as plain rows-of-two rather than a LazyVerticalGrid so it nests safely inside the
 * runner's vertical scroll — nested vertical-scroll containers swallow gestures (the Back
 * button taps don't get through). Images are Coil-cached during survey refresh, so this
 * works fully offline at the venue.
 */
@Composable
fun PictureSelectionQuestion(
    question: QuestionDto,
    answer: List<String>?,
    onAnswer: (Any?) -> Unit,
) {
    val choices = question.choices.orEmpty()
    val multi = question.allowMulti == true
    val selected = answer.orEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        choices.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { choice ->
                    val isSelected = choice.id in selected
                    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(3.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable {
                                val next = when {
                                    multi && isSelected -> selected - choice.id
                                    multi -> selected + choice.id
                                    isSelected -> emptyList()
                                    else -> listOf(choice.id)
                                }
                                onAnswer(next)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = choice.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
