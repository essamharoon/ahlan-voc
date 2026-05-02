package com.fbint.collector.ui.surveylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fbint.collector.data.NetworkMonitor
import com.fbint.collector.data.UpdateChecker
import com.fbint.collector.data.UpdateInfo
import com.fbint.collector.data.local.PerSurveyCount
import com.fbint.collector.data.local.ResponseQueueDao
import com.fbint.collector.data.local.entity.SurveyEntity
import com.fbint.collector.data.repository.ConfigRepository
import com.fbint.collector.data.repository.ResponseRepository
import com.fbint.collector.data.repository.SurveyRepository
import com.fbint.collector.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val online: Boolean = false,
    val perSurveyCounts: Map<String, PerSurveyCount> = emptyMap(),
)

data class UpdateUiState(
    val checking: Boolean = false,
    val info: UpdateInfo? = null,
    val downloading: Boolean = false,
    val downloadProgress: Int = 0,
    val errorMessage: String? = null,
    val message: String? = null,
)

@HiltViewModel
class SurveyListViewModel @Inject constructor(
    private val surveyRepo: SurveyRepository,
    private val responseRepo: ResponseRepository,
    private val sync: SyncScheduler,
    private val config: ConfigRepository,
    private val responseQueueDao: ResponseQueueDao,
    private val updateChecker: UpdateChecker,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _update = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _update.asStateFlow()

    private val refreshState = MutableStateFlow(false to (null as String?))

    val state: StateFlow<SurveyListState> = combine(
        surveyRepo.observeCachedSurveys(),
        responseRepo.pendingCount(),
        responseRepo.syncedCount(),
        responseRepo.strugglingCount(),
        refreshState,
        networkMonitor.observeOnline(),
        responseQueueDao.observePerSurveyCounts(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val surveys = values[0] as List<SurveyEntity>
        val pending = values[1] as Int
        val synced = values[2] as Int
        val struggling = values[3] as Int
        @Suppress("UNCHECKED_CAST")
        val refresh = values[4] as Pair<Boolean, String?>
        val busy = refresh.first
        val err = refresh.second
        val online = values[5] as Boolean
        @Suppress("UNCHECKED_CAST")
        val perSurvey = (values[6] as List<PerSurveyCount>).associateBy { it.surveyId }
        SurveyListState(
            surveys = surveys,
            pendingResponses = pending,
            syncedResponses = synced,
            strugglingResponses = struggling,
            refreshing = busy,
            refreshError = err,
            projectName = config.projectName(),
            surveyorId = config.surveyorId(),
            online = online,
            perSurveyCounts = perSurvey,
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

    fun checkForUpdate() {
        if (_update.value.checking || _update.value.downloading) return
        _update.update { it.copy(checking = true, errorMessage = null, message = null) }
        viewModelScope.launch {
            try {
                val info = updateChecker.check()
                if (info == null) {
                    _update.update { it.copy(checking = false, errorMessage = "Couldn't reach GitHub.") }
                } else if (info.isNewer) {
                    _update.update { it.copy(checking = false, info = info) }
                } else {
                    _update.update { it.copy(checking = false, message = "You're on the latest version (${info.installedVersion}).") }
                }
            } catch (t: Throwable) {
                _update.update { it.copy(checking = false, errorMessage = t.message ?: "Update check failed.") }
            }
        }
    }

    fun dismissUpdate() {
        _update.update { UpdateUiState() }
    }

    fun downloadAndInstall() {
        val info = _update.value.info ?: return
        if (!updateChecker.canInstallPackages()) {
            updateChecker.openInstallSettings()
            _update.update { it.copy(message = "Allow 'install unknown apps' for Ahlan VOC, then tap Update again.") }
            return
        }
        _update.update { it.copy(downloading = true, downloadProgress = 0, errorMessage = null) }
        viewModelScope.launch {
            try {
                val file = updateChecker.download(info.downloadUrl) { pct ->
                    _update.update { it.copy(downloadProgress = pct) }
                }
                if (file == null) {
                    _update.update { it.copy(downloading = false, errorMessage = "Download failed.") }
                    return@launch
                }
                _update.update { it.copy(downloading = false) }
                updateChecker.launchInstaller(file)
            } catch (t: Throwable) {
                _update.update { it.copy(downloading = false, errorMessage = t.message ?: "Update failed.") }
            }
        }
    }

    /**
     * Tap-handler for a survey card: if the survey has any hidden fields the surveyor needs to
     * fill manually (i.e. not in [AUTO_STAMPED_HIDDEN_FIELD_IDS]), route through the "before
     * you start" screen; otherwise jump straight into the runner.
     */
    fun onSurveyTapped(survey: SurveyEntity, nav: androidx.navigation.NavHostController) {
        viewModelScope.launch {
            val full = surveyRepo.loadFromCache(survey.id)
            val manualFields = full?.hiddenFields?.fieldIds.orEmpty()
                .filter { it !in com.fbint.collector.data.repository.AUTO_STAMPED_HIDDEN_FIELD_IDS }
            val needsHidden = full?.hiddenFields?.enabled == true && manualFields.isNotEmpty()
            val route = if (needsHidden) com.fbint.collector.ui.nav.Routes.hiddenFields(survey.id)
                else com.fbint.collector.ui.nav.Routes.runner(survey.id)
            nav.navigate(route)
        }
    }
}
