package com.lorenzo.aicalendar.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App-level preferences (kept in the same Preferences DataStore as the profile). */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Whether to overlay the device's system-calendar events in the app (requires READ_CALENDAR). */
    val showSystemCalendar: Flow<Boolean> =
        dataStore.data.map { it[SHOW_SYSTEM_CALENDAR] ?: false }

    suspend fun setShowSystemCalendar(enabled: Boolean) {
        dataStore.edit { it[SHOW_SYSTEM_CALENDAR] = enabled }
    }

    /** Whether a final dictation result is sent to the assistant right away. */
    val voiceAutoSend: Flow<Boolean> =
        dataStore.data.map { it[VOICE_AUTO_SEND] ?: true }

    suspend fun setVoiceAutoSend(enabled: Boolean) {
        dataStore.edit { it[VOICE_AUTO_SEND] = enabled }
    }

    /** Whether the assistant reads its replies aloud for voice-initiated messages. */
    val voiceReplies: Flow<Boolean> =
        dataStore.data.map { it[VOICE_REPLIES] ?: true }

    suspend fun setVoiceReplies(enabled: Boolean) {
        dataStore.edit { it[VOICE_REPLIES] = enabled }
    }

    private companion object {
        val SHOW_SYSTEM_CALENDAR = booleanPreferencesKey("show_system_calendar")
        val VOICE_AUTO_SEND = booleanPreferencesKey("voice_auto_send")
        val VOICE_REPLIES = booleanPreferencesKey("voice_replies")
    }
}
