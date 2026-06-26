package com.lorenzo.aicalendar.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A single persisted chat turn (role = "USER" or "ASSISTANT"). */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val role: String,
    val text: String,
    val createdAtEpochMillis: Long,
)
