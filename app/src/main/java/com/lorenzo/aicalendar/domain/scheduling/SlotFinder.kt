package com.lorenzo.aicalendar.domain.scheduling

import com.lorenzo.aicalendar.domain.model.CalendarEvent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Deterministic scheduling helper: detects overlaps and proposes the nearest free slots of
 * the same duration. This is classic interval scheduling on the sorted busy list (the same
 * idea behind calendar "find a time" features) — done in code, never delegated to the LLM,
 * so conflicts and alternatives are reliable regardless of what the model noticed.
 */
object SlotFinder {

    data class Slot(val start: Instant, val end: Instant)

    /**
     * Timed events in [agenda] overlapping [candidate] (all-day entries are ignored, and an
     * event never conflicts with its own occurrences).
     */
    fun conflicts(candidate: CalendarEvent, agenda: List<CalendarEvent>): List<CalendarEvent> {
        if (candidate.allDay) return emptyList()
        val start = candidate.start
        val end = candidate.effectiveEnd
        val masterId = candidate.id.substringBefore("@")
        return agenda.asSequence()
            .filter { it.id.substringBefore("@") != masterId && !it.allDay }
            .filter { it.start.isBefore(end) && it.effectiveEnd.isAfter(start) }
            .distinctBy { it.id.substringBefore("@") }
            .toList()
    }

    /**
     * Free slots of the candidate's duration on the same local day, inside the
     * [dayStartHour, dayEndHour] window, ordered by closeness to the requested time.
     *
     * Algorithm: clamp the busy intervals to the window, merge them (sorted sweep), take the
     * complement as free gaps, then place the slot inside each big-enough gap as close to the
     * requested start as possible; slots not at the requested time are rounded up to the next
     * quarter hour when they still fit.
     */
    fun suggestAlternatives(
        candidate: CalendarEvent,
        agenda: List<CalendarEvent>,
        zone: ZoneId,
        max: Int = 2,
        dayStartHour: Int = 8,
        dayEndHour: Int = 22,
    ): List<Slot> {
        if (candidate.allDay) return emptyList()
        val duration = Duration.between(candidate.start, candidate.effectiveEnd)
        if (duration.isZero || duration.isNegative) return emptyList()

        val day = candidate.start.atZone(zone).toLocalDate()
        val windowStart = day.atTime(dayStartHour, 0).atZone(zone).toInstant()
        val windowEnd = day.atTime(dayEndHour, 0).atZone(zone).toInstant()
        if (!windowEnd.isAfter(windowStart)) return emptyList()

        val masterId = candidate.id.substringBefore("@")
        val busy = agenda.asSequence()
            .filter { !it.allDay && it.id.substringBefore("@") != masterId }
            .filter { it.start.isBefore(windowEnd) && it.effectiveEnd.isAfter(windowStart) }
            .map { maxOf(it.start, windowStart) to minOf(it.effectiveEnd, windowEnd) }
            .sortedBy { it.first }

        val merged = mutableListOf<Pair<Instant, Instant>>()
        for (interval in busy) {
            val last = merged.lastOrNull()
            if (last != null && !interval.first.isAfter(last.second)) {
                if (interval.second.isAfter(last.second)) {
                    merged[merged.lastIndex] = last.first to interval.second
                }
            } else {
                merged += interval
            }
        }

        val gaps = buildList {
            var cursor = windowStart
            for ((busyStart, busyEnd) in merged) {
                if (busyStart.isAfter(cursor)) add(cursor to busyStart)
                if (busyEnd.isAfter(cursor)) cursor = busyEnd
            }
            if (windowEnd.isAfter(cursor)) add(cursor to windowEnd)
        }

        val requested = candidate.start
        return gaps.asSequence()
            .filter { (gapStart, gapEnd) -> Duration.between(gapStart, gapEnd) >= duration }
            .map { (gapStart, gapEnd) ->
                val latestStart = gapEnd.minus(duration)
                val nearest = when {
                    requested.isBefore(gapStart) -> gapStart
                    requested.isAfter(latestStart) -> latestStart
                    else -> requested
                }
                Slot(roundedIfFits(nearest, latestStart), duration)
            }
            .sortedBy { Duration.between(requested, it.start).abs() }
            .take(max)
            .toList()
    }

    private fun Slot(start: Instant, duration: Duration) = Slot(start, start.plus(duration))

    /** Rounds [start] up to the next quarter hour, unless that pushes it past [latestStart]. */
    private fun roundedIfFits(start: Instant, latestStart: Instant): Instant {
        val quarterMillis = Duration.ofMinutes(15).toMillis()
        val remainder = start.toEpochMilli().mod(quarterMillis)
        if (remainder == 0L) return start
        val rounded = Instant.ofEpochMilli(start.toEpochMilli() - remainder + quarterMillis)
        return if (rounded.isAfter(latestStart)) start else rounded
    }
}
