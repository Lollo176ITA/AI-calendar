package com.lorenzo.aicalendar.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A single agenda entry, in domain terms (no persistence/UI concerns).
 *
 * Times are absolute [Instant]s plus the [zone] they were authored in, so the app can
 * render them in the user's local time and still reason about them across time zones.
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val start: Instant,
    val zone: ZoneId,
    val end: Instant? = null,
    val allDay: Boolean = false,
    val location: String? = null,
    val notes: String? = null,
    val source: EventSource = EventSource.MANUAL,
    val reminderOffsetMin: Int? = DEFAULT_REMINDER_OFFSET_MIN,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** End time to use for display/conflict math — defaults to [start] + [DEFAULT_DURATION]. */
    val effectiveEnd: Instant
        get() = end ?: start.plus(DEFAULT_DURATION)

    companion object {
        /** Fallback duration when an event has no explicit end. */
        val DEFAULT_DURATION: Duration = Duration.ofHours(1)

        /** Default "remind me N minutes before" offset. */
        const val DEFAULT_REMINDER_OFFSET_MIN: Int = 30
    }
}
