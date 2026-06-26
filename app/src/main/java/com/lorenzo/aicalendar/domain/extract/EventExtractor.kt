package com.lorenzo.aicalendar.domain.extract

import com.lorenzo.aicalendar.domain.model.EventSource
import java.time.Instant
import java.time.ZoneId

/** Raw user input plus the context needed to resolve relative dates ("il 14", "domani"). */
data class ExtractionInput(
    val text: String,
    val now: Instant,
    val zone: ZoneId,
    val source: EventSource,
)

/** Outcome of interpreting [ExtractionInput]: a draft event plus how much to trust it. */
data class ExtractionResult(
    val draft: EventDraft,
    val needsReview: Boolean,
    val warnings: List<String> = emptyList(),
)

/**
 * Turns natural-language text into a structured [EventDraft].
 *
 * The MVP has on-device (ML Kit) and cloud (OpenRouter) implementations behind this seam,
 * selected by availability — the rest of the app never depends on which one runs.
 */
interface EventExtractor {
    suspend fun extract(input: ExtractionInput): ExtractionResult
}
