package com.lorenzo.aicalendar.domain.model

import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

private const val MAX_OCCURRENCES = 2000

/**
 * Expands an event into its occurrences within `[rangeStart, rangeEnd)`. Non-recurring events
 * yield themselves (if in range); recurring ones expand their RRULE with lib-recur, anchored at
 * the event start (so the time-of-day and any BY* rules are honored, DST-correctly).
 */
fun CalendarEvent.occurrencesInRange(
    rangeStart: Instant,
    rangeEnd: Instant,
    zone: ZoneId,
): List<CalendarEvent> {
    val rule = parsedRuleOrNull()
        ?: return if (!start.isBefore(rangeStart) && start.isBefore(rangeEnd)) listOf(this) else emptyList()

    val tz = TimeZone.getTimeZone(zone)
    val durationMillis = effectiveEnd.toEpochMilli() - start.toEpochMilli()
    val iterator = rule.iterator(dateTimeOf(start, zone, tz))
    if (rangeStart.isAfter(start)) iterator.fastForward(dateTimeOf(rangeStart, zone, tz))

    val rangeEndMillis = rangeEnd.toEpochMilli()
    val rangeStartMillis = rangeStart.toEpochMilli()
    val result = mutableListOf<CalendarEvent>()
    var guard = 0
    while (iterator.hasNext() && guard < MAX_OCCURRENCES) {
        val ms = iterator.nextMillis()
        if (ms >= rangeEndMillis) break
        if (ms >= rangeStartMillis) {
            result += copy(
                id = "$id@$ms",
                start = Instant.ofEpochMilli(ms),
                end = end?.let { Instant.ofEpochMilli(ms + durationMillis) },
            )
        }
        guard++
    }
    return result
}

/** The first occurrence at or after [after], or null if none. */
fun CalendarEvent.nextOccurrenceStart(after: Instant, zone: ZoneId): Instant? {
    val rule = parsedRuleOrNull() ?: return start.takeIf { !it.isBefore(after) }

    val tz = TimeZone.getTimeZone(zone)
    val iterator = rule.iterator(dateTimeOf(start, zone, tz))
    if (after.isAfter(start)) iterator.fastForward(dateTimeOf(after, zone, tz))

    val afterMillis = after.toEpochMilli()
    var guard = 0
    while (iterator.hasNext() && guard < MAX_OCCURRENCES) {
        val ms = iterator.nextMillis()
        if (ms >= afterMillis) return Instant.ofEpochMilli(ms)
        guard++
    }
    return null
}

private fun CalendarEvent.parsedRuleOrNull(): RecurrenceRule? =
    recurrence?.rrule?.let { runCatching { RecurrenceRule(it) }.getOrNull() }

private fun dateTimeOf(instant: Instant, zone: ZoneId, tz: TimeZone): DateTime {
    val z = instant.atZone(zone)
    return DateTime(tz, z.year, z.monthValue - 1, z.dayOfMonth, z.hour, z.minute, z.second)
}
