package com.lorenzo.aicalendar.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventDaoTest {

    private lateinit var db: AiCalendarDatabase
    private lateinit var dao: EventDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AiCalendarDatabase::class.java,
        ).build()
        dao = db.eventDao()
    }

    @After
    fun teardown() = db.close()

    private fun event(id: String, startMillis: Long) = EventEntity(
        id = id,
        title = "event-$id",
        startEpochMillis = startMillis,
        zoneId = "Europe/Rome",
        endEpochMillis = null,
        allDay = false,
        location = null,
        notes = null,
        source = "MANUAL",
        reminderOffsetMin = null,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )

    @Test
    fun observeBetween_returnsOnlyEventsInRange_orderedByStart() = runTest {
        // Query window is [1000, 2000): start inclusive, end exclusive.
        dao.upsert(event("before", 999))
        dao.upsert(event("b", 1500))
        dao.upsert(event("a", 1000)) // inclusive lower bound
        dao.upsert(event("onEnd", 2000)) // exclusive upper bound → excluded
        dao.upsert(event("after", 2500))

        val ids = dao.observeBetween(1000, 2000).first().map { it.id }

        assertEquals(listOf("a", "b"), ids)
    }

    @Test
    fun upsert_replacesRowWithSameId() = runTest {
        dao.upsert(event("x", 1000))
        dao.upsert(event("x", 1500))

        assertEquals(1500L, dao.getById("x")?.startEpochMillis)
    }

    @Test
    fun deleteById_removesRow() = runTest {
        dao.upsert(event("x", 1000))
        dao.deleteById("x")

        assertNull(dao.getById("x"))
    }
}
