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
        val online = isOnline()
        val hasKey = keyProvider.currentKey() != null
        if (online && hasKey) {
            runCatching { return cloud.respond(history, userMessage, context) }
                .onFailure { Log.w(TAG, "Cloud assistant failed; using on-device fallback", it) }
        }

        // Fallback: extract a single event on-device. Why we got here drives an honest message
        // (genuinely offline vs missing key vs a transient cloud error — never a bare "offline").
        val reason = when {
            !online -> "Sembri offline"
            !hasKey -> "L'assistente AI non è configurato in questa build"
            else -> "Ho avuto un problema a contattare l'assistente"
        }
        val draft = runCatching {
            onDevice.extract(ExtractionInput(userMessage, context.now, context.zone, EventSource.AI_TEXT)).draft
        }.getOrNull()

        return when {
            draft?.start != null -> AssistantReply(
                text = "$reason, ma ho preparato «${draft.title}» dal tuo messaggio: controlla i dettagli nel calendario.",
                eventToCreate = draft,
            )
            else -> AssistantReply(
                text = "$reason. Riprova tra poco" +
                    if (!hasKey) " (serve la chiave OpenRouter nella build)." else ".",
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
