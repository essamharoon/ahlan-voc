package com.fbint.collector.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fbint.collector.data.repository.ConfigRepository
import com.fbint.collector.ui.runner.SurveyRunnerScreen
import com.fbint.collector.ui.setup.AdminSetupScreen
import com.fbint.collector.ui.setup.QrGenerateScreen
import com.fbint.collector.ui.setup.QrScanScreen
import com.fbint.collector.ui.setup.RolePickerScreen
import com.fbint.collector.ui.status.SyncStatusScreen
import com.fbint.collector.ui.surveylist.SurveyListScreen
import com.fbint.collector.ui.surveyor.SurveyorIdScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object Routes {
    const val ROLE = "role"
    const val ADMIN_SETUP = "admin/setup"
    const val ADMIN_QR = "admin/qr"
    const val SURVEYOR_SCAN = "surveyor/scan"
    const val SURVEYOR_ID = "surveyor/id"
    const val SURVEY_LIST = "surveyList"
    const val RUNNER = "runner/{surveyId}"
    const val SYNC_STATUS = "sync"
    fun runner(id: String) = "runner/$id"
}

@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    config: ConfigRepository,
) : ViewModel() {
    val state = config.observeOnboardingState()
        .stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingState.Unknown)
}

sealed interface OnboardingState {
    data object Unknown : OnboardingState
    data object NoConfig : OnboardingState
    data object NoSurveyor : OnboardingState
    data object Ready : OnboardingState
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: StartDestinationViewModel = hiltViewModel()
    val state by vm.state.collectAsState()

    val start = when (state) {
        OnboardingState.Unknown -> Routes.ROLE
        OnboardingState.NoConfig -> Routes.ROLE
        OnboardingState.NoSurveyor -> Routes.SURVEYOR_ID
        OnboardingState.Ready -> Routes.SURVEY_LIST
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.ROLE) { RolePickerScreen(nav) }
        composable(Routes.ADMIN_SETUP) { AdminSetupScreen(nav) }
        composable(Routes.ADMIN_QR) { QrGenerateScreen(nav) }
        composable(Routes.SURVEYOR_SCAN) { QrScanScreen(nav) }
        composable(Routes.SURVEYOR_ID) { SurveyorIdScreen(nav) }
        composable(Routes.SURVEY_LIST) { SurveyListScreen(nav) }
        composable(
            route = Routes.RUNNER,
            arguments = listOf(navArgument("surveyId") { type = NavType.StringType }),
        ) { backStack ->
            val id = backStack.arguments?.getString("surveyId").orEmpty()
            SurveyRunnerScreen(nav, surveyId = id)
        }
        composable(Routes.SYNC_STATUS) { SyncStatusScreen(nav) }
    }
}
