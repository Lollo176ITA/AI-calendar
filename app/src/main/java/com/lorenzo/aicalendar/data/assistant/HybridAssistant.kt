package com.lorenzo.aicalendar.data.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.lorenzo.aicalendar.data.auth.ApiKeyProvider
import com.lorenzo.aicalendar.data.extract.OnDeviceEventExtractor
import com.lorenzo.aicalendar.domain.assistant.AiAssistant
import com.lorenzo.aicalendar.domain.assistant.AssistantContext
import com.lorenzo.aicalendar.domain.assistant.AssistantReply
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.extract.ExtractionInput
import com.lorenzo.aicalendar.domain.model.EventSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Cloud assistant when online + key; otherwise a degraded on-device single-event path. */
class HybridAssistant @Inject constructor(
    private val cloud: OpenRouterAssistant,
    private val onDevice: OnDeviceEventExtractor,
    private val keyProvider: ApiKeyProvider,
    @ApplicationContext private val appContext: Context,
) : AiAssistant {

    override suspend fun respond(
        history: List<ChatMessage>,
        userMessage: String,
        context: AssistantContext,
    ): AssistantReply {
        if (isOnline() && keyProvider.currentKey() != null) {
            runCatching { return cloud.respond(history, userMessage, context) }
                .onFailure { Log.w(TAG, "Cloud assistant failed; using on-device fallback", it) }
        }
        // Offline / failure: try to extract a single event from the message on-device.
        val result = onDevice.extract(
            ExtractionInput(userMessage, context.now, context.zone, EventSource.AI_TEXT),
        )
        val draft = result.draft
        return if (draft.start != null) {
            AssistantReply(
                text = "Sono offline, ma ho preparato «${draft.title}». Controlla i dettagli nel calendario.",
                eventToCreate = draft,
            )
        } else {
            AssistantReply(
                text = "Al momento sono offline e non ho colto una data. Riprova quando hai connessione.",
            )
        }
    }

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val TAG = "HybridAssistant"
    }
}
