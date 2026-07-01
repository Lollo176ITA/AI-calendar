package com.lorenzo.aicalendar.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.aicalendar.data.calendar.SystemCalendarReader
import com.lorenzo.aicalendar.data.settings.SettingsRepository
import com.lorenzo.aicalendar.domain.assistant.AiAssistant
import com.lorenzo.aicalendar.domain.assistant.AssistantAction
import com.lorenzo.aicalendar.domain.assistant.AssistantContext
import com.lorenzo.aicalendar.domain.assistant.AssistantOperation
import com.lorenzo.aicalendar.domain.assistant.AssistantReply
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.chat.ChatRepository
import com.lorenzo.aicalendar.domain.chat.ChatRole
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import com.lorenzo.aicalendar.domain.profile.ProfileRepository
import com.lorenzo.aicalendar.domain.repository.EventRepository
import com.lorenzo.aicalendar.domain.scheduling.SlotFinder
import com.lorenzo.aicalendar.domain.usecase.DeleteEventUseCase
import com.lorenzo.aicalendar.domain.usecase.SaveEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val assistant: AiAssistant,
    private val eventRepository: EventRepository,
    private val saveEvent: SaveEventUseCase,
    private val deleteEvent: DeleteEventUseCase,
    private val profileRepository: ProfileRepository,
    private val settings: SettingsRepository,
    private val systemReader: SystemCalendarReader,
    private val clock: Clock,
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> =
        chatRepository.observeMessages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Replies to read aloud (emitted only for voice-initiated turns, if the setting allows). */
    private val _speakReplies = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val speakReplies: SharedFlow<String> = _speakReplies.asSharedFlow()

    /** Whether a final dictation result should be sent right away (Settings toggle). */
    val voiceAutoSend: StateFlow<Boolean> =
        settings.voiceAutoSend.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun send(text: String, viaVoice: Boolean = false) {
        val message = text.trim()
        if (message.isEmpty() || _sending.value) return

        viewModelScope.launch {
            _sending.value = true
            val now = Instant.now(clock)
            chatRepository.add(ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, message, now))
            try {
                val zone = clock.zone
                val profile = profileRepository.profile.first()
                val upcoming = eventRepository.getUpcomingEvents(now, zone)
                val systemEvents = if (settings.showSystemCalendar.first()) {
                    systemReader.eventsBetween(now, now.plus(60, ChronoUnit.DAYS), zone)
                } else {
                    emptyList()
                }
                val history = chatRepository.observeMessages().first().dropLast(1)

                val reply = assistant.respond(
                    history = history,
                    userMessage = message,
                    context = AssistantContext(now, zone, profile, upcoming, systemEvents),
                )

                val saved = applyOperations(reply, upcoming, zone)
                // Flag each newly created/updated event that overlaps the existing agenda.
                val conflictNote = saved
                    .mapNotNull { event -> conflictWarning(event, upcoming + systemEvents, zone) }
                    .joinToString("")

                chatRepository.add(
                    ChatMessage(
                        UUID.randomUUID().toString(),
                        ChatRole.ASSISTANT,
                        reply.text + conflictNote,
                        Instant.now(clock),
                    ),
                )
                if (viaVoice && settings.voiceReplies.first()) {
                    _speakReplies.tryEmit(reply.text + conflictNote)
                }
            } catch (e: Exception) {
                chatRepository.add(
                    ChatMessage(
                        UUID.randomUUID().toString(),
                        ChatRole.ASSISTANT,
                        "Ops, qualcosa è andato storto. Riprova.",
                        Instant.now(clock),
                    ),
                )
            } finally {
                _sending.value = false
            }
        }
    }

    /**
     * Applies every operation the assistant proposed this turn (a routine description produces
     * several at once). Returns the created/updated events, for conflict checking.
     */
    private suspend fun applyOperations(
        reply: AssistantReply,
        upcoming: List<CalendarEvent>,
        zone: ZoneId,
    ): List<CalendarEvent> = reply.operations.mapNotNull { applyOperation(it, upcoming, zone) }

    /**
     * Applies one operation: create a new event, or update/delete one it referenced.
     * Returns the created/updated event (for conflict checking), or null for delete/no-op.
     */
    private suspend fun applyOperation(
        op: AssistantOperation,
        upcoming: List<CalendarEvent>,
        zone: ZoneId,
    ): CalendarEvent? {
        val draft = op.draft
        val target = op.targetRef
            ?.let { upcoming.take(AssistantContext.MAX_AGENDA).getOrNull(it - 1) }
        val ts = Instant.now(clock)

        return when (op.action) {
            AssistantAction.DELETE -> {
                target?.let { deleteEvent(it.id) }
                null
            }

            AssistantAction.UPDATE -> {
                if (target == null || draft?.start == null) return null
                val existing = eventRepository.getEvent(target.id) ?: target
                existing.copy(
                    title = draft.title,
                    start = draft.start,
                    end = draft.end,
                    allDay = draft.allDay,
                    location = draft.location,
                    recurrence = draft.recurrence ?: existing.recurrence,
                    updatedAt = ts,
                ).also { saveEvent(it) }
            }

            AssistantAction.CREATE -> {
                if (draft?.start == null) return null
                CalendarEvent(
                    id = UUID.randomUUID().toString(),
                    title = draft.title,
                    start = draft.start,
                    zone = zone,
                    end = draft.end,
                    allDay = draft.allDay,
                    location = draft.location,
                    reminderOffsetMin = draft.reminderOffsetMin,
                    recurrence = draft.recurrence,
                    source = EventSource.AI_TEXT,
                    createdAt = ts,
                    updatedAt = ts,
                ).also { saveEvent(it) }
            }
        }
    }

    /**
     * Deterministic conflict check via [SlotFinder]: overlaps are caught in code, reliably,
     * regardless of what the LLM noticed — and the same algorithm proposes the nearest free
     * slots of the same duration as alternatives. Returns a note to append to the reply.
     */
    private fun conflictWarning(
        event: CalendarEvent,
        others: List<CalendarEvent>,
        zone: ZoneId,
    ): String? {
        val clashes = SlotFinder.conflicts(event, others)
        if (clashes.isEmpty()) return null

        val list = clashes.joinToString("; ") { c ->
            "«${c.title}» ${timeFmt.format(c.start.atZone(zone))}–${timeFmt.format(c.effectiveEnd.atZone(zone))}"
        }
        val alternatives = SlotFinder.suggestAlternatives(event, others, zone)
            .joinToString(" oppure ") { slot ->
                "${timeFmt.format(slot.start.atZone(zone))}–${timeFmt.format(slot.end.atZone(zone))}"
            }
        val suggestion = alternatives.takeIf { it.isNotBlank() }
            ?.let { " Se preferisci, quel giorno sei libero: $it." } ?: ""
        return "\n\nAttenzione: si sovrappone con $list.$suggestion"
    }

    private companion object {
        val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
