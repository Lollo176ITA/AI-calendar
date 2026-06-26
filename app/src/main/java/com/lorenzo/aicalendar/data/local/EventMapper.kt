package com.lorenzo.aicalendar.data.local

import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import com.lorenzo.aicalendar.domain.model.Frequency
import com.lorenzo.aicalendar.domain.model.Recurrence
import java.time.Instant
import java.time.ZoneId

/** Maps a persisted [EventEntity] to the domain [CalendarEvent]. */
fun EventEntity.toDomain(): CalendarEvent = CalendarEvent(
    id = id,
    title = title,
    start = Instant.ofEpochMilli(startEpochMillis),
    zone = ZoneId.of(zoneId),
    end = endEpochMillis?.let(Instant::ofEpochMilli),
    allDay = allDay,
    location = location,
    notes = notes,
    // Tolerate unknown/renamed values so an older row can't crash the app.
    source = runCatching { EventSource.valueOf(source) }.getOrDefault(EventSource.MANUAL),
    reminderOffsetMin = reminderOffsetMin,
    recurrence = recurrenceFreq
        ?.let { freq -> runCatching { Frequency.valueOf(freq) }.getOrNull() }
        ?.let { Recurrence(it, recurrenceInterval.coerceAtLeast(1)) },
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

/** Maps a domain [CalendarEvent] to its persisted [EventEntity]. */
fun CalendarEvent.toEntity(): EventEntity = EventEntity(
    id = id,
    title = title,
    startEpochMillis = start.toEpochMilli(),
    zoneId = zone.id,
    endEpochMillis = end?.toEpochMilli(),
    allDay = allDay,
    location = location,
    notes = notes,
    source = source.name,
    reminderOffsetMin = reminderOffsetMin,
    recurrenceFreq = recurrence?.frequency?.name,
    recurrenceInterval = recurrence?.interval ?: 1,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)
