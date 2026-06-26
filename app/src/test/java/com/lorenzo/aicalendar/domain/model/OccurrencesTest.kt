package com.lorenzo.aicalendar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Recurrence-expansion tests — the heart of the "complex recurrence done right" fix.
 * In particular they pin the difference the user hit: "un sabato al mese" (monthly, one Saturday)
 * must NOT behave like "ogni sabato" (weekly, every Saturday).
 */
class OccurrencesTest {

    private val zone: ZoneId = ZoneId.of("Europe/Rome")

    private fun event(rrule: String, start: LocalDateTime) = CalendarEvent(
        id = "e1",
        title = "Test",
        start = start.atZone(zone).toInstant(),
        zone = zone,
        recurrence = Recurrence(rrule, "label"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun midnight(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

    private fun List<CalendarEvent>.dates(): List<LocalDate> =
        map { it.start.atZone(zone).toLocalDate() }

    @Test
    fun `un sabato al mese yields exactly one (first) Saturday per month`() {
        // 4 Jul 2026 is the first Saturday of July.
        val e = event("FREQ=MONTHLY;BYDAY=1SA", LocalDateTime.of(2026, 7, 4, 20, 0))

        val occ = e.occurrencesInRange(
            midnight(LocalDate.of(2026, 7, 1)),
            midnight(LocalDate.of(2026, 10, 1)),
            zone,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 7, 4), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 5)),
            occ.dates(),
        )
        assertTrue(occ.dates().all { it.dayOfWeek == DayOfWeek.SATURDAY })
        assertTrue("must be the FIRST week", occ.dates().all { it.dayOfMonth <= 7 })
    }

    @Test
    fun `ogni sabato yields every Saturday (weekly)`() {
        val e = event("FREQ=WEEKLY;BYDAY=SA", LocalDateTime.of(2026, 7, 4, 20, 0))

        val occ = e.occurrencesInRange(
            midnight(LocalDate.of(2026, 7, 1)),
            midnight(LocalDate.of(2026, 8, 1)),
            zone,
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 4),
                LocalDate.of(2026, 7, 11),
                LocalDate.of(2026, 7, 18),
                LocalDate.of(2026, 7, 25),
            ),
            occ.dates(),
        )
    }

    @Test
    fun `daily yields one per day`() {
        val e = event("FREQ=DAILY", LocalDateTime.of(2026, 7, 1, 9, 0))

        val occ = e.occurrencesInRange(
            midnight(LocalDate.of(2026, 7, 1)),
            midnight(LocalDate.of(2026, 7, 8)),
            zone,
        )

        assertEquals(7, occ.size)
    }

    @Test
    fun `nextOccurrenceStart skips past dates to the next monthly Saturday`() {
        val e = event("FREQ=MONTHLY;BYDAY=1SA", LocalDateTime.of(2026, 7, 4, 20, 0))

        val next = e.nextOccurrenceStart(midnight(LocalDate.of(2026, 7, 10)), zone)

        assertEquals(LocalDate.of(2026, 8, 1), next?.atZone(zone)?.toLocalDate())
    }

    @Test
    fun `non-recurring event yields itself only when in range`() {
        val e = CalendarEvent(
            id = "x",
            title = "One-off",
            start = LocalDateTime.of(2026, 7, 4, 10, 0).atZone(zone).toInstant(),
            zone = zone,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val inRange = e.occurrencesInRange(
            midnight(LocalDate.of(2026, 7, 1)),
            midnight(LocalDate.of(2026, 7, 8)),
            zone,
        )
        val outOfRange = e.occurrencesInRange(
            midnight(LocalDate.of(2026, 8, 1)),
            midnight(LocalDate.of(2026, 8, 8)),
            zone,
        )

        assertEquals(1, inRange.size)
        assertEquals(0, outOfRange.size)
    }
}
