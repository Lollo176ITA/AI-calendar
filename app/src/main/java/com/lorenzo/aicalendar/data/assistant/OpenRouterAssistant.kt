package com.lorenzo.aicalendar.data.assistant

import com.lorenzo.aicalendar.data.remote.openrouter.OpenRouterApi
import com.lorenzo.aicalendar.domain.assistant.AiAssistant
import com.lorenzo.aicalendar.domain.assistant.AssistantAction
import com.lorenzo.aicalendar.domain.assistant.AssistantContext
import com.lorenzo.aicalendar.domain.assistant.AssistantOperation
import com.lorenzo.aicalendar.domain.assistant.AssistantReply
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.chat.ChatRole
import com.lorenzo.aicalendar.domain.extract.EventDraft
import com.lorenzo.aicalendar.domain.model.EventSource
import com.lorenzo.aicalendar.domain.model.Recurrence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import javax.inject.Inject

/** Cloud conversational assistant via OpenRouter (JSON {reply, event}). */
class OpenRouterAssistant @Inject constructor(
    private val api: OpenRouterApi,
    private val json: Json,
) : AiAssistant {

    private val codeFence = Regex("```(?:json)?")

    override suspend fun respond(
        history: List<ChatMessage>,
        userMessage: String,
        context: AssistantContext,
    ): AssistantReply {
        val messages = buildList {
            add(ApiMessage(role = "system", content = AssistantPrompts.eventAssistant(context)))
            history.takeLast(20).forEach {
                add(ApiMessage(role = if (it.role == ChatRole.USER) "user" else "assistant", content = it.text))
            }
            add(ApiMessage(role = "user", content = userMessage))
        }
        val raw = api.chat(messages)
        return parseReply(raw, context.zone)
    }

    /**
     * Safety net for a common model mistake: FREQ=MONTHLY with a bare BYDAY (e.g. "BYDAY=SA",
     * which means *every* Saturday) is rewritten to the Nth-weekday derived from [start]
     * (e.g. 27 Jun = 4th Saturday → "BYDAY=4SA"), so "un sabato al mese" really repeats monthly.
     */
    private fun fixMonthlyByDay(rrule: String, start: Instant, zone: ZoneId): String {
        if (!rrule.contains("FREQ=MONTHLY", ignoreCase = true)) return rrule
        if (rrule.contains("BYSETPOS", ignoreCase = true) || rrule.contains("BYMONTHDAY", ignoreCase = true)) return rrule
        val match = Regex("BYDAY=([A-Za-z,+\\-0-9]+)", RegexOption.IGNORE_CASE).find(rrule) ?: return rrule
        val value = match.groupValues[1]
        if (value.any { it.isDigit() }) return rrule // already carries an ordinal
        val day = value.trim().uppercase()
        if (day !in WEEKDAY_CODES) return rrule // only a single bare weekday
        val ordinal = (start.atZone(zone).dayOfMonth - 1) / 7 + 1
        return rrule.replace(match.value, "BYDAY=$ordinal$day")
    }

    private fun parseReply(raw: String, zone: ZoneId): AssistantReply {
        val obj = runCatching { json.parseToJsonElement(extractJson(raw)).jsonObject }.getOrNull()
            ?: return AssistantReply(raw.trim().ifBlank { "Ok." })

        val reply = obj.str("reply", "text", "message") ?: "Ok."

        // Accept both the multi-event field ("events": [...]) and the single-event field
        // ("event": {...}); a routine description maps to several recurring events at once.
        val eventObjs: List<JsonObject> = buildList {
            (obj["events"] as? JsonArray)?.forEach { el -> (el as? JsonObject)?.let { add(it) } }
            (obj["event"]?.takeIf { it !is JsonNull } as? JsonObject)?.let { add(it) }
        }

        val operations = eventObjs.mapNotNull { parseOperation(it, zone) }
        return AssistantReply(reply, operations)
    }

    /** Turns one event JSON object into an [AssistantOperation], or null if it carries no usable op. */
    private fun parseOperation(e: JsonObject, zone: ZoneId): AssistantOperation? {
        val action = when (e.str("action")?.lowercase()) {
            "update", "modify", "move", "sposta", "modifica" -> AssistantAction.UPDATE
            "delete", "remove", "cancel", "elimina", "cancella" -> AssistantAction.DELETE
            else -> AssistantAction.CREATE
        }
        val ref = e["ref"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull

        val start = e.str("startDateTime", "datetime", "start")?.let { parseInstant(it, zone) }
        val draft = start?.let {
            val recurrence = parseRecurrence(e["recurrence"])
                ?.let { r -> r.copy(rrule = fixMonthlyByDay(r.rrule, start, zone)) }
            EventDraft(
                title = e.str("title", "event") ?: "Evento",
                start = start,
                end = e.str("endDateTime", "end")?.let { v -> parseInstant(v, zone) },
                allDay = e["allDay"]?.jsonPrimitive?.booleanOrNull ?: false,
                location = e.str("location"),
                recurrence = recurrence,
                source = EventSource.AI_TEXT,
            )
        }
        // A delete only needs a ref; create/update need a draft. Skip empty objects.
        return when {
            action == AssistantAction.DELETE && ref != null -> AssistantOperation(action, null, ref)
            draft != null -> AssistantOperation(action, draft, ref)
            else -> null
        }
    }

    private fun parseRecurrence(element: JsonElement?): Recurrence? {
        val obj = element?.takeIf { it !is JsonNull } as? JsonObject ?: return null
        val rrule = obj.str("rrule", "rule")
            ?.trim()?.removePrefix("RRULE:")?.removePrefix("rrule:")?.trim()
            ?.takeIf { it.contains("FREQ=", ignoreCase = true) }
            ?: return null
        val label = obj.str("label", "description", "summary") ?: rrule
        return Recurrence(rrule, label)
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

    private companion object {
        val WEEKDAY_CODES = setOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    }
}
