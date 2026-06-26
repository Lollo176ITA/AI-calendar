package com.lorenzo.aicalendar.domain.chat

import kotlinx.coroutines.flow.Flow

/** Persisted assistant conversation (single ongoing thread). */
interface ChatRepository {
    fun observeMessages(): Flow<List<ChatMessage>>
    suspend fun add(message: ChatMessage)
    suspend fun clear()
}
