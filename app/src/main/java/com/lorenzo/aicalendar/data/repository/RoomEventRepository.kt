package com.lorenzo.aicalendar.data.repository

import com.lorenzo.aicalendar.data.calendar.SystemCalendarMirror
import com.lorenzo.aicalendar.data.local.EventDao
import com.lorenzo.aicalendar.data.local.toDomain
import com.lorenzo.aicalendar.data.local.toEntity
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.nextOccurrenceStart
import com.lorenzo.aicalendar.domain.model.occurrencesInRange
import com.lorenzo.aicalendar.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Room-backed [EventRepository]: translates day queries to epoch-millis bounds and maps rows.
 * Saves/deletes are also mirrored to the device calendar via [SystemCalendarMirror] (when the
 * user enabled it in Settings), keeping the CalendarContract event id alongside the Room row.
 */
class RoomEventRepository @Inject constructor(
    private val dao: EventDao,
    private val mirror: SystemCalendarMirror,
) : EventRepository {

    override fun observeEventsForDay(date: LocalDate, zone: ZoneId): Flow<List<CalendarEvent>> {
        val (start, end) = dayBoundsUtcMillis(date, zone)
        return dao.observeBetween(start, end).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeEventsInRange(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zone: ZoneId,
    ): Flow<List<CalendarEvent>> {
        val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = endDateExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.observeBetween(start, end).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeOccurrencesInRange(
        startDate: LocalDate,
        endDateExclusive: LocalDate,
        zone: ZoneId,
    ): Flow<List<CalendarEvent>> {
        val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endDateExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeStart = Instant.ofEpochMilli(startMillis)
        val rangeEnd = Instant.ofEpochMilli(endMillis)

        val nonRecurring = dao.observeBetween(startMillis, endMillis)
            .map { rows -> rows.map { it.toDomain() }.filter { it.recurrence == null } }
        val recurring = dao.observeRecurring()
            .map { rows -> rows.map { it.toDomain() } }

        return combine(nonRecurring, recurring) { single, masters ->
            single + masters.flatMap { it.occurrencesInRange(rangeStart, rangeEnd, zone) }
        }
    }

    override suspend fun getUpcomingEvents(from: Instant, zone: ZoneId): List<CalendarEvent> {
        val nonRecurring = dao.getStartingAtOrAfter(from.toEpochMilli())
            .map { it.toDomain() }
            .filter { it.recurrence == null }
        val recurringNext = dao.getRecurring().map { it.toDomain() }.mapNotNull { master ->
            master.nextOccurrenceStart(from, zone)?.let { occ ->
                val durationMillis = master.effectiveEnd.toEpochMilli() - master.start.toEpochMilli()
                master.copy(
                    start = occ,
                    end = master.end?.let { Instant.ofEpochMilli(occ.toEpochMilli() + durationMillis) },
                )
            }
        }
        return (nonRecurring + recurringNext).sortedBy { it.start }
    }

    override fun observeRecurringEvents(): Flow<List<CalendarEvent>> =
        dao.observeRecurring().map { rows -> rows.map { it.toDomain() } }

    override fun searchEvents(query: String): Flow<List<CalendarEvent>> =
        dao.search(query).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getEvent(id: String): CalendarEvent? = dao.getById(id)?.toDomain()

    override suspend fun upsert(event: CalendarEvent) {
        val existingSystemId = dao.getById(event.id)?.systemEventId
        val systemId = mirror.sync(event, existingSystemId)
        dao.upsert(event.toEntity().copy(systemEventId = systemId))
    }

    override suspend fun delete(id: String) {
        mirror.delete(dao.getById(id)?.systemEventId)
        dao.deleteById(id)
    }
}
