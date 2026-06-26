package com.lorenzo.aicalendar.data.remote.openrouter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** OpenRouter is OpenAI-compatible; these mirror the chat-completions request/response. */

@Serializable
data class ChatRequest(
    val models: List<String>,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat,
    val provider: ProviderOptions = ProviderOptions(),
    val temperature: Double = 0.1,
)

@Serializable
data class ChatMessage(
    val role: String,
    // Nullable: reasoning models sometimes return null content with the answer in `reasoning`.
    val content: String? = null,
    val reasoning: String? = null,
)

@Serializable
data class ResponseFormat(
    val type: String = "json_schema",
    @SerialName("json_schema") val jsonSchema: JsonSchemaSpec,
)

@Serializable
data class JsonSchemaSpec(
    val name: String,
    val strict: Boolean = true,
    val schema: JsonObject,
)

@Serializable
data class ProviderOptions(
    @SerialName("require_parameters") val requireParameters: Boolean = true,
    @SerialName("data_collection") val dataCollection: String = "deny",
)

@Serializable
data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
data class Choice(val message: ChatMessage)

/** The model's structured answer (content of the assistant message). */
@Serializable
data class ExtractedEvent(
    val title: String,
    val startDateTime: String,
    val endDateTime: String? = null,
    val location: String? = null,
    val allDay: Boolean = false,
)

/** Free models that advertise `structured_outputs`, best Italian first (verified on /models). */
val OPENROUTER_EVENT_MODELS = listOf(
    "qwen/qwen3-next-80b-a3b-instruct:free",
    "openai/gpt-oss-20b:free",
    "google/gemma-4-26b-a4b-it:free",
)

/** Strict json_schema for a CalendarEvent. Nullable fields are unions but still required. */
val EVENT_JSON_SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    put("additionalProperties", false)
    putJsonArray("required") {
        add("title"); add("startDateTime"); add("endDateTime"); add("location"); add("allDay")
    }
    putJsonObject("properties") {
        putJsonObject("title") {
            put("type", "string")
            put("description", "Titolo dell'evento, in italiano")
        }
        putJsonObject("startDateTime") {
            put("type", "string")
            put("description", "Inizio in ISO-8601 locale senza offset, es 2026-06-26T15:00:00")
        }
        putJsonObject("endDateTime") {
            putJsonArray("type") { add("string"); add("null") }
            put("description", "Fine in ISO-8601, oppure null se non indicata")
        }
        putJsonObject("location") {
            putJsonArray("type") { add("string"); add("null") }
            put("description", "Luogo, oppure null")
        }
        putJsonObject("allDay") {
            put("type", "boolean")
            put("description", "true se l'evento non ha un orario preciso")
        }
    }
}
