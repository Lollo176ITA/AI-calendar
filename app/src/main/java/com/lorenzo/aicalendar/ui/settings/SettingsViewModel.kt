package com.lorenzo.aicalendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.aicalendar.data.assistant.GeminiNanoAssistant
import com.lorenzo.aicalendar.data.assistant.NanoStatus
import com.lorenzo.aicalendar.data.calendar.SystemCalendarWriter
import com.lorenzo.aicalendar.data.settings.SettingsRepository
import com.lorenzo.aicalendar.domain.profile.ProfileRepository
import com.lorenzo.aicalendar.domain.profile.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val settings: SettingsRepository,
    private val calendarWriter: SystemCalendarWriter,
    private val nano: GeminiNanoAssistant,
) : ViewModel() {

    private val _draft = MutableStateFlow(UserProfile())
    val draft: StateFlow<UserProfile> = _draft.asStateFlow()

    val showSystemCalendar: StateFlow<Boolean> =
        settings.showSystemCalendar.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val voiceAutoSend: StateFlow<Boolean> =
        settings.voiceAutoSend.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val voiceReplies: StateFlow<Boolean> =
        settings.voiceReplies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val localAiOnly: StateFlow<Boolean> =
        settings.localAiOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val syncToSystemCalendar: StateFlow<Boolean> =
        settings.syncToSystemCalendar.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val systemCalendarId: StateFlow<Long?> =
        settings.systemCalendarId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _writableCalendars = MutableStateFlow<List<SystemCalendarWriter.WritableCalendar>>(emptyList())
    val writableCalendars: StateFlow<List<SystemCalendarWriter.WritableCalendar>> =
        _writableCalendars.asStateFlow()

    /** Live Gemini Nano availability (unsupported / downloading with progress / ready). */
    val nanoStatus: StateFlow<NanoStatus> = nano.status

    init {
        viewModelScope.launch { _draft.value = repository.profile.first() }
        viewModelScope.launch { nano.refreshStatus() }
        refreshWritableCalendars()
    }

    /** Loads the device calendars we can write to (empty without the permission). */
    fun refreshWritableCalendars() {
        viewModelScope.launch {
            val calendars = calendarWriter.writableCalendars()
            _writableCalendars.value = calendars
            // First activation: default to the primary (first) calendar.
            if (settings.systemCalendarId.first() == null) {
                calendars.firstOrNull()?.let { settings.setSystemCalendarId(it.id) }
            }
        }
    }

    fun update(profile: UserProfile) {
        _draft.value = profile
    }

    fun setShowSystemCalendar(enabled: Boolean) {
        viewModelScope.launch { settings.setShowSystemCalendar(enabled) }
    }

    fun setVoiceAutoSend(enabled: Boolean) {
        viewModelScope.launch { settings.setVoiceAutoSend(enabled) }
    }

    fun setVoiceReplies(enabled: Boolean) {
        viewModelScope.launch { settings.setVoiceReplies(enabled) }
    }

    fun setLocalAiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settings.setLocalAiOnly(enabled)
            if (enabled) nano.refreshStatus()
        }
    }

    fun setSyncToSystemCalendar(enabled: Boolean) {
        viewModelScope.launch {
            settings.setSyncToSystemCalendar(enabled)
            if (enabled) refreshWritableCalendars()
        }
    }

    fun setSystemCalendarId(id: Long) {
        viewModelScope.launch { settings.setSystemCalendarId(id) }
    }

    fun save() {
        viewModelScope.launch { repository.save(_draft.value) }
    }
}
