package com.lorenzo.aicalendar.data.assistant

import com.lorenzo.aicalendar.data.remote.openrouter.OpenRouterApi
import com.lorenzo.aicalendar.domain.assistant.AiAssistant
import com.lorenzo.aicalendar.domain.assistant.AssistantContext
import com.lorenzo.aicalendar.domain.assistant.AssistantReply
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.chat.ChatRole
import com.lorenzo.aicalendar.data.remote.openrouter.ChatMessage as ApiMessage
import javax.inject.Inject

/** Cloud conversational assistant via OpenRouter (JSON {reply, events[]} contract). */
class OpenRouterAssistant @Inject constructor(
    private val api: OpenRouterApi,
    private val parser: AssistantReplyParser,
) : AiAssistant {

    override suspend fun respond(
        history: List<ChatMessage>,
        userMessage: String,
        context: AssistantContext,
    ): AssistantReply = respond(history, userMessage, context, models = null)

    /** Same as [respond], pinning the [models] fallback chain (used by the eval harness). */
    suspend fun respond(
        history: List<ChatMessage>,
        userMessage: String,
        context: AssistantContext,
        models: List<String>?,
    ): AssistantReply {
        val messages = buildList {
            add(ApiMessage(role = "system", content = AssistantPrompts.eventAssistant(context)))
            history.takeLast(20).forEach {
                add(ApiMessage(role = if (it.role == ChatRole.USER) "user" else "assistant", content = it.text))
            }
            add(ApiMessage(role = "user", content = userMessage))
        }
        val raw = if (models == null) api.chat(messages) else api.chat(messages, models)
        return parser.parse(raw, context.zone)
    }
}
