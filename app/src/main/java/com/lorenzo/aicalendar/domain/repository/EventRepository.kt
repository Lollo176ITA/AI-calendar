package com.lorenzo.aicalendar.domain.repository

import com.lorenzo.aicalendar.domain.model.CalendarEvent
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Source of truth for agenda events. The MVP implementation is local (Room); the
 * interface is the seam where a cloud-sync implementation can be added later without
 * touching the domain or UI.
 */
interface EventRepository {

    /** Events starting on [date] (in [zone]), ordered by start time, as a live stream. */
    fun observeEventsForDay(date: LocalDate, zone: ZoneId): Flow<List<CalendarEvent>>

    /** Events starting in `[startDate, endDateExclusive)` (in [zone]), ordered by start. */
    fun observeEventsInRange(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zone: ZoneId,
    ): Flow<List<CalendarEvent>>

    /** Like [observeEventsInRange] but expands recurring events into their occurrences. */
    fun observeOccurrencesInRange(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zone: ZoneId,
    ): Flow<List<CalendarEvent>>

    /**
     * Upcoming events for the assistant context / reminder re-arming: non-recurring events
     * from [from], plus each recurring event's next occurrence at/after [from].
     */
    suspend fun getUpcomingEvents(from: Instant, zone: ZoneId): List<CalendarEvent>

    suspend fun getEvent(id: String): CalendarEvent?

    /** Inserts a new event or replaces the existing one with the same id. */
    suspend fun upsert(event: CalendarEvent)

    suspend fun delete(id: String)
}
