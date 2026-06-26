package com.lorenzo.aicalendar.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import com.lorenzo.aicalendar.domain.repository.EventRepository
import com.lorenzo.aicalendar.domain.usecase.SaveEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: EventRepository,
    private val saveEvent: SaveEventUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId = clock.zone
    private val today: LocalDate = LocalDate.now(clock)

    val uiState: StateFlow<TodayUiState> =
        repository.observeEventsForDay(today, zone)
            .map { events -> TodayUiState(date = today, events = events) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TodayUiState(date = today, events = emptyList()),
            )

    /**
     * Temporary: inserts a sample event a couple of minutes out (with a 2-minute reminder
     * offset, so the reminder fires right away) to exercise the Room → UI path AND the
     * reminder pipeline. Replaced by the real AI Quick-Add in a later slice.
     */
    fun addSampleEvent() {
        viewModelScope.launch {
            val now = Instant.now(clock)
            saveEvent(
                CalendarEvent(
                    id = UUID.randomUUID().toString(),
                    title = "Evento di prova",
                    start = now.plus(Duration.ofMinutes(2)),
                    zone = zone,
                    location = "Casa",
                    source = EventSource.MANUAL,
                    reminderOffsetMin = 2,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
}
