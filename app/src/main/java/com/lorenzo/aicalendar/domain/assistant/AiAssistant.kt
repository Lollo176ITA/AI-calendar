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
        /**
         * How many agenda events are shown to / referenceable by the model. Keeps prompts small,
         * but not too small: with a real phone calendar (trains, birthdays…) a low cap silently
         * truncates the context and the assistant "doesn't see" events the user asks about.
         */
        const val MAX_AGENDA = 40
    }
}

/** What the assistant wants to do with an event this turn. */
enum class AssistantAction { CREATE, UPDATE, DELETE }

/**
 * A single event operation proposed this turn. [action] says whether to create, update, or
 * delete; [targetRef] is the 1-based index into the agenda shown in context (for update/delete);
 * [draft] carries the (new) values.
 */
data class AssistantOperation(
    val action: AssistantAction = AssistantAction.CREATE,
    val draft: EventDraft? = null,
    val targetRef: Int? = null,
)

/**
 * One assistant turn: the natural-language reply plus zero or more event [operations].
 * A turn can carry several operations at once — e.g. describing a whole weekly routine creates
 * one recurring event per distinct block (work, volunteering, …) in a single reply.
 */
data class AssistantReply(
    val text: String,
    val operations: List<AssistantOperation> = emptyList(),
) {
    /** Convenience for the single-event paths (on-device fallback, simple create/update/delete). */
    constructor(
        text: String,
        eventToCreate: EventDraft?,
        action: AssistantAction = AssistantAction.CREATE,
        targetRef: Int? = null,
    ) : this(
        text,
        if (eventToCreate != null || targetRef != null) {
            listOf(AssistantOperation(action, eventToCreate, targetRef))
        } else {
            emptyList()
        },
    )
}

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
