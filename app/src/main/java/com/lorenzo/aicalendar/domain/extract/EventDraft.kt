package com.lorenzo.aicalendar.domain.extract

import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import com.lorenzo.aicalendar.domain.model.Recurrence
import java.time.Instant

/**
 * A proposed event produced by an [EventExtractor], before the user confirms/edits it.
 * [start] is null when the extractor couldn't determine a date/time (the user must pick one).
 */
data class EventDraft(
    val title: String,
    val start: Instant?,
    val end: Instant? = null,
    val allDay: Boolean = false,
    val location: String? = null,
    val reminderOffsetMin: Int? = CalendarEvent.DEFAULT_REMINDER_OFFSET_MIN,
    val recurrence: Recurrence? = null,
    val source: EventSource = EventSource.MANUAL,
)
