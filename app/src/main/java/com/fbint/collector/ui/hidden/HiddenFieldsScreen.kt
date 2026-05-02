package com.fbint.collector.ui.hidden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.fbint.collector.data.repository.ConfigRepository
import com.fbint.collector.data.repository.SurveyRepository
import com.fbint.collector.ui.nav.Routes
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HiddenFieldsUiState(
    val loading: Boolean = true,
    val surveyName: String = "",
    val fields: List<String> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel(assistedFactory = HiddenFieldsViewModel.Factory::class)
class HiddenFieldsViewModel @AssistedInject constructor(
    @Assisted private val surveyId: String,
    private val surveyRepo: SurveyRepository,
    private val config: ConfigRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HiddenFieldsUiState())
    val state: StateFlow<HiddenFieldsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val survey = surveyRepo.loadFromCache(surveyId)
            if (survey == null) {
                _state.update { it.copy(loading = false, error = "Survey not in cache.") }
                return@launch
            }
            val fieldIds = survey.hiddenFields?.fieldIds.orEmpty()
            val pre = config.loadHiddenFields(surveyId, fieldIds)
            _state.update {
                it.copy(loading = false, surveyName = survey.name, fields = fieldIds, values = pre)
            }
        }
    }

    fun setValue(fieldId: String, value: String) {
        _state.update { it.copy(values = it.values + (fieldId to value)) }
    }

    /** All fields are required to start the survey. Returns true if every field has a value. */
    fun saveAndProceed(onProceed: () -> Unit) {
        val s = _state.value
        if (s.fields.any { (s.values[it].orEmpty()).isBlank() }) {
            _state.update { it.copy(error = "All fields are required.") }
            return
        }
        config.saveHiddenFields(surveyId, s.values)
        onProceed()
    }

    @AssistedFactory
    interface Factory { fun create(surveyId: String): HiddenFieldsViewModel }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenFieldsScreen(nav: NavHostController, surveyId: String) {
    val vm: HiddenFieldsViewModel = hiltViewModel<HiddenFieldsViewModel, HiddenFieldsViewModel.Factory>(
        creationCallback = { it.create(surveyId) },
    )
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Before you start") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            if (state.loading) {
                CircularProgressIndicator()
                return@Column
            }
            Text(state.surveyName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Fill these context fields once for this respondent — they're attached to every response you collect for this survey.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))

            state.fields.forEach { fieldId ->
                OutlinedTextField(
                    value = state.values[fieldId].orEmpty(),
                    onValueChange = { vm.setValue(fieldId, it) },
                    label = { Text("$fieldId *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    vm.saveAndProceed {
                        nav.navigate(Routes.runner(surveyId)) {
                            popUpTo(Routes.SURVEY_LIST)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start survey") }
        }
    }
}
