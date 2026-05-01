package com.fbint.collector.ui.runner.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fbint.collector.data.remote.dto.QuestionDto
import com.fbint.collector.data.repository.FILE_PLACEHOLDER_PREFIX
import com.fbint.collector.ui.runner.FileUploadDelegate
import kotlinx.coroutines.launch

/**
 * File picker / camera capture for `fileUpload` questions. Picked files are copied into app
 * private storage and a placeholder string (`fbint-file:<uuid>`) is added to the answer list.
 * The actual upload happens in [com.fbint.collector.sync.FileUploadWorker]; the response is
 * not POSTed until every placeholder has been resolved to a real fileUrl.
 */
@Composable
fun FileUploadQuestion(
    question: QuestionDto,
    delegate: FileUploadDelegate,
    answer: List<String>?,
    onAnswer: (Any?) -> Unit,
) {
    val ctx = LocalContext.current
    val current = answer.orEmpty()
    val multiAllowed = question.allowMultipleFiles == true
    val scope = rememberCoroutineScope()
    var inFlight by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            inFlight = true
            try {
                val placeholder = delegate.ingestFile(uri, question.id, suggestedNameFromUri(ctx, uri))
                onAnswer(if (multiAllowed) current + placeholder else listOf(placeholder))
            } finally {
                inFlight = false
            }
        }
    }

    val mimeMatcher = mimeMatcherFor(question.allowedFileExtensions)

    Column {
        if (current.isEmpty()) {
            OutlinedButton(
                onClick = { launcher.launch(mimeMatcher) },
                enabled = !inFlight,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (inFlight) "Adding…" else "Choose file") }
        } else {
            current.forEachIndexed { idx, placeholder ->
                FileChip(placeholder = placeholder, onRemove = {
                    onAnswer(current.toMutableList().also { it.removeAt(idx) })
                })
                Spacer(Modifier.height(6.dp))
            }
            if (multiAllowed) {
                OutlinedButton(
                    onClick = { launcher.launch(mimeMatcher) },
                    enabled = !inFlight,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (inFlight) "Adding…" else "Add another file") }
            }
        }
        Spacer(Modifier.height(6.dp))
        question.maxSizeInMB?.let {
            Text("Max size: $it MB")
        }
    }
}

@Composable
private fun FileChip(placeholder: String, onRemove: () -> Unit) {
    val uuid = placeholder.removePrefix(FILE_PLACEHOLDER_PREFIX)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("File queued — $uuid", modifier = Modifier.padding(end = 8.dp))
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }
}

private fun mimeMatcherFor(allowed: List<String>?): String = when {
    allowed.isNullOrEmpty() -> "*/*"
    allowed.all { it in setOf("png", "jpg", "jpeg", "webp", "heic", "ico") } -> "image/*"
    allowed.all { it in setOf("mp4", "mov", "avi", "mkv", "webm") } -> "video/*"
    allowed.all { it in setOf("mp3") } -> "audio/*"
    else -> "*/*"
}

private fun suggestedNameFromUri(ctx: android.content.Context, uri: Uri): String? {
    if (uri.scheme == "file") return uri.lastPathSegment
    val cursor = ctx.contentResolver.query(uri, null, null, null, null) ?: return uri.lastPathSegment
    cursor.use {
        val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIdx >= 0 && it.moveToFirst()) return it.getString(nameIdx)
    }
    return uri.lastPathSegment
}
