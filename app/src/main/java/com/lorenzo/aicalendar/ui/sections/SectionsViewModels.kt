package com.lorenzo.aicalendar.ui.sections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.repository.EventRepository
import com.lorenzo.aicalendar.domain.usecase.DeleteEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@HiltViewModel
class UpcomingViewModel @Inject constructor(
    repository: EventRepository,
    clock: Clock,
) : ViewModel() {
    private val zone = clock.zone
    private val today = LocalDate.now(clock)

    val eventsByDay: StateFlow<Map<LocalDate, List<CalendarEvent>>> =
        repository.observeOccurrencesInRange(today, today.plusMonths(3), zone)
            .map { events ->
                events.sortedBy { it.start }
                    .groupBy { it.start.atZone(zone).toLocalDate() }
                    .toSortedMap()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

@HiltViewModel
class RecurringViewModel @Inject constructor(
    repository: EventRepository,
    private val deleteEvent: DeleteEventUseCase,
) : ViewModel() {
    val events: StateFlow<List<CalendarEvent>> =
        repository.observeRecurringEvents()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String) = viewModelScope.launch { deleteEvent(id) }.let { }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    repository: EventRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<CalendarEvent>> =
        _query.flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.searchEvents(q)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }
}

data class CalendarSummary(val thisWeek: Int = 0, val thisMonth: Int = 0, val recurring: Int = 0)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    repository: EventRepository,
    clock: Clock,
) : ViewModel() {
    private val zone = clock.zone
    private val today = LocalDate.now(clock)
    private val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val monthStart = today.withDayOfMonth(1)

    val summary: StateFlow<CalendarSummary> = combine(
        repository.observeOccurrencesInRange(weekStart, weekStart.plusWeeks(1), zone),
        repository.observeOccurrencesInRange(monthStart, monthStart.plusMonths(1), zone),
        repository.observeRecurringEvents(),
    ) { week, month, recurring ->
        CalendarSummary(week.size, month.size, recurring.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarSummary())
}
