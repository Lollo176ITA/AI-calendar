package com.lorenzo.aicalendar.domain.assistant

import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.extract.EventDraft
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.profile.UserProfile
import java.time.Instant
import java.time.ZoneId

/** Context the assistant reasons over: who the user is and what's already on the agenda. */
data class AssistantContext(
    val now: Instant,
    val zone: ZoneId,
    val profile: UserProfile,
    val upcomingEvents: List<CalendarEvent>,
    /** Read-only events mirrored from the device calendar, for conflict awareness (never edited). */
    val systemEvents: List<CalendarEvent> = emptyList(),
) {
    companion object {
        /** How many agenda events are shown to / referenceable by the model (keeps prompts small). */
        const val MAX_AGENDA = 25
    }
}

/** What the assistant wants to do with an event this turn. */
enum class AssistantAction { CREATE, UPDATE, DELETE }

/**
 * One assistant turn: the natural-language reply plus an optional event operation.
 * [action] says whether to create, update, or delete; [targetRef] is the 1-based index into
 * the agenda shown in context (for update/delete); [eventToCreate] carries the (new) values.
 */
data class AssistantReply(
    val text: String,
    val eventToCreate: EventDraft? = null,
    val action: AssistantAction = AssistantAction.CREATE,
    val targetRef: Int? = null,
)

/**
 * The conversational calendar assistant. Given the conversation so far, the new user message,
 * and the agenda context, it replies naturally and may propose an event to add. Cloud
 * (OpenRouter) when available; a degraded on-device path otherwise.
 */
interface AiAssistant {
    suspend fun respond(
        history: List<ChatMessage>,
        userMessage: String,
        context: AssistantContext,
    ): AssistantReply
}
