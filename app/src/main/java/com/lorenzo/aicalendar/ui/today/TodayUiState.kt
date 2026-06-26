package com.lorenzo.aicalendar.ui.today

import com.lorenzo.aicalendar.domain.model.CalendarEvent
import java.time.LocalDate

/** Immutable state for the "Oggi" screen. */
data class TodayUiState(
    val date: LocalDate,
    val events: List<CalendarEvent>,
)
