package com.lorenzo.aicalendar.data.calendar

import com.lorenzo.aicalendar.data.settings.SettingsRepository
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides when an app event gets mirrored to the device calendar: only if the user turned
 * the sync on and picked a target calendar. The repository calls [sync]/[delete] around
 * every save/delete and persists the returned system event id next to the Room row.
 */
@Singleton
class SystemCalendarMirror @Inject constructor(
    private val writer: SystemCalendarWriter,
    private val settings: SettingsRepository,
) {
    /**
     * Brings the mirrored copy in line with [event]. Returns the system event id now associated
     * with it: the existing one after an update, a fresh one after an insert (also when the row
     * was deleted from Google Calendar in the meantime), or null when nothing is mirrored.
     */
    suspend fun sync(event: CalendarEvent, existingSystemId: Long?): Long? {
        // System events are already the device's own rows — never mirror them back.
        if (event.source == EventSource.SYSTEM) return existingSystemId
        if (!settings.syncToSystemCalendar.first()) return existingSystemId
        val calendarId = settings.systemCalendarId.first() ?: return existingSystemId

        if (existingSystemId != null && writer.update(existingSystemId, event)) return existingSystemId
        return writer.insert(calendarId, event)
    }

    /** Removes the mirrored copy (we created it, so we own its lifecycle). */
    suspend fun delete(systemEventId: Long?) {
        if (systemEventId != null) writer.delete(systemEventId)
    }
}
