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

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: String): EventEntity?

    @Upsert
    suspend fun upsert(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)
}
