package com.lorenzo.aicalendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val _draft = MutableStateFlow(UserProfile())
    val draft: StateFlow<UserProfile> = _draft.asStateFlow()

    val showSystemCalendar: StateFlow<Boolean> =
        settings.showSystemCalendar.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val voiceAutoSend: StateFlow<Boolean> =
        settings.voiceAutoSend.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val voiceReplies: StateFlow<Boolean> =
        settings.voiceReplies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        viewModelScope.launch { _draft.value = repository.profile.first() }
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

    fun save() {
        viewModelScope.launch { repository.save(_draft.value) }
    }
}
