package com.lorenzo.aicalendar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * App database. exportSchema is off for the MVP; switch it on with a schema directory
 * when we introduce migrations (post-MVP).
 */
@Database(entities = [EventEntity::class], version = 1, exportSchema = false)
abstract class AiCalendarDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}
