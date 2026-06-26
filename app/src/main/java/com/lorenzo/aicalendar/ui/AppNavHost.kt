package com.lorenzo.aicalendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lorenzo.aicalendar.ui.assistant.AssistantScreen
import com.lorenzo.aicalendar.ui.calendar.CalendarScreen
import com.lorenzo.aicalendar.ui.event.EventDetailScreen
import com.lorenzo.aicalendar.ui.onboarding.OnboardingFlow
import com.lorenzo.aicalendar.ui.sections.RecurringScreen
import com.lorenzo.aicalendar.ui.sections.SearchScreen
import com.lorenzo.aicalendar.ui.sections.SummaryScreen
import com.lorenzo.aicalendar.ui.sections.UpcomingScreen
import com.lorenzo.aicalendar.ui.settings.SettingsScreen

object AppDestinations {
    const val CALENDAR = "calendar"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"
    const val UPCOMING = "upcoming"
    const val RECURRING = "recurring"
    const val SEARCH = "search"
    const val SUMMARY = "summary"
    const val EVENT = "event/{id}"
    fun event(id: String) = "event/$id"
}

/** Root: gates first launch on the onboarding flag, then shows the main navigation graph. */
@Composable
fun AppNavHost(rootViewModel: RootViewModel = hiltViewModel()) {
    val onboardingDone by rootViewModel.onboardingCompleted.collectAsStateWithLifecycle()

    when (onboardingDone) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        false -> OnboardingFlow()
        true -> MainGraph()
    }
}

@Composable
private fun MainGraph() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(navController = nav, startDestination = AppDestinations.CALENDAR) {
        composable(AppDestinations.CALENDAR) {
            CalendarScreen(
                onAddEvent = { nav.navigate(AppDestinations.ASSISTANT) },
                onNavigate = { route -> nav.navigate(route) },
            )
        }
        composable(AppDestinations.ASSISTANT) { AssistantScreen(onClose = back) }
        composable(AppDestinations.SETTINGS) { SettingsScreen(onClose = back) }
        composable(AppDestinations.UPCOMING) { UpcomingScreen(onClose = back) }
        composable(AppDestinations.RECURRING) { RecurringScreen(onClose = back) }
        composable(AppDestinations.SEARCH) { SearchScreen(onClose = back) }
        composable(AppDestinations.SUMMARY) { SummaryScreen(onClose = back) }
        composable(AppDestinations.EVENT) { EventDetailScreen(onClose = back) }
    }
}
