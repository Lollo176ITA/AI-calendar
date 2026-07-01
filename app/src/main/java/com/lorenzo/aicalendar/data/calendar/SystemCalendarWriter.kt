package com.lorenzo.aicalendar.data.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write side of the system-calendar bridge: creates/updates/deletes events in a device
 * calendar (Google/Samsung/…) via CalendarContract, so app events become real events that
 * sync everywhere — no OAuth or Google API needed. Everything is best-effort: a missing
 * WRITE_CALENDAR permission or provider error never crashes the app, it just skips the mirror.
 */
@Singleton
class SystemCalendarWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** A device calendar the user is allowed to create events in. */
    data class WritableCalendar(val id: Long, val name: String, val account: String)

    /** Device calendars with contributor-or-better access (empty without READ_CALENDAR). */
    suspend fun writableCalendars(): List<WritableCalendar> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                ),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
                    "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR} AND " +
                    "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            WritableCalendar(
                                id = c.getLong(0),
                                name = c.getString(1) ?: "(senza nome)",
                                account = c.getString(2) ?: "",
                            ),
                        )
                    }
                }
            }.orEmpty()
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_CALENDAR not granted; no writable calendars")
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Calendar list query failed: ${e.message}")
            emptyList()
        }
    }

    /** Inserts [event] into [calendarId]; returns the new system event id, or null on failure. */
    suspend fun insert(calendarId: Long, event: CalendarEvent): Long? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver
                .insert(CalendarContract.Events.CONTENT_URI, event.toContentValues(calendarId))
                ?.let(ContentUris::parseId)
        } catch (e: Exception) {
            Log.w(TAG, "System calendar insert failed: ${e.message}")
            null
        }
    }

    /** Updates the mirrored copy; false if the row is gone (e.g. deleted in Google Calendar). */
    suspend fun update(systemEventId: Long, event: CalendarEvent): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, systemEventId)
            context.contentResolver.update(uri, event.toContentValues(calendarId = null), null, null) > 0
        } catch (e: Exception) {
            Log.w(TAG, "System calendar update failed: ${e.message}")
            false
        }
    }

    suspend fun delete(systemEventId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, systemEventId)
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "System calendar delete failed: ${e.message}")
            }
        }
    }

    private fun CalendarEvent.toContentValues(calendarId: Long?) = ContentValues().apply {
        calendarId?.let { put(CalendarContract.Events.CALENDAR_ID, it) }
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DTSTART, start.toEpochMilli())
        // The provider requires all-day events to be expressed in UTC.
        put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else zone.id)
        put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_LOCATION, location)
        put(CalendarContract.Events.DESCRIPTION, notes)
        if (recurrence != null) {
            // Recurring rows must carry RRULE + DURATION instead of DTEND (CalendarContract rule).
            put(CalendarContract.Events.RRULE, recurrence.rrule)
            put(CalendarContract.Events.DURATION, rfc5545Duration())
            putNull(CalendarContract.Events.DTEND)
        } else {
            put(CalendarContract.Events.DTEND, effectiveEnd.toEpochMilli())
            putNull(CalendarContract.Events.RRULE)
            putNull(CalendarContract.Events.DURATION)
        }
    }

    private fun CalendarEvent.rfc5545Duration(): String {
        val seconds = (effectiveEnd.toEpochMilli() - start.toEpochMilli()) / 1000
        return "P${seconds.coerceAtLeast(0)}S"
    }

    private companion object {
        const val TAG = "SystemCalendarWriter"
    }
}
