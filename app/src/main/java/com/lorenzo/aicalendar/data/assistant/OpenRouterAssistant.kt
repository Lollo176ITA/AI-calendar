package com.lorenzo.aicalendar.data.assistant

import com.lorenzo.aicalendar.data.remote.openrouter.OpenRouterApi
import com.lorenzo.aicalendar.domain.assistant.AiAssistant
import com.lorenzo.aicalendar.domain.assistant.AssistantContext
import com.lorenzo.aicalendar.domain.assistant.AssistantReply
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.chat.ChatRole
import com.lorenzo.aicalendar.domain.extract.EventDraft
import com.lorenzo.aicalendar.domain.model.EventSource
import com.lorenzo.aicalendar.domain.model.Frequency
import com.lorenzo.aicalendar.domain.model.Recurrence
import com.lorenzo.aicalendar.domain.profile.Profession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.lorenzo.aicalendar.data.remote.openrouter.ChatMessage as ApiMessage
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Cloud conversational assistant via OpenRouter (JSON {reply, event}). */
class OpenRouterAssistant @Inject constructor(
    private val api: OpenRouterApi,
    private val json: Json,
) : AiAssistant {

    private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)
    private val eventFmt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ITALIAN)
    private val codeFence = Regex("```(?:json)?")

    override suspend fun respond(
        history: List<ChatMessage>,
        userMessage: String,
        context: AssistantContext,
    ): AssistantReply {
        val messages = buildList {
            add(ApiMessage(role = "system", content = systemPrompt(context)))
            history.takeLast(20).forEach {
                add(ApiMessage(role = if (it.role == ChatRole.USER) "user" else "assistant", content = it.text))
            }
            add(ApiMessage(role = "user", content = userMessage))
        }
        val raw = api.chat(messages)
        return parseReply(raw, context.zone)
    }

    private fun systemPrompt(ctx: AssistantContext): String {
        val today = ctx.now.atZone(ctx.zone)
        val name = ctx.profile.firstName.takeIf { it.isNotBlank() }
        val agenda = ctx.upcomingEvents.take(25).joinToString("\n") { e ->
            "- ${e.start.atZone(ctx.zone).format(eventFmt)} ${e.title}" +
                (e.location?.takeIf { it.isNotBlank() }?.let { " (@$it)" } ?: "")
        }.ifBlank { "(nessun evento)" }

        return buildString {
            append("Sei l'assistente del calendario")
            name?.let { append(" di $it") }
            append(". Oggi è ${today.format(dayFmt)}, fuso ${ctx.zone.id}. ")
            if (ctx.profile.profession != Profession.UNSPECIFIED) {
                append("L'utente è ${ctx.profile.profession.name.lowercase()}. ")
            }
            append("\n\nEventi già in agenda (prossimi):\n").append(agenda)
            append("\n\nConversa in italiano, naturale e conciso. ")
            append("Se l'utente vuole aggiungere o spostare un evento, mettilo nel campo \"event\". ")
            append("Risolvi le date relative rispetto a oggi (ISO-8601, es 2026-06-26T15:00:00). ")
            append("Segnala eventuali sovrapposizioni con gli eventi esistenti; se l'intento è chiaro crea pure l'evento, altrimenti chiedi conferma e lascia \"event\" a null. ")
            append("Se l'evento è ricorrente (ogni giorno/settimana/mese/anno), imposta \"recurrence\" con \"frequency\" tra daily|weekly|monthly|yearly e \"interval\" (intero, default 1); altrimenti \"recurrence\": null. ")
            append("\n\nRispondi SEMPRE e SOLO con un oggetto JSON, niente altro:\n")
            append("{\"reply\": \"<testo per l'utente>\", \"event\": {\"title\": \"...\", \"startDateTime\": \"ISO-8601\", \"endDateTime\": \"ISO-8601 o null\", \"location\": \"string o null\", \"allDay\": false, \"recurrence\": {\"frequency\": \"yearly\", \"interval\": 1} oppure null} oppure null}")
        }
    }

    private fun parseReply(raw: String, zone: ZoneId): AssistantReply {
        val obj = runCatching { json.parseToJsonElement(extractJson(raw)).jsonObject }.getOrNull()
            ?: return AssistantReply(raw.trim().ifBlank { "Ok." })

        val reply = obj.str("reply", "text", "message") ?: "Ok."
        val eventObj = obj["event"]?.takeIf { it !is JsonNull } as? JsonObject

        val draft = eventObj?.let { e ->
            val start = e.str("startDateTime", "datetime", "start")?.let { parseInstant(it, zone) }
            if (start == null) {
                null
            } else {
                EventDraft(
                    title = e.str("title", "event") ?: "Evento",
                    start = start,
                    end = e.str("endDateTime", "end")?.let { parseInstant(it, zone) },
                    allDay = e["allDay"]?.jsonPrimitive?.booleanOrNull ?: false,
                    location = e.str("location"),
                    recurrence = parseRecurrence(e["recurrence"]),
                    source = EventSource.AI_TEXT,
                )
            }
        }
        return AssistantReply(reply, draft)
    }

    private fun parseRecurrence(element: JsonElement?): Recurrence? {
        val obj = element?.takeIf { it !is JsonNull } as? JsonObject ?: return null
        val freq = obj.str("frequency", "freq", "type")?.uppercase()
            ?.let { runCatching { Frequency.valueOf(it) }.getOrNull() } ?: return null
        val interval = (obj["interval"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1)
        return Recurrence(freq, interval)
    }

    private fun JsonObject.str(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun parseInstant(value: String, zone: ZoneId): Instant? = runCatching {
        LocalDateTime.parse(value).atZone(zone).toInstant()
    }.recoverCatching {
        OffsetDateTime.parse(value).toInstant()
    }.getOrNull()

    private fun extractJson(raw: String): String {
        val cleaned = raw.replace(codeFence, "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start in 0 until end) cleaned.substring(start, end + 1) else cleaned
    }
}
