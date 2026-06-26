package com.lorenzo.aicalendar.data.chat

import com.lorenzo.aicalendar.data.local.ChatDao
import com.lorenzo.aicalendar.data.local.ChatMessageEntity
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.chat.ChatRepository
import com.lorenzo.aicalendar.domain.chat.ChatRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class RoomChatRepository @Inject constructor(
    private val dao: ChatDao,
) : ChatRepository {

    override fun observeMessages(): Flow<List<ChatMessage>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(message: ChatMessage) = dao.insert(message.toEntity())

    override suspend fun clear() = dao.clear()

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id,
        role = runCatching { ChatRole.valueOf(role) }.getOrDefault(ChatRole.ASSISTANT),
        text = text,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    )

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        id = id,
        role = role.name,
        text = text,
        createdAtEpochMillis = createdAt.toEpochMilli(),
    )
}
