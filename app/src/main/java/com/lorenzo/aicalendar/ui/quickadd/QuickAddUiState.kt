package com.lorenzo.aicalendar.ui.quickadd

import java.time.LocalDate
import java.time.LocalTime

/** Editable, UI-friendly version of an extracted draft (split date/time for the pickers). */
data class EditableDraft(
    val title: String,
    val date: LocalDate,
    val time: LocalTime,
    val location: String,
)

data class QuickAddUiState(
    val inputText: String = "",
    val isExtracting: Boolean = false,
    /** Non-null once interpreted → the screen switches to review/confirm mode. */
    val draft: EditableDraft? = null,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    /** Set after a successful save so the screen can close. */
    val saved: Boolean = false,
)
