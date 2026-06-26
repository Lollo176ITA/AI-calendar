package com.lorenzo.aicalendar.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lorenzo.aicalendar.ui.calendar.CalendarScreen
import com.lorenzo.aicalendar.ui.quickadd.QuickAddScreen

private object Routes {
    const val CALENDAR = "calendar"
    const val QUICK_ADD = "quickadd"
}

/** Single-activity navigation graph. */
@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.CALENDAR) {
        composable(Routes.CALENDAR) {
            CalendarScreen(onAddEvent = { nav.navigate(Routes.QUICK_ADD) })
        }
        composable(Routes.QUICK_ADD) {
            QuickAddScreen(onClose = { nav.popBackStack() })
        }
    }
}
