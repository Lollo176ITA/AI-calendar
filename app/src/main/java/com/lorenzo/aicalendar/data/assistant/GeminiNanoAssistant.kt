package com.lorenzo.aicalendar.data.assistant

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.lorenzo.aicalendar.domain.assistant.AiAssistant
import com.lorenzo.aicalendar.domain.assistant.AssistantContext
import com.lorenzo.aicalendar.domain.assistant.AssistantReply
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.chat.ChatRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device assistant on Gemini Nano (Android AICore) via the ML Kit GenAI Prompt API.
 * Same prompt contract and parsing as the cloud path — requests never leave the phone.
 * Available only on supported devices (Pixel 9+, recent Galaxy S, …); [isReady] gates it
 * and quietly kicks off the one-time model download when the device supports it.
 */
@Singleton
class GeminiNanoAssistant @Inject constructor(
    private val parser: AssistantReplyParser,
) : AiAssistant {

    private val model by lazy { Generation.getClient() }
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadStarted = false

    /** True when the on-device model can answer right now (never throws on unsupported devices). */
    suspend fun isReady(): Boolean = runCatching {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> true
            FeatureStatus.DOWNLOADABLE -> {
                startDownloadOnce()
                false
            }
            else -> false
        }
    }.getOrElse {
        Log.w(TAG, "Gemini Nano unavailable: ${it.message}")
        false
    }

    /** First-use download of the shared on-device model — fire and forget, never blocks a turn. */
    private fun startDownloadOnce() {
        if (downloadStarted) return
        downloadStarted = true
        downloadScope.launch {
            runCatching { model.download().collect { } }
                .onFailure { Log.w(TAG, "Gemini Nano download failed: ${it.message}") }
        }
    }

    override suspend fun respond(
        history: List<ChatMessage>,
        userMessage: String,
        context: AssistantContext,
    ): AssistantReply {
        // The Prompt API takes a single prompt (no chat roles): system prompt + a short
        // transcript tail + the new message, flattened.
        val prompt = buildString {
            append(AssistantPrompts.eventAssistant(context))
            append("\n\nCONVERSAZIONE FINORA:\n")
            history.takeLast(8).forEach {
                append(if (it.role == ChatRole.USER) "Utente: " else "Assistente: ")
                append(it.text)
                append('\n')
            }
            append("Utente: ")
            append(userMessage)
            append("\nAssistente (rispondi SOLO con l'oggetto JSON):")
        }
        val response = model.generateContent(prompt)
        val text = response.candidates.firstOrNull()?.text.orEmpty()
        return parser.parse(text, context.zone)
    }

    private companion object {
        const val TAG = "GeminiNanoAssistant"
    }
}
