package com.lorenzo.aicalendar.domain.assistant

import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.profile.UserProfile

/** One routine-onboarding turn: a reply, plus the consolidated routine once it's complete. */
data class RoutineReply(
    val text: String,
    /** Non-null when the assistant has gathered the full weekly routine (ready to save). */
    val routine: String? = null,
)

/**
 * Guides the user, right after the profile step, through a short chat that asks about their
 * weekly routine and returns the consolidated routine when done.
 */
interface RoutineOnboarder {
    suspend fun next(
        history: List<ChatMessage>,
        userMessage: String,
        profile: UserProfile,
    ): RoutineReply
}
