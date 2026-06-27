package com.lorenzo.aicalendar.data.assistant

import com.lorenzo.aicalendar.data.remote.openrouter.OpenRouterApi
import com.lorenzo.aicalendar.domain.assistant.RoutineOnboarder
import com.lorenzo.aicalendar.domain.assistant.RoutineReply
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.chat.ChatRole
import com.lorenzo.aicalendar.domain.profile.UserProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.lorenzo.aicalendar.data.remote.openrouter.ChatMessage as ApiMessage
import javax.inject.Inject

/** Routine-onboarding chat via OpenRouter (JSON {reply, routine}). */
class OpenRouterRoutineOnboarder @Inject constructor(
    private val api: OpenRouterApi,
    private val json: Json,
) : RoutineOnboarder {

    private val codeFence = Regex("```(?:json)?")
    private val specialTokens = Regex("<\\|?(?:pad|endoftext|eos|bos|sep|cls|unk|mask)\\|?>", RegexOption.IGNORE_CASE)

    override suspend fun next(
        history: List<ChatMessage>,
        userMessage: String,
        profile: UserProfile,
    ): RoutineReply {
        val messages = buildList {
            add(ApiMessage(role = "system", content = AssistantPrompts.routineOnboarder(profile)))
            history.takeLast(20).forEach {
                add(ApiMessage(role = if (it.role == ChatRole.USER) "user" else "assistant", content = it.text))
            }
            add(ApiMessage(role = "user", content = userMessage))
        }
        return parse(api.chat(messages))
    }

    private fun parse(raw: String): RoutineReply {
        val obj = runCatching {
            json.parseToJsonElement(extractJson(raw)).jsonObject
        }.getOrNull() ?: return RoutineReply(clean(raw).ifBlank { "Ok." })

        val reply = obj["reply"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
            ?.let { clean(it) }?.takeIf { it.isNotBlank() }
            ?: "Ok."
        val routine = obj["routine"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
            ?.let { clean(it) }?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        return RoutineReply(reply, routine)
    }

    private fun clean(text: String): String =
        text.replace(specialTokens, "").trim()

    private fun extractJson(raw: String): String {
        val cleaned = raw.replace(codeFence, "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start in 0 until end) cleaned.substring(start, end + 1) else cleaned
    }
}
