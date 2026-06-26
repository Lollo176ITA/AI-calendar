package com.lorenzo.aicalendar.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.repository.EventRepository
import com.lorenzo.aicalendar.domain.usecase.DeleteEventUseCase
import com.lorenzo.aicalendar.domain.usecase.SaveEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val repository: EventRepository,
    private val saveEvent: SaveEventUseCase,
    private val deleteEvent: DeleteEventUseCase,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Occurrence ids look like "<masterId>@<millis>"; edit/delete operate on the master.
    private val masterId: String = savedStateHandle.get<String>(ARG_ID).orEmpty().substringBefore("@")

    private val _event = MutableStateFlow<CalendarEvent?>(null)
    val event: StateFlow<CalendarEvent?> = _event.asStateFlow()

    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    init {
        viewModelScope.launch { _event.value = repository.getEvent(masterId) }
    }

    fun setTitle(value: String) = _event.update { it?.copy(title = value) }

    fun setLocation(value: String) = _event.update { it?.copy(location = value.ifBlank { null }) }

    fun setDateTime(date: LocalDate, time: LocalTime) = _event.update { e ->
        e?.copy(start = ZonedDateTime.of(date, time, e.zone).toInstant())
    }

    fun removeRecurrence() = _event.update { it?.copy(recurrence = null) }

    fun save() {
        viewModelScope.launch {
            _event.value?.let { saveEvent(it.copy(updatedAt = Instant.now(clock))) }
            _closed.value = true
        }
    }

    fun delete() {
        viewModelScope.launch {
            deleteEvent(masterId)
            _closed.value = true
        }
    }

    companion object {
        const val ARG_ID = "id"
    }
}
