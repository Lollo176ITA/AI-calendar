@file:OptIn(ExperimentalMaterial3Api::class)

package com.lorenzo.aicalendar.ui.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ITALIAN)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)

@Composable
fun EventDetailScreen(
    onClose: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val event by viewModel.event.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(closed) { if (closed) onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evento") },
                navigationIcon = {
                    IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, "Elimina", tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        val e = event
        if (e == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val zdt = e.start.atZone(e.zone)
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = e.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Titolo") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField(
                        date = zdt.toLocalDate(),
                        onPick = { viewModel.setDateTime(it, zdt.toLocalTime()) },
                        modifier = Modifier.weight(1f),
                    )
                    TimeField(
                        time = zdt.toLocalTime(),
                        onPick = { viewModel.setDateTime(zdt.toLocalDate(), it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = e.location.orEmpty(),
                    onValueChange = viewModel::setLocation,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Luogo (opzionale)") },
                )
                e.recurrence?.let { rec ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Ricorrenza: ${rec.label.ifBlank { "ricorrente" }}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = viewModel::removeRecurrence) { Text("Rimuovi") }
                    }
                }
                Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("Salva") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } },
            title = { Text("Eliminare l'evento?") },
            text = { Text("L'evento (e le sue ripetizioni) verrà rimosso.") },
        )
    }
}

@Composable
private fun DateField(date: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = modifier) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(date.format(dateFmt))
    }
    if (show) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Annulla") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun TimeField(time: LocalTime, onPick: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = modifier) {
        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(time.format(timeFmt))
    }
    if (show) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); show = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Annulla") } },
            text = { TimePicker(state = state) },
        )
    }
}
