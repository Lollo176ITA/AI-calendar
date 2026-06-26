package com.lorenzo.aicalendar

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. [@HiltAndroidApp] triggers Hilt's code generation and
 * creates the app-level dependency container that every injected class draws from.
 */
@HiltAndroidApp
class AiCalendarApp : Application()
