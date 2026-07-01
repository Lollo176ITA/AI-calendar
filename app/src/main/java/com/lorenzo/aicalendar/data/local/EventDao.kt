package com.lorenzo.aicalendar.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    /** Rows whose start falls in `[startMillis, endMillis)`, oldest first. */
    @Query(
        "SELECT * FROM events WHERE startEpochMillis >= :startMillis AND startEpochMillis < :endMillis " +
            "ORDER BY startEpochMillis ASC",
    )
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<EventEntity>>

    /** All events starting at or after [fromMillis] (used to re-arm reminders after reboot). */
    @Query("SELECT * FROM events WHERE startEpochMillis >= :fromMillis ORDER BY startEpochMillis ASC")
    suspend fun getStartingAtOrAfter(fromMillis: Long): List<EventEntity>

    /** Recurring "master" events (expanded into occurrences in code). */
    @Query("SELECT * FROM events WHERE recurrenceRule IS NOT NULL")
    fun observeRecurring(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE recurrenceRule IS NOT NULL")
    suspend fun getRecurring(): List<EventEntity>

    @Query("SELECT * FROM events WHERE title LIKE '%' || :query || '%' ORDER BY startEpochMillis DESC")
    fun search(query: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: String): EventEntity?

    /** System-calendar ids of events we mirrored (used to hide them from the system overlay). */
    @Query("SELECT systemEventId FROM events WHERE systemEventId IS NOT NULL")
    suspend fun getMirroredSystemIds(): List<Long>

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)
}
