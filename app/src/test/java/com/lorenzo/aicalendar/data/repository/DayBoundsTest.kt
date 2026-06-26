package com.lorenzo.aicalendar.data.repository

import org.junit.Assert.assertEquals
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class DayBoundsTest {

    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun `start is local midnight and end is next local midnight`() {
        val date = LocalDate.of(2026, 6, 26)
        val (start, end) = dayBoundsUtcMillis(date, rome)

        val expectedStart = ZonedDateTime.of(2026, 6, 26, 0, 0, 0, 0, rome).toInstant().toEpochMilli()
        val expectedEnd = ZonedDateTime.of(2026, 6, 27, 0, 0, 0, 0, rome).toInstant().toEpochMilli()
        assertEquals(expectedStart, start)
        assertEquals(expectedEnd, end)
    }

    @Test
    fun `a normal day spans exactly 24 hours`() {
        val (start, end) = dayBoundsUtcMillis(LocalDate.of(2026, 6, 26), rome)
        assertEquals(Duration.ofHours(24).toMillis(), end - start)
    }

    @Test
    fun `DST spring-forward day spans 23 hours, not 24`() {
        // 2026-03-29: Europe/Rome moves clocks forward 1h → the local day is only 23h long.
        // A naive "+24h" boundary would be wrong; zone-aware math must yield 23h.
        val (start, end) = dayBoundsUtcMillis(LocalDate.of(2026, 3, 29), rome)
        assertEquals(Duration.ofHours(23).toMillis(), end - start)
    }
}
