@file:OptIn(ExperimentalMaterial3Api::class)

package com.lorenzo.aicalendar.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)

@Composable
fun QuickAddScreen(
    onClose: () -> Unit,
    viewModel: QuickAddViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.draft == null) "Nuovo evento" else "Conferma evento") },
                navigationIcon = {
                    IconButton(onClick = { if (state.draft != null) viewModel.backToInput() else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val draft = state.draft
            if (draft == null) {
                InputPhase(state = state, viewModel = viewModel)
            } else {
                ReviewPhase(draft = draft, warnings = state.warnings, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ColumnScope.InputPhase(state: QuickAddUiState, viewModel: QuickAddViewModel) {
    Text(
        text = "Scrivi cosa vuoi aggiungere, in linguaggio naturale.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.inputText,
        onValueChange = viewModel::onInputChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        enabled = !state.isExtracting,
        label = { Text("Es: Dentista domani alle 16 in centro") },
    )
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(
        onClick = viewModel::interpret,
        enabled = state.inputText.isNotBlank() && !state.isExtracting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isExtracting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Interpreto…")
        } else {
            Text("Interpreta")
        }
    }
}

@Composable
private fun ColumnScope.ReviewPhase(
    draft: EditableDraft,
    warnings: List<String>,
    viewModel: QuickAddViewModel,
) {
    if (warnings.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    OutlinedTextField(
        value = draft.title,
        onValueChange = viewModel::onTitleChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Titolo") },
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateField(date = draft.date, onPick = viewModel::onDateChange, modifier = Modifier.weight(1f))
        TimeField(time = draft.time, onPick = viewModel::onTimeChange, modifier = Modifier.weight(1f))
    }

    OutlinedTextField(
        value = draft.location,
        onValueChange = viewModel::onLocationChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Luogo (opzionale)") },
    )

    Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
        Text("Salva")
    }
}

@Composable
private fun DateField(date: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = modifier) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(date.format(dateFormatter))
    }
    if (show) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Annulla") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun TimeField(time: LocalTime, onPick: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }, modifier = modifier) {
        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(time.format(timeFormatter))
    }
    if (show) {
        val pickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    onPick(LocalTime.of(pickerState.hour, pickerState.minute))
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Annulla") } },
            text = { TimePicker(state = pickerState) },
        )
    }
}
