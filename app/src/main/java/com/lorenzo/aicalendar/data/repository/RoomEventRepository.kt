package com.lorenzo.aicalendar.data.repository

import com.lorenzo.aicalendar.data.local.EventDao
import com.lorenzo.aicalendar.data.local.toDomain
import com.lorenzo.aicalendar.data.local.toEntity
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Room-backed [EventRepository]: translates day queries to epoch-millis bounds and maps rows. */
class RoomEventRepository @Inject constructor(
    private val dao: EventDao,
) : EventRepository {

    override fun observeEventsForDay(date: LocalDate, zone: ZoneId): Flow<List<CalendarEvent>> {
        val (start, end) = dayBoundsUtcMillis(date, zone)
        return dao.observeBetween(start, end).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getEvent(id: String): CalendarEvent? = dao.getById(id)?.toDomain()

    override suspend fun upsert(event: CalendarEvent) = dao.upsert(event.toEntity())

    override suspend fun delete(id: String) = dao.deleteById(id)
}
