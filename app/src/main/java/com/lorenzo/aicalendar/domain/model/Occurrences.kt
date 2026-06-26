package com.lorenzo.aicalendar.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

private const val MAX_OCCURRENCES = 1500

/**
 * Expands an event into its occurrences within `[rangeStart, rangeEnd)`.
 * A non-recurring event yields itself (if in range); a recurring one yields zone-aware
 * occurrences stepping by frequency × interval, each as a copy with a shifted start/end.
 */
fun CalendarEvent.occurrencesInRange(
    rangeStart: Instant,
    rangeEnd: Instant,
    zone: ZoneId,
): List<CalendarEvent> {
    val rule = recurrence
        ?: return if (!start.isBefore(rangeStart) && start.isBefore(rangeEnd)) listOf(this) else emptyList()

    val durationMillis = effectiveEnd.toEpochMilli() - start.toEpochMilli()
    val base = start.atZone(zone)
    val result = mutableListOf<CalendarEvent>()

    var i = 0
    while (i < MAX_OCCURRENCES) {
        val occ = base.advance(rule, i).toInstant()
        if (!occ.isBefore(rangeEnd)) break
        if (!occ.isBefore(rangeStart)) {
            result += copy(
                id = "$id@$i",
                start = occ,
                end = end?.let { Instant.ofEpochMilli(occ.toEpochMilli() + durationMillis) },
            )
        }
        i++
    }
    return result
}

/** The first occurrence at or after [after], or null if none/non-recurring is already past. */
fun CalendarEvent.nextOccurrenceStart(after: Instant, zone: ZoneId): Instant? {
    val rule = recurrence ?: return start.takeIf { !it.isBefore(after) }
    val base = start.atZone(zone)
    var i = 0
    while (i < MAX_OCCURRENCES) {
        val occ = base.advance(rule, i).toInstant()
        if (!occ.isBefore(after)) return occ
        i++
    }
    return null
}

private fun ZonedDateTime.advance(rule: Recurrence, steps: Int): ZonedDateTime {
    val n = steps.toLong() * rule.interval
    return when (rule.frequency) {
        Frequency.DAILY -> plusDays(n)
        Frequency.WEEKLY -> plusWeeks(n)
        Frequency.MONTHLY -> plusMonths(n)
        Frequency.YEARLY -> plusYears(n)
    }
}
