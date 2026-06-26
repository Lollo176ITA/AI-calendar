package com.lorenzo.aicalendar.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.aicalendar.data.calendar.SystemCalendarReader
import com.lorenzo.aicalendar.data.settings.SettingsRepository
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class CalendarViewMode { DAY, WEEK, MONTH }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    repository: EventRepository,
    settings: SettingsRepository,
    systemReader: SystemCalendarReader,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId = clock.zone
    private val windowStart: LocalDate = LocalDate.now(clock).minusMonths(6)
    private val windowEndExclusive: LocalDate = LocalDate.now(clock).plusMonths(18)

    private val _selectedDate = MutableStateFlow(LocalDate.now(clock))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _viewMode = MutableStateFlow(CalendarViewMode.DAY)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    /** System-calendar overlay, loaded once when enabled (empty/absent otherwise). */
    private val systemEvents: Flow<List<CalendarEvent>> =
        settings.showSystemCalendar.flatMapLatest { show ->
            if (!show) {
                flowOf(emptyList())
            } else {
                flow {
                    emit(
                        systemReader.eventsBetween(
                            windowStart.atStartOfDay(zone).toInstant(),
                            windowEndExclusive.atStartOfDay(zone).toInstant(),
                            zone,
                        ),
                    )
                }
            }
        }

    /**
     * Events across a wide window around today, grouped by day, for the grids' event dots and
     * the selected-day list. Merges the app's events with the optional system-calendar overlay.
     */
    val eventsByDay: StateFlow<Map<LocalDate, List<CalendarEvent>>> =
        combine(
            repository.observeOccurrencesInRange(windowStart, windowEndExclusive, zone),
            systemEvents,
        ) { app, system ->
            (app + system)
                .groupBy { it.start.atZone(zone).toLocalDate() }
                .mapValues { (_, events) -> events.sortedBy { it.start } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun today(): LocalDate = LocalDate.now(clock)

    fun setViewMode(mode: CalendarViewMode) = _viewMode.update { mode }

    fun selectDate(date: LocalDate) = _selectedDate.update { date }

    fun goToday() = _selectedDate.update { LocalDate.now(clock) }

    fun previous() = shift(-1)

    fun next() = shift(1)

    private fun shift(direction: Int) = _selectedDate.update { date ->
        when (_viewMode.value) {
            CalendarViewMode.DAY -> date.plusDays(direction.toLong())
            CalendarViewMode.WEEK -> date.plusWeeks(direction.toLong())
            CalendarViewMode.MONTH -> date.plusMonths(direction.toLong())
        }
    }
}
