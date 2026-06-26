package com.lorenzo.aicalendar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * App database. exportSchema off for the MVP; bumped to v2 to add chat_messages.
 * DatabaseModule uses destructive migration (dev-stage; no migrations to maintain yet).
 */
@Database(entities = [EventEntity::class, ChatMessageEntity::class], version = 4, exportSchema = false)
abstract class AiCalendarDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun chatDao(): ChatDao
}
