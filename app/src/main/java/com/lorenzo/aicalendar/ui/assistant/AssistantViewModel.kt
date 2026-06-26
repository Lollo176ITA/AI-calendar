package com.lorenzo.aicalendar.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.aicalendar.domain.assistant.AiAssistant
import com.lorenzo.aicalendar.domain.assistant.AssistantContext
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.chat.ChatRepository
import com.lorenzo.aicalendar.domain.chat.ChatRole
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import com.lorenzo.aicalendar.domain.profile.ProfileRepository
import com.lorenzo.aicalendar.domain.repository.EventRepository
import com.lorenzo.aicalendar.domain.usecase.SaveEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val assistant: AiAssistant,
    private val eventRepository: EventRepository,
    private val saveEvent: SaveEventUseCase,
    private val profileRepository: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> =
        chatRepository.observeMessages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _sending.value) return

        viewModelScope.launch {
            _sending.value = true
            val now = Instant.now(clock)
            chatRepository.add(ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, message, now))
            try {
                val zone = clock.zone
                val profile = profileRepository.profile.first()
                val upcoming = eventRepository.getUpcomingEvents(now)
                val history = chatRepository.observeMessages().first().dropLast(1)

                val reply = assistant.respond(
                    history = history,
                    userMessage = message,
                    context = AssistantContext(now, zone, profile, upcoming),
                )

                reply.eventToCreate?.start?.let { start ->
                    val draft = reply.eventToCreate!!
                    val ts = Instant.now(clock)
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
                            source = EventSource.AI_TEXT,
                            createdAt = ts,
                            updatedAt = ts,
                        ),
                    )
                }

                chatRepository.add(
                    ChatMessage(UUID.randomUUID().toString(), ChatRole.ASSISTANT, reply.text, Instant.now(clock)),
                )
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
}
