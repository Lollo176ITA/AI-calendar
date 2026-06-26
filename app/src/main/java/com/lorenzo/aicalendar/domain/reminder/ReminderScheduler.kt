package com.lorenzo.aicalendar.domain.reminder

import com.lorenzo.aicalendar.domain.model.CalendarEvent

/**
 * Schedules/cancels the OS-level reminder for an event. The MVP implementation uses
 * AlarmManager; the interface keeps the rest of the app independent of that detail.
 */
interface ReminderScheduler {

    /** Arms (or re-arms) the reminder for [event]. No-op if the event has no reminder. */
    fun schedule(event: CalendarEvent)

    /** Cancels any pending reminder for the event with [eventId]. */
    fun cancel(eventId: String)
}
