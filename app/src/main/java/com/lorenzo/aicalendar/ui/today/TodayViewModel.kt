package com.lorenzo.aicalendar.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import com.lorenzo.aicalendar.domain.repository.EventRepository
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
     * Temporary: inserts a sample event ~1h from now so the reactive Room → UI path is
     * visible. Replaced by the real AI Quick-Add in a later slice.
     */
    fun addSampleEvent() {
        viewModelScope.launch {
            val now = Instant.now(clock)
            repository.upsert(
                CalendarEvent(
                    id = UUID.randomUUID().toString(),
                    title = "Evento di prova",
                    start = now.plus(Duration.ofHours(1)),
                    zone = zone,
                    location = "Casa",
                    source = EventSource.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
}
