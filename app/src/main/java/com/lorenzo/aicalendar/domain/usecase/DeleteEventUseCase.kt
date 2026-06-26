package com.lorenzo.aicalendar.domain.usecase

import com.lorenzo.aicalendar.domain.reminder.ReminderScheduler
import com.lorenzo.aicalendar.domain.repository.EventRepository
import javax.inject.Inject

/** Deletes an event and cancels its reminder. */
class DeleteEventUseCase @Inject constructor(
    private val repository: EventRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(eventId: String) {
        scheduler.cancel(eventId)
        repository.delete(eventId)
    }
}
