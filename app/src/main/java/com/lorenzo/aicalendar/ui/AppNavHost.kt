package com.lorenzo.aicalendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lorenzo.aicalendar.ui.calendar.CalendarScreen
import com.lorenzo.aicalendar.ui.onboarding.OnboardingScreen
import com.lorenzo.aicalendar.ui.quickadd.QuickAddScreen
import com.lorenzo.aicalendar.ui.settings.SettingsScreen

private object Routes {
    const val CALENDAR = "calendar"
    const val QUICK_ADD = "quickadd"
    const val SETTINGS = "settings"
}

/** Root: gates first launch on the onboarding flag, then shows the main navigation graph. */
@Composable
fun AppNavHost(rootViewModel: RootViewModel = hiltViewModel()) {
    val onboardingDone by rootViewModel.onboardingCompleted.collectAsStateWithLifecycle()

    when (onboardingDone) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        false -> OnboardingScreen()
        true -> MainGraph()
    }
}

@Composable
private fun MainGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.CALENDAR) {
        composable(Routes.CALENDAR) {
            CalendarScreen(
                onAddEvent = { nav.navigate(Routes.QUICK_ADD) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.QUICK_ADD) {
            QuickAddScreen(onClose = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onClose = { nav.popBackStack() })
        }
    }
}
