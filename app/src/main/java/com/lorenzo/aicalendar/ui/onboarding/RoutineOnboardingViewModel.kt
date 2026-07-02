package com.lorenzo.aicalendar.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.aicalendar.domain.assistant.AiAssistant
import com.lorenzo.aicalendar.domain.assistant.AssistantAction
import com.lorenzo.aicalendar.domain.assistant.AssistantContext
import com.lorenzo.aicalendar.domain.assistant.RoutineOnboarder
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.chat.ChatRole
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import com.lorenzo.aicalendar.domain.profile.ProfileRepository
import com.lorenzo.aicalendar.domain.profile.UserProfile
import com.lorenzo.aicalendar.domain.usecase.SaveEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RoutineOnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val onboarder: RoutineOnboarder,
    private val assistant: AiAssistant,
    private val saveEvent: SaveEventUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private var profile = UserProfile()

    init {
        viewModelScope.launch {
            profile = profileRepository.profile.first()
            _messages.value = listOf(assistantMessage(greeting()))
        }
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            _messages.update { it + userMessage(message) }
            try {
                val history = _messages.value.dropLast(1)
                val reply = onboarder.next(history, message, profile)
                _messages.update { it + assistantMessage(reply.text) }
                reply.routine?.let { routine ->
                    val updated = profile.copy(routine = routine)
                    profileRepository.save(updated)
                    // The whole point of the interview: the routine must land IN the agenda,
                    // not just in the profile text. Best-effort — a hiccup here must not
                    // block onboarding (the routine text is saved either way).
                    _messages.update { it + assistantMessage("Perfetto! Sto creando i tuoi impegni fissi in agenda…") }
                    runCatching { seedAgendaFromRoutine(updated, routine) }
                    profileRepository.setOnboardingCompleted(true) // → app root switches to the calendar
                }
            } catch (e: Exception) {
                _messages.update { it + assistantMessage("Ops, qualcosa è andato storto. Riprova.") }
            } finally {
                _sending.value = false
            }
        }
    }

    /** Lets the user finish onboarding without describing the routine now. */
    fun skip() {
        viewModelScope.launch { profileRepository.setOnboardingCompleted(true) }
    }

    /**
     * Turns the routine summary into actual recurring agenda events by running it through the
     * same event assistant used by the chat (one CREATE per distinct block, RRULE included).
     */
    private suspend fun seedAgendaFromRoutine(profile: UserProfile, routine: String) {
        val zone = clock.zone
        val now = Instant.now(clock)
        val reply = assistant.respond(
            history = emptyList(),
            userMessage = "Questa è la mia routine settimanale, creala in agenda come eventi ricorrenti: $routine",
            context = AssistantContext(now = now, zone = zone, profile = profile, upcomingEvents = emptyList()),
        )
        val ts = Instant.now(clock)
        for (op in reply.operations) {
            if (op.action != AssistantAction.CREATE) continue
            val draft = op.draft ?: continue
            val start = draft.start ?: continue
            saveEvent(
                CalendarEvent(
                    id = UUID.randomUUID().toString(),
                    title = draft.title,
                    start = start,
                    zone = zone,
                    end = draft.end,
                    allDay = draft.allDay,
                    location = draft.location,
                    reminderOffsetMin = draft.reminderOffsetMin,
                    recurrence = draft.recurrence,
                    source = EventSource.AI_TEXT,
                    createdAt = ts,
                    updatedAt = ts,
                ),
            )
        }
    }

    private fun greeting(): String = buildString {
        append("Ciao")
        profile.firstName.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
        append("! Per costruirti un'agenda su misura, raccontami la tua settimana tipo. ")
        append("Cosa fai di solito nei giorni feriali — sveglia, lavoro o lezioni, impegni fissi?")
    }

    private fun userMessage(text: String) =
        ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, text, Instant.now(clock))

    private fun assistantMessage(text: String) =
        ChatMessage(UUID.randomUUID().toString(), ChatRole.ASSISTANT, text, Instant.now(clock))
}
