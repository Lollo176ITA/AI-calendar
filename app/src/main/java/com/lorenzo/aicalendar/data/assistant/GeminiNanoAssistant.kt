package com.lorenzo.aicalendar.data.assistant

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observable availability of the on-device model, so the UI can answer "why is the local AI
 * not responding?" — supported devices spend their first session in [Downloading].
 */
sealed interface NanoStatus {
    /** Not queried yet. */
    data object Unknown : NanoStatus

    /** AICore says the feature can't run on this device. */
    data object Unsupported : NanoStatus

    /** Model download pending or running; byte counts appear once AICore reports them. */
    data class Downloading(
        val downloadedBytes: Long? = null,
        val totalBytes: Long? = null,
    ) : NanoStatus

    data object Ready : NanoStatus

    data class Failed(val message: String?) : NanoStatus
}

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

    private val _status = MutableStateFlow<NanoStatus>(NanoStatus.Unknown)

    /** Live availability of the on-device model (shown in Settings). */
    val status: StateFlow<NanoStatus> = _status.asStateFlow()

    /** True when the on-device model can answer right now (never throws on unsupported devices). */
    suspend fun isReady(): Boolean = refreshStatus() == NanoStatus.Ready

    /** Re-queries AICore, updates [status], and kicks off the model download when possible. */
    suspend fun refreshStatus(): NanoStatus {
        val status = runCatching {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> NanoStatus.Ready
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    startDownloadOnce()
                    // Keep whatever progress the download collector already reported.
                    _status.value as? NanoStatus.Downloading ?: NanoStatus.Downloading()
                }
                else -> NanoStatus.Unsupported
            }
        }.getOrElse {
            Log.w(TAG, "Gemini Nano unavailable: ${it.message}")
            NanoStatus.Unsupported
        }
        _status.value = status
        return status
    }

    /** First-use download of the shared on-device model — fire and forget, never blocks a turn. */
    private fun startDownloadOnce() {
        if (downloadStarted) return
        downloadStarted = true
        downloadScope.launch {
            var total: Long? = null
            runCatching {
                model.download().collect { event ->
                    when (event) {
                        is DownloadStatus.DownloadStarted -> {
                            total = event.bytesToDownload.takeIf { it > 0 }
                            _status.value = NanoStatus.Downloading(0, total)
                        }
                        is DownloadStatus.DownloadProgress ->
                            _status.value = NanoStatus.Downloading(event.totalBytesDownloaded, total)
                        is DownloadStatus.DownloadCompleted -> _status.value = NanoStatus.Ready
                        is DownloadStatus.DownloadFailed -> {
                            Log.w(TAG, "Gemini Nano download failed", event.e)
                            _status.value = NanoStatus.Failed(event.e.message)
                        }
                        else -> Unit
                    }
                }
            }.onFailure {
                Log.w(TAG, "Gemini Nano download failed: ${it.message}")
                _status.value = NanoStatus.Failed(it.message)
            }
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
