package com.lorenzo.aicalendar.domain.chat

import java.time.Instant

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val createdAt: Instant,
)
