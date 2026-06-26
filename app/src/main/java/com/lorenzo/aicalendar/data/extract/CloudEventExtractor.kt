package com.lorenzo.aicalendar.data.extract

import com.lorenzo.aicalendar.data.remote.openrouter.OpenRouterApi
import com.lorenzo.aicalendar.domain.extract.EventDraft
import com.lorenzo.aicalendar.domain.extract.EventExtractor
import com.lorenzo.aicalendar.domain.extract.ExtractionInput
import com.lorenzo.aicalendar.domain.extract.ExtractionResult
import java.time.LocalDateTime
import javax.inject.Inject

/** Cloud tier: OpenRouter structured-output extraction. Produces a confident, complete draft. */
class CloudEventExtractor @Inject constructor(
    private val api: OpenRouterApi,
) : EventExtractor {

    override suspend fun extract(input: ExtractionInput): ExtractionResult {
        val extracted = api.extractEvent(input.text, input.now, input.zone)

        val start = parseLocal(extracted.startDateTime)?.atZone(input.zone)?.toInstant()
        val end = extracted.endDateTime
            ?.let { parseLocal(it) }
            ?.atZone(input.zone)
            ?.toInstant()

        return ExtractionResult(
            draft = EventDraft(
                title = extracted.title.ifBlank { "Evento" },
                start = start,
                end = end,
                allDay = extracted.allDay,
                location = extracted.location?.takeIf { it.isNotBlank() },
                source = input.source,
            ),
            needsReview = start == null,
            warnings = if (start == null) listOf("Data/ora non chiara: controllala.") else emptyList(),
        )
    }

    // The model returns local ISO-8601 without offset; tolerate a trailing 'Z' or offset.
    private fun parseLocal(value: String): LocalDateTime? = runCatching {
        LocalDateTime.parse(value)
    }.recoverCatching {
        java.time.OffsetDateTime.parse(value).toLocalDateTime()
    }.getOrNull()
}
