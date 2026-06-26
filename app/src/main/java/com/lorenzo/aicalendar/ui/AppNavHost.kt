package com.lorenzo.aicalendar.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lorenzo.aicalendar.ui.quickadd.QuickAddScreen
import com.lorenzo.aicalendar.ui.today.TodayScreen

private object Routes {
    const val TODAY = "today"
    const val QUICK_ADD = "quickadd"
}

/** Single-activity navigation graph. */
@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.TODAY) {
        composable(Routes.TODAY) {
            TodayScreen(onAddEvent = { nav.navigate(Routes.QUICK_ADD) })
        }
        composable(Routes.QUICK_ADD) {
            QuickAddScreen(onClose = { nav.popBackStack() })
        }
    }
}
