package com.lorenzo.aicalendar.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.aicalendar.domain.assistant.RoutineOnboarder
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.chat.ChatRole
import com.lorenzo.aicalendar.domain.profile.ProfileRepository
import com.lorenzo.aicalendar.domain.profile.UserProfile
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
                    profileRepository.save(profile.copy(routine = routine))
                    profileRepository.setOnboardingCompleted(true) // → app root switches to the calendar
                }
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("HTTP", ignoreCase = true) == true ->
                        "Il server dell'assistente non risponde al momento. Riprova tra qualche secondo."
                    e.message?.contains("key", ignoreCase = true) == true ->
                        "L'assistente AI non è configurato in questa build."
                    e.message?.contains("Empty", ignoreCase = true) == true ->
                        "L'assistente ha risposto in modo vuoto. Riprova, magari riformulando la frase."
                    else ->
                        "Ops, qualcosa è andato storto. Riprova tra qualche secondo."
                }
                _messages.update { it + assistantMessage(msg) }
            } finally {
                _sending.value = false
            }
        }
    }

    /** Lets the user finish onboarding without describing the routine now. */
    fun skip() {
        viewModelScope.launch { profileRepository.setOnboardingCompleted(true) }
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
