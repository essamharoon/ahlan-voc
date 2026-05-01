package com.fbint.collector.ui.surveylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fbint.collector.data.local.entity.SurveyEntity
import com.fbint.collector.data.repository.ConfigRepository
import com.fbint.collector.data.repository.ResponseRepository
import com.fbint.collector.data.repository.SurveyRepository
import com.fbint.collector.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SurveyListState(
    val surveys: List<SurveyEntity> = emptyList(),
    val pendingResponses: Int = 0,
    val syncedResponses: Int = 0,
    val strugglingResponses: Int = 0,
    val refreshing: Boolean = false,
    val refreshError: String? = null,
    val projectName: String? = null,
    val surveyorId: String? = null,
)

@HiltViewModel
class SurveyListViewModel @Inject constructor(
    private val surveyRepo: SurveyRepository,
    private val responseRepo: ResponseRepository,
    private val sync: SyncScheduler,
    private val config: ConfigRepository,
) : ViewModel() {

    private val refreshState = MutableStateFlow(false to (null as String?))

    val state: StateFlow<SurveyListState> = combine(
        surveyRepo.observeCachedSurveys(),
        responseRepo.pendingCount(),
        responseRepo.syncedCount(),
        responseRepo.strugglingCount(),
        refreshState,
    ) { surveys, pending, synced, struggling, (busy, err) ->
        SurveyListState(
            surveys = surveys,
            pendingResponses = pending,
            syncedResponses = synced,
            strugglingResponses = struggling,
            refreshing = busy,
            refreshError = err,
            projectName = config.projectName(),
            surveyorId = config.surveyorId(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SurveyListState())

    init {
        // First-launch refresh, in case the periodic worker hasn't run yet.
        refresh()
    }

    fun refresh() {
        if (refreshState.value.first) return
        refreshState.update { true to null }
        viewModelScope.launch {
            val outcome = surveyRepo.refresh()
            refreshState.update { false to (outcome.exceptionOrNull()?.message) }
        }
    }

    fun syncNow() = sync.requestImmediateSync()

    /** Clears every device-level setting so the splash re-routes to the role picker. */
    fun resetDevice() {
        config.clear()
    }
}
