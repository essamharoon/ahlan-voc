package com.fbint.collector.ui.runner.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
 * Images are loaded via Coil; the survey-refresh worker pre-warms Coil's disk cache so this
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().height(420.dp),
    ) {
        items(choices, key = { it.id }) { choice ->
            val isSelected = choice.id in selected
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            Box(
                modifier = Modifier
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
    }
}
