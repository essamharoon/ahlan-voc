package com.fbint.collector.ui.surveylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.fbint.collector.ui.nav.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyListScreen(
    nav: NavHostController,
    vm: SurveyListViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.projectName ?: "Surveys")
                        Text(
                            "Surveyor: ${state.surveyorId ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.SYNC_STATUS) }) {
                        Icon(Icons.Filled.CloudSync, contentDescription = "Sync status")
                    }
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            QueueBanner(
                pending = state.pendingResponses,
                synced = state.syncedResponses,
                struggling = state.strugglingResponses,
                onSyncNow = vm::syncNow,
            )
            if (state.refreshing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(strokeWidth = 2.dp) }
            }
            if (state.refreshError != null) {
                Text(
                    "Refresh failed: ${state.refreshError}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.surveys.isEmpty() && !state.refreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No surveys cached yet. Tap refresh while online.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(state.surveys, key = { it.id }) { survey ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { nav.navigate(Routes.runner(survey.id)) },
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(survey.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Status: ${survey.status ?: "—"}  •  Type: ${survey.type ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueBanner(pending: Int, synced: Int, struggling: Int, onSyncNow: () -> Unit) {
    val color = if (struggling > 0) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Queue: $pending pending  •  $synced synced", style = MaterialTheme.typography.bodyMedium)
                if (struggling > 0) {
                    Text("$struggling struggling — tap sync to retry", style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onSyncNow) {
                Icon(Icons.Filled.CloudSync, contentDescription = "Sync now")
            }
        }
    }
}
