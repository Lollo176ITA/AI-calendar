package com.lorenzo.aicalendar.data.local

import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import org.junit.Assert.assertEquals
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

class EventMapperTest {

    private val sample = CalendarEvent(
        id = "evt-1",
        title = "Appuntamento dal medico",
        start = Instant.ofEpochMilli(1_780_000_000_000),
        zone = ZoneId.of("Europe/Rome"),
        end = Instant.ofEpochMilli(1_780_003_600_000),
        allDay = false,
        location = "Studio medico",
        notes = "Portare gli esami",
        source = EventSource.AI_TEXT,
        reminderOffsetMin = 45,
        createdAt = Instant.ofEpochMilli(1_779_000_000_000),
        updatedAt = Instant.ofEpochMilli(1_779_500_000_000),
    )

    @Test
    fun `domain to entity to domain round-trips losslessly`() {
        val restored = sample.toEntity().toDomain()
        assertEquals(sample, restored)
    }

    @Test
    fun `null end and optional fields survive the round-trip`() {
        val minimal = sample.copy(end = null, location = null, notes = null, reminderOffsetMin = null)
        val restored = minimal.toEntity().toDomain()
        assertEquals(minimal, restored)
    }

    @Test
    fun `entity stores source as the enum name`() {
        assertEquals("AI_TEXT", sample.toEntity().source)
    }

    @Test
    fun `unknown source string falls back to MANUAL`() {
        val entity = sample.toEntity().copy(source = "SOMETHING_NEW")
        assertEquals(EventSource.MANUAL, entity.toDomain().source)
    }
}
