package com.lorenzo.aicalendar.domain.usecase

import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.reminder.ReminderScheduler
import com.lorenzo.aicalendar.domain.repository.EventRepository
import javax.inject.Inject

/** Persists an event and (re-)arms its reminder as one atomic action for callers. */
class SaveEventUseCase @Inject constructor(
    private val repository: EventRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(event: CalendarEvent) {
        repository.upsert(event)
        scheduler.schedule(event)
    }
}
