package com.lorenzo.aicalendar.domain.scheduling

import com.lorenzo.aicalendar.domain.model.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SlotFinderTest {

    private val zone = ZoneId.of("Europe/Rome")
    private val day = LocalDate.of(2026, 7, 8)

    private fun at(hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(day, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant()

    private fun event(
        id: String,
        start: Instant,
        end: Instant?,
        allDay: Boolean = false,
    ) = CalendarEvent(
        id = id,
        title = "event-$id",
        start = start,
        zone = zone,
        end = end,
        allDay = allDay,
        createdAt = start,
        updatedAt = start,
    )

    @Test
    fun conflicts_findsOverlap_ignoresAllDayAndSelf() {
        val candidate = event("new", at(15), at(16))
        val agenda = listOf(
            event("overlap", at(15, 30), at(17)),
            event("before", at(13), at(14)),
            event("allday", at(0), null, allDay = true),
            event("new@123", at(15), at(16)), // own occurrence
        )
        val clashes = SlotFinder.conflicts(candidate, agenda)
        assertEquals(listOf("overlap"), clashes.map { it.id })
    }

    @Test
    fun conflicts_backToBackIsNotAConflict() {
        val candidate = event("new", at(15), at(16))
        val agenda = listOf(event("adjacent", at(16), at(17)))
        assertTrue(SlotFinder.conflicts(candidate, agenda).isEmpty())
    }

    @Test
    fun suggestAlternatives_proposesNearestFreeSlots() {
        // Busy 15-17; asking for 15-16. Nearest fit is 14:00 (1h away, right before);
        // the slot after the busy block starts at 17:00 (2h away) and comes second.
        val candidate = event("new", at(15), at(16))
        val agenda = listOf(event("busy", at(15), at(17)))

        val slots = SlotFinder.suggestAlternatives(candidate, agenda, zone, max = 2)

        assertEquals(2, slots.size)
        assertEquals(at(14), slots[0].start)
        assertEquals(at(15), slots[0].end)
        assertEquals(at(17), slots[1].start)
        assertEquals(at(18), slots[1].end)
    }

    @Test
    fun suggestAlternatives_respectsDayWindowAndSkipsSmallGaps() {
        // Day window 8-22. Busy 8-15 and 15:30-21:30: the 15:00-15:30 gap is too small for 1h,
        // so the only fit is 21:30-22:00... too small too; nothing before 8. Only 21:30+ rejected →
        // expect just the tail gap if it fits: it doesn't (30 min), so no suggestions.
        val candidate = event("new", at(15), at(16))
        val agenda = listOf(
            event("morning", at(8), at(15)),
            event("evening", at(15, 30), at(21, 30)),
        )
        val slots = SlotFinder.suggestAlternatives(candidate, agenda, zone)
        assertTrue(slots.isEmpty())
    }

    @Test
    fun suggestAlternatives_roundsOddStartsToQuarterHour() {
        // Morning fully busy (8-15) then 15:00-16:37: the only gap starts at the odd 16:37,
        // which gets rounded up to the next quarter hour, 16:45.
        val candidate = event("new", at(15), at(16))
        val agenda = listOf(
            event("morning", at(8), at(15)),
            event("busy", at(15), at(16, 37)),
        )

        val slots = SlotFinder.suggestAlternatives(candidate, agenda, zone, max = 1)

        assertEquals(at(16, 45), slots.single().start)
    }

    @Test
    fun suggestAlternatives_keepsRequestedTimeWhenFree() {
        // No conflicts at all: the best "alternative" is the requested time itself.
        val candidate = event("new", at(15), at(16))
        val slots = SlotFinder.suggestAlternatives(candidate, emptyList(), zone, max = 1)
        assertEquals(at(15), slots.single().start)
    }

    @Test
    fun suggestAlternatives_allDayCandidateHasNoSuggestions() {
        val candidate = event("new", at(0), null, allDay = true)
        assertTrue(SlotFinder.suggestAlternatives(candidate, emptyList(), zone).isEmpty())
    }
}
