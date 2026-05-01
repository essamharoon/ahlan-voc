package com.fbint.collector.ui.runner

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.fbint.collector.data.remote.dto.EndingDto
import com.fbint.collector.data.remote.dto.QuestionDto
import com.fbint.collector.data.remote.dto.SurveyDto
import com.fbint.collector.domain.QType
import com.fbint.collector.domain.localized
import com.fbint.collector.ui.runner.components.AddressQuestion
import com.fbint.collector.ui.runner.components.CalQuestion
import com.fbint.collector.ui.runner.components.ChoiceMultiQuestion
import com.fbint.collector.ui.runner.components.ChoiceSingleQuestion
import com.fbint.collector.ui.runner.components.ConsentQuestion
import com.fbint.collector.ui.runner.components.ContactInfoQuestion
import com.fbint.collector.ui.runner.components.CtaQuestion
import com.fbint.collector.ui.runner.components.DateQuestion
import com.fbint.collector.ui.runner.components.FileUploadQuestion
import com.fbint.collector.ui.runner.components.MatrixQuestion
import com.fbint.collector.ui.runner.components.NpsQuestion
import com.fbint.collector.ui.runner.components.OpenTextQuestion
import com.fbint.collector.ui.runner.components.PictureSelectionQuestion
import com.fbint.collector.ui.runner.components.RankingQuestion
import com.fbint.collector.ui.runner.components.RatingQuestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyRunnerScreen(nav: NavHostController, surveyId: String) {
    val vm: SurveyRunnerViewModel = hiltViewModel<SurveyRunnerViewModel, SurveyRunnerViewModel.Factory>(
        creationCallback = { factory -> factory.create(surveyId) },
    )
    val state by vm.state.collectAsState()
    val survey = state.survey

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(survey?.name ?: "Survey") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list")
                    }
                },
                actions = {
                    if (state.showLanguageSwitch) {
                        LanguageMenu(
                            current = state.language,
                            options = state.availableLanguages,
                            onPick = vm::setLanguage,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ProgressBar(state)
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                when (val stage = state.stage) {
                    RunnerStage.Loading, RunnerStage.Submitting ->
                        Centered { CircularProgressIndicator() }

                    RunnerStage.Welcome ->
                        WelcomePane(survey, state.language, vm::startFromWelcome)

                    is RunnerStage.Question -> {
                        val q = vm.currentQuestion() ?: return@Box
                        QuestionPane(
                            question = q,
                            lang = state.language,
                            answer = vm.questionInitialAnswer(q),
                            error = state.validationError,
                            onAnswer = { vm.setAnswer(q.id, it) },
                            delegate = vm,
                        )
                    }

                    is RunnerStage.Ending -> {
                        val ending = vm.currentEnding()
                        EndingPane(
                            ending = ending,
                            lang = state.language,
                            onAnother = vm::reset,
                            onExit = { nav.popBackStack() },
                        )
                    }

                    RunnerStage.Done -> DonePane(onAnother = vm::reset, onExit = { nav.popBackStack() })

                    is RunnerStage.Error -> Centered { Text(stage.message) }
                }
            }
            FooterButtons(state = state, onBack = vm::back, onNext = vm::next, onStart = vm::startFromWelcome)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageMenu(current: String, options: List<String>, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.Language, contentDescription = "Language: $current")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { code ->
            DropdownMenuItem(
                text = { Text(if (code == current) "$code  •  current" else code) },
                onClick = { expanded = false; onPick(code) },
            )
        }
    }
}

@Composable
private fun ProgressBar(state: RunnerState) {
    val survey = state.survey ?: return
    val total = survey.questions.size.coerceAtLeast(1)
    val current = (state.stage as? RunnerStage.Question)?.let { stage ->
        survey.questions.indexOfFirst { it.id == stage.questionId }
    } ?: return
    if (current < 0) return
    LinearProgressIndicator(
        progress = { (current + 1f) / total },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WelcomePane(survey: SurveyDto?, lang: String, onStart: () -> Unit) {
    val card = survey?.welcomeCard ?: return
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(card.headline.localized(lang), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        val body = card.html?.localized(lang)?.replace(Regex("<[^>]+>"), "")
        if (!body.isNullOrBlank()) Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun QuestionPane(
    question: QuestionDto,
    lang: String,
    answer: Any?,
    error: String?,
    onAnswer: (Any?) -> Unit,
    delegate: FileUploadDelegate,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(question.headline.localized(lang), style = MaterialTheme.typography.titleLarge)
        if (!question.subheader.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(question.subheader.localized(lang), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        when (question.type) {
            QType.OPEN_TEXT -> OpenTextQuestion(question, lang, answer as? String, onAnswer)
            QType.CHOICE_SINGLE -> ChoiceSingleQuestion(question, lang, answer as? String, onAnswer)
            QType.CHOICE_MULTI -> {
                @Suppress("UNCHECKED_CAST")
                ChoiceMultiQuestion(question, lang, answer as? List<String>, onAnswer)
            }
            QType.RATING -> RatingQuestion(question, lang, (answer as? Number)?.toInt(), onAnswer)
            QType.NPS -> NpsQuestion(question, lang, (answer as? Number)?.toInt(), onAnswer)
            QType.CTA -> CtaQuestion(question, lang, answer as? String, onAnswer)
            QType.CONSENT -> ConsentQuestion(question, lang, answer as? String, onAnswer)
            QType.PICTURE_SELECTION -> {
                @Suppress("UNCHECKED_CAST")
                PictureSelectionQuestion(question, answer as? List<String>, onAnswer)
            }
            QType.DATE -> DateQuestion(question, answer as? String, onAnswer)
            QType.MATRIX -> {
                @Suppress("UNCHECKED_CAST")
                MatrixQuestion(question, lang, answer as? Map<String, String>, onAnswer)
            }
            QType.RANKING -> {
                @Suppress("UNCHECKED_CAST")
                RankingQuestion(question, lang, answer as? List<String>, onAnswer)
            }
            QType.ADDRESS -> {
                @Suppress("UNCHECKED_CAST")
                AddressQuestion(question, lang, answer as? List<String>, onAnswer)
            }
            QType.CONTACT_INFO -> {
                @Suppress("UNCHECKED_CAST")
                ContactInfoQuestion(question, lang, answer as? List<String>, onAnswer)
            }
            QType.FILE_UPLOAD -> {
                @Suppress("UNCHECKED_CAST")
                FileUploadQuestion(question, delegate, answer as? List<String>, onAnswer)
            }
            QType.CAL -> CalQuestion(question, answer as? String, onAnswer)
            else -> Text("Unsupported question type: ${question.type}")
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EndingPane(ending: EndingDto?, lang: String, onAnother: () -> Unit, onExit: () -> Unit) {
    val ctx = LocalContext.current
    if (ending == null) {
        DonePane(onAnother = onAnother, onExit = onExit)
        return
    }
    when (ending.type) {
        "redirectToUrl" -> {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(ending.label ?: "Open link", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                if (!ending.url.isNullOrBlank()) {
                    Button(
                        onClick = {
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, ending.url.toUri()))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(ending.url) }
                }
                Spacer(Modifier.height(24.dp))
                ActionButtons(onAnother, onExit)
            }
        }
        else -> {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(ending.headline.localized(lang).ifBlank { "Thank you" }, style = MaterialTheme.typography.headlineSmall)
                if (!ending.subheader.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(ending.subheader.localized(lang))
                }
                Spacer(Modifier.height(24.dp))
                ActionButtons(onAnother, onExit)
            }
        }
    }
}

@Composable
private fun DonePane(onAnother: () -> Unit, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Saved offline", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Will sync as soon as a network is available.")
        Spacer(Modifier.height(24.dp))
        ActionButtons(onAnother, onExit)
    }
}

@Composable
private fun ActionButtons(onAnother: () -> Unit, onExit: () -> Unit) {
    Button(onClick = onAnother, modifier = Modifier.fillMaxWidth()) { Text("Collect another response") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Back to surveys") }
}

@Composable
private fun FooterButtons(
    state: RunnerState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onStart: () -> Unit,
) {
    when (val stage = state.stage) {
        RunnerStage.Welcome -> Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start") }
        }
        is RunnerStage.Question -> Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val survey = state.survey ?: return
            val canBack = survey.isBackButtonHidden != true
            if (canBack) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                val isLast = survey.questions.indexOfFirst { it.id == stage.questionId } == survey.questions.lastIndex
                Text(if (isLast) "Submit" else "Next")
            }
        }
        else -> Unit
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
