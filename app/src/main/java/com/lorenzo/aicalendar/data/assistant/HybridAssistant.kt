package com.lorenzo.aicalendar.data.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.lorenzo.aicalendar.data.auth.ApiKeyProvider
import com.lorenzo.aicalendar.data.extract.OnDeviceEventExtractor
import com.lorenzo.aicalendar.data.settings.SettingsRepository
import com.lorenzo.aicalendar.domain.assistant.AiAssistant
import com.lorenzo.aicalendar.domain.assistant.AssistantContext
import com.lorenzo.aicalendar.domain.assistant.AssistantReply
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.extract.ExtractionInput
import com.lorenzo.aicalendar.domain.model.EventSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Backend chain, in order: OpenRouter cloud (best quality, skipped entirely when the
 * "local-only AI" setting is on) → Gemini Nano on-device (private, offline) → degraded
 * single-event extraction. Every hop is best-effort with an honest reason in the reply.
 */
class HybridAssistant @Inject constructor(
    private val cloud: OpenRouterAssistant,
    private val nano: GeminiNanoAssistant,
    private val onDevice: OnDeviceEventExtractor,
    private val keyProvider: ApiKeyProvider,
    private val settings: SettingsRepository,
    @param:ApplicationContext private val appContext: Context,
) : AiAssistant {

    override suspend fun respond(
        history: List<ChatMessage>,
        userMessage: String,
        context: AssistantContext,
    ): AssistantReply {
        val localOnly = settings.localAiOnly.first()
        val online = isOnline()
        val hasKey = keyProvider.currentKey() != null

        if (!localOnly && online && hasKey) {
            runCatching { return cloud.respond(history, userMessage, context) }
                .onFailure { Log.w(TAG, "Cloud assistant failed; trying on-device", it) }
        }
        if (nano.isReady()) {
            runCatching { return nano.respond(history, userMessage, context) }
                .onFailure { Log.w(TAG, "Gemini Nano failed; using degraded fallback", it) }
        }

        // Fallback: extract a single event on-device. Why we got here drives an honest message
        // (local-only without Nano vs offline vs missing key vs a transient error).
        val reason = when {
            localOnly -> "Modalità solo AI locali attiva, ma l'AI del telefono non è (ancora) disponibile"
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
