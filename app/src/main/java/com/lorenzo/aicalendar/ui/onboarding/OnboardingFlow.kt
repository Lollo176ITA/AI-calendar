package com.lorenzo.aicalendar.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

private const val STEP_PROFILE = 0
private const val STEP_ROUTINE = 1

/** First-launch onboarding: profile form, then the guided routine chat. */
@Composable
fun OnboardingFlow() {
    var step by rememberSaveable { mutableIntStateOf(STEP_PROFILE) }

    when (step) {
        STEP_PROFILE -> OnboardingScreen(onContinue = { step = STEP_ROUTINE })
        else -> RoutineOnboardingScreen()
    }
}
