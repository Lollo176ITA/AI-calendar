package com.lorenzo.aicalendar.data.reminder

import com.lorenzo.aicalendar.domain.model.CalendarEvent
import java.time.Duration

/**
 * Epoch-millis at which to fire the reminder for [event] — its start minus the
 * `reminderOffsetMin`. Returns null when the event has no reminder configured.
 */
fun reminderTriggerMillis(event: CalendarEvent): Long? {
    val offsetMin = event.reminderOffsetMin ?: return null
    return event.start.minus(Duration.ofMinutes(offsetMin.toLong())).toEpochMilli()
}
