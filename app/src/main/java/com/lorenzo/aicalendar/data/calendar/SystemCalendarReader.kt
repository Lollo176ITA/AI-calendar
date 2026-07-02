package com.lorenzo.aicalendar.data.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import com.lorenzo.aicalendar.data.local.EventDao
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only bridge to the device's system calendars (Google/Samsung/etc.) via CalendarContract,
 * so the app lives alongside the existing ecosystem instead of being standalone. Returns already
 * expanded [CalendarContract.Instances], mapped to read-only [CalendarEvent]s ([EventSource.SYSTEM]).
 */
@Singleton
class SystemCalendarReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val eventDao: EventDao,
) {
    /**
     * System-calendar events overlapping [from, to]. Returns empty if READ_CALENDAR is not granted
     * (we never crash on a missing permission — the overlay is simply absent).
     */
    suspend fun eventsBetween(from: Instant, to: Instant, zone: ZoneId): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            // Events we mirrored to the device calendar are already shown as app events —
            // skip them here so they don't appear twice.
            val mirrored = eventDao.getMirroredSystemIds().toSet()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
                ContentUris.appendId(this, from.toEpochMilli())
                ContentUris.appendId(this, to.toEpochMilli())
            }.build()

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            )

            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                    val titleCol = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                    val beginCol = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                    val endCol = c.getColumnIndexOrThrow(CalendarContract.Instances.END)
                    val locCol = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                    val allDayCol = c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                    val calCol = c.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

                    buildList {
                        while (c.moveToNext()) {
                            if (c.getLong(idCol) in mirrored) continue
                            val begin = c.getLong(beginCol)
                            val end = c.getLong(endCol)
                            val start = Instant.ofEpochMilli(begin)
                            add(
                                CalendarEvent(
                                    id = "sys:${c.getLong(idCol)}@$begin",
                                    title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "(senza titolo)",
                                    start = start,
                                    zone = zone,
                                    end = if (end > begin) Instant.ofEpochMilli(end) else null,
                                    allDay = c.getInt(allDayCol) != 0,
                                    location = c.getString(locCol)?.takeIf { it.isNotBlank() },
                                    notes = c.getString(calCol)?.takeIf { it.isNotBlank() },
                                    source = EventSource.SYSTEM,
                                    reminderOffsetMin = null,
                                    recurrence = null,
                                    createdAt = start,
                                    updatedAt = start,
                                ),
                            )
                        }
                    }
                }.orEmpty()
            } catch (e: SecurityException) {
                Log.w(TAG, "READ_CALENDAR not granted; skipping system overlay")
                emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "System calendar query failed: ${e.message}")
                emptyList()
            }
        }

    private companion object {
        const val TAG = "SystemCalendarReader"
    }
}
