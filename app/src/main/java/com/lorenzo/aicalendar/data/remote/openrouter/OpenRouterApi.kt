package com.lorenzo.aicalendar.data.remote.openrouter

import android.util.Log
import com.lorenzo.aicalendar.data.auth.ApiKeyProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Thin OpenRouter chat-completions client that returns a structured [ExtractedEvent]. */
@Singleton
class OpenRouterApi @Inject constructor(
    private val client: HttpClient,
    private val keyProvider: ApiKeyProvider,
    private val json: Json,
) {
    private val dayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)

    suspend fun extractEvent(text: String, now: Instant, zone: ZoneId): ExtractedEvent {
        val key = keyProvider.currentKey() ?: error("OpenRouter key not available")
        val today = now.atZone(zone)

        val systemPrompt = buildString {
            append("Sei un assistente che estrae UN evento di agenda dal testo dell'utente. ")
            append("Oggi è ${today.format(dayFormatter)}, fuso orario ${zone.id}. ")
            append("Risolvi le date relative (oggi, domani, dopodomani, venerdì prossimo) rispetto a oggi. ")
            append("Rispondi SOLO con un oggetto JSON, senza testo né code-fence, con ESATTAMENTE queste chiavi: ")
            append("title (string), startDateTime (string ISO-8601 es 2026-06-26T21:00:00), ")
            append("endDateTime (string ISO-8601 o null), location (string o null), allDay (boolean). ")
            append("Se non è indicato un orario preciso, allDay=true e usa 00:00. Se non c'è fine, endDateTime=null.")
        }

        val request = ChatRequest(
            models = OPENROUTER_EVENT_MODELS,
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", text),
            ),
            responseFormat = ResponseFormat(
                jsonSchema = JsonSchemaSpec(name = "calendar_event", schema = EVENT_JSON_SCHEMA),
            ),
        )

        val http = client.post("$BASE_URL/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $key")
            header("HTTP-Referer", APP_REFERER)
            header("X-Title", APP_TITLE)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val bodyText = http.bodyAsText()
        if (!http.status.isSuccess()) {
            Log.w(TAG, "OpenRouter ${http.status}: ${bodyText.take(300)}")
            error("OpenRouter HTTP ${http.status}")
        }

        val response = json.decodeFromString(ChatResponse.serializer(), bodyText)
        val message = response.choices.firstOrNull()?.message
        // Prefer content; some reasoning models leave content empty and answer in `reasoning`.
        val raw = message?.content?.takeIf { it.isNotBlank() }
            ?: message?.reasoning?.takeIf { it.isNotBlank() }
            ?: error("Empty OpenRouter response")
        return parseEvent(sanitize(raw))
    }

    /** Conversational call: sends [messages], returns the assistant's raw JSON content. */
    suspend fun chat(messages: List<ChatMessage>): String = withRetry {
        val key = keyProvider.currentKey() ?: error("OpenRouter key not available")
        val request = ChatJsonRequest(models = OPENROUTER_EVENT_MODELS, messages = messages)

        val http = client.post("$BASE_URL/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $key")
            header("HTTP-Referer", APP_REFERER)
            header("X-Title", APP_TITLE)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val bodyText = http.bodyAsText()
        if (!http.status.isSuccess()) {
            Log.w(TAG, "OpenRouter ${http.status}: ${bodyText.take(300)}")
            error("OpenRouter HTTP ${http.status}")
        }
        val response = json.decodeFromString(ChatResponse.serializer(), bodyText)
        val message = response.choices.firstOrNull()?.message
        val raw = message?.content?.takeIf { it.isNotBlank() }
            ?: message?.reasoning?.takeIf { it.isNotBlank() }
            ?: error("Empty OpenRouter response")
        sanitize(raw)
    }

    /**
     * Retries a call a few times with short backoff. Free OpenRouter models hit per-minute
     * rate limits and occasionally return an empty body; a brief retry usually clears it,
     * sparing the user a spurious "something went wrong".
     */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var last: Throwable? = null
        repeat(BACKOFF_MS.size + 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "OpenRouter attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < BACKOFF_MS.size) delay(BACKOFF_MS[attempt])
            }
        }
        throw last ?: IllegalStateException("OpenRouter call failed")
    }

    /**
     * Free models (Qwen, Gemma, Nemotron) sometimes leak special tokens into their output.
     * Strip them so they never reach the UI or pollute conversation history.
     */
    private fun sanitize(text: String): String =
        text.replace(SPECIAL_TOKENS, "").trim()

    /**
     * Tolerant parse: free models often ignore strict json_schema (wrong key names, code
     * fences, extra prose). Pull out the JSON object and read each field by trying several
     * likely key names, so we still get a usable event.
     */
    private fun parseEvent(raw: String): ExtractedEvent {
        val obj = json.parseToJsonElement(extractJsonObject(raw)).jsonObject

        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
            obj[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }
        fun bool(vararg keys: String): Boolean =
            keys.firstNotNullOfOrNull { key -> obj[key]?.jsonPrimitive?.booleanOrNull } ?: false

        return ExtractedEvent(
            title = str("title", "event", "summary", "titolo", "nome") ?: "Evento",
            startDateTime = str("startDateTime", "datetime", "start", "startDate", "date", "inizio")
                ?: error("No start datetime in model output"),
            endDateTime = str("endDateTime", "end", "endDate", "fine"),
            location = str("location", "venue", "place", "luogo"),
            allDay = bool("allDay", "all_day", "tuttoIlGiorno"),
        )
    }

    /** Strips ``` fences and returns the substring from the first '{' to the last '}'. */
    private fun extractJsonObject(raw: String): String {
        val cleaned = raw.replace(CODE_FENCE, "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start in 0 until end) cleaned.substring(start, end + 1) else cleaned
    }

    private companion object {
        const val TAG = "OpenRouterApi"
        const val BASE_URL = "https://openrouter.ai/api/v1"
        const val APP_REFERER = "https://github.com/lorenzo/ai-calendar"
        const val APP_TITLE = "AI-calendar"
        val CODE_FENCE = Regex("```(?:json)?")

        /** Strips special tokens leaked by free models (Qwen <pad>, Gemma <|endoftext|>, etc.). */
        val SPECIAL_TOKENS = Regex("<\\|?(?:pad|endoftext|eos|bos|sep|cls|unk|mask)\\|?>", RegexOption.IGNORE_CASE)

        /** Backoff before each retry (ms); size also sets the number of extra attempts.
         *  Free models are flaky — three retries absorb most transient rate-limit / empty-body errors. */
        val BACKOFF_MS = longArrayOf(1_500, 3_000, 6_000)
    }
}
