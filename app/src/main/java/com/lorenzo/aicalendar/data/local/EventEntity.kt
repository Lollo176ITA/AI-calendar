package com.lorenzo.aicalendar.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for an agenda event. Stored with primitive columns only — times as UTC
 * epoch-millis plus a zone id string, [source] as an enum name — so persistence stays
 * decoupled from the domain model. Mapping lives in EventMapper.
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startEpochMillis: Long,
    val zoneId: String,
    val endEpochMillis: Long?,
    val allDay: Boolean,
    val location: String?,
    val notes: String?,
    val source: String,
    val reminderOffsetMin: Int?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
