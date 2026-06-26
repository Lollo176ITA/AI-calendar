package com.lorenzo.aicalendar.data.reminder

import com.lorenzo.aicalendar.domain.model.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class ReminderTimeTest {

    private fun event(offsetMin: Int?) = CalendarEvent(
        id = "x",
        title = "t",
        start = Instant.ofEpochMilli(1_000_000_000_000),
        zone = ZoneId.of("Europe/Rome"),
        reminderOffsetMin = offsetMin,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `trigger is start minus the offset`() {
        assertEquals(1_000_000_000_000 - 30 * 60_000L, reminderTriggerMillis(event(30)))
    }

    @Test
    fun `null offset means no reminder`() {
        assertNull(reminderTriggerMillis(event(null)))
    }

    @Test
    fun `zero offset triggers exactly at start`() {
        assertEquals(1_000_000_000_000L, reminderTriggerMillis(event(0)))
    }
}
