package com.lorenzo.aicalendar.data.extract

import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.lorenzo.aicalendar.domain.extract.EventDraft
import com.lorenzo.aicalendar.domain.extract.EventExtractor
import com.lorenzo.aicalendar.domain.extract.ExtractionInput
import com.lorenzo.aicalendar.domain.extract.ExtractionResult
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline tier of the hybrid pipeline. ML Kit Entity Extraction resolves the date/time
 * ("il 14 ... alle 15") fully on-device, in Italian; the title is a best-effort cleanup of
 * the remaining text. Always flagged needsReview — the cloud tier produces richer drafts.
 */
@Singleton
class OnDeviceEventExtractor @Inject constructor() : EventExtractor {

    private val client = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ITALIAN).build(),
    )

    override suspend fun extract(input: ExtractionInput): ExtractionResult {
        client.downloadModelIfNeeded().await()

        val params = EntityExtractionParams.Builder(input.text)
            .setReferenceTime(input.now.toEpochMilli())
            .setReferenceTimeZone(TimeZone.getTimeZone(input.zone))
            .setPreferredLocale(Locale.ITALIAN)
            .build()

        val annotations = client.annotate(params).await()

        var startMillis: Long? = null
        val dateTimeSpans = mutableListOf<IntRange>()
        for (annotation in annotations) {
            val dateTime = annotation.entities.mapNotNull { it.asDateTimeEntity() }.firstOrNull()
            if (dateTime != null) {
                if (startMillis == null) startMillis = dateTime.timestampMillis
                dateTimeSpans += annotation.start until annotation.end
            }
        }

        val title = cleanTitle(removeRanges(input.text, dateTimeSpans))
        val warnings = if (startMillis == null) {
            listOf("Data/ora non riconosciuta: scegli tu quando.")
        } else {
            emptyList()
        }

        return ExtractionResult(
            draft = EventDraft(
                title = title,
                start = startMillis?.let(Instant::ofEpochMilli),
                source = input.source,
            ),
            needsReview = true,
            warnings = warnings,
        )
    }
}

/** Blanks the given character [ranges] out of [text] (so they don't pollute the title). */
private fun removeRanges(text: String, ranges: List<IntRange>): String {
    if (ranges.isEmpty()) return text
    val chars = text.toCharArray()
    for (range in ranges) {
        for (i in range) if (i in chars.indices) chars[i] = ' '
    }
    return String(chars)
}
