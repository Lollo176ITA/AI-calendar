package com.lorenzo.aicalendar.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.aicalendar.domain.extract.EventExtractor
import com.lorenzo.aicalendar.domain.extract.ExtractionInput
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import com.lorenzo.aicalendar.domain.usecase.SaveEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val extractor: EventExtractor,
    private val saveEvent: SaveEventUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val zone = clock.zone
    private val _state = MutableStateFlow(QuickAddUiState())
    val state: StateFlow<QuickAddUiState> = _state.asStateFlow()

    fun onInputChange(text: String) = _state.update { it.copy(inputText = text) }

    /** Runs the extractor and switches to review mode with an editable draft. */
    fun interpret() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty() || _state.value.isExtracting) return
        _state.update { it.copy(isExtracting = true, error = null) }

        viewModelScope.launch {
            try {
                val result = extractor.extract(
                    ExtractionInput(text, Instant.now(clock), zone, EventSource.AI_TEXT),
                )
                // Fall back to "in 1 hour" when the extractor couldn't find a date/time.
                val start = result.draft.start ?: Instant.now(clock).plus(Duration.ofHours(1))
                val zdt = start.atZone(zone)
                _state.update {
                    it.copy(
                        isExtracting = false,
                        draft = EditableDraft(
                            title = result.draft.title,
                            date = zdt.toLocalDate(),
                            time = zdt.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                            location = result.draft.location.orEmpty(),
                        ),
                        warnings = result.warnings,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isExtracting = false, error = "Non riesco a interpretare il testo. Riprova.")
                }
            }
        }
    }

    fun onTitleChange(value: String) = updateDraft { it.copy(title = value) }
    fun onLocationChange(value: String) = updateDraft { it.copy(location = value) }
    fun onDateChange(value: LocalDate) = updateDraft { it.copy(date = value) }
    fun onTimeChange(value: LocalTime) = updateDraft { it.copy(time = value) }

    /** Returns from review mode back to editing the raw text. */
    fun backToInput() = _state.update { it.copy(draft = null, warnings = emptyList()) }

    fun save() {
        val draft = _state.value.draft ?: return
        viewModelScope.launch {
            val now = Instant.now(clock)
            val start = ZonedDateTime.of(draft.date, draft.time, zone).toInstant()
            saveEvent(
                CalendarEvent(
                    id = UUID.randomUUID().toString(),
                    title = draft.title.ifBlank { "Evento" },
                    start = start,
                    zone = zone,
                    location = draft.location.ifBlank { null },
                    source = EventSource.AI_TEXT,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            _state.update { it.copy(saved = true) }
        }
    }

    private inline fun updateDraft(transform: (EditableDraft) -> EditableDraft) =
        _state.update { it.copy(draft = it.draft?.let(transform)) }
}
