@file:OptIn(ExperimentalMaterial3Api::class)

package com.lorenzo.aicalendar.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.ui.calendar.EventCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val IT = Locale.ITALIAN
private val dayHeaderFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", IT)

@Composable
private fun SectionScaffold(
    title: String,
    onClose: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                },
            )
        },
        content = content,
    )
}

@Composable
private fun EmptyHint(text: String, padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun UpcomingScreen(onClose: () -> Unit, viewModel: UpcomingViewModel = hiltViewModel()) {
    val eventsByDay by viewModel.eventsByDay.collectAsStateWithLifecycle()
    SectionScaffold("Prossimi eventi", onClose) { padding ->
        if (eventsByDay.isEmpty()) {
            EmptyHint("Nessun evento in arrivo.", padding)
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                eventsByDay.forEach { (date, events) ->
                    item(key = date.toString()) {
                        Text(
                            date.format(dayHeaderFmt).replaceFirstChar { it.uppercase(IT) },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(events, key = { it.id }) { EventCard(it) }
                }
            }
        }
    }
}

@Composable
fun RecurringScreen(onClose: () -> Unit, viewModel: RecurringViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    SectionScaffold("Ricorrenti", onClose) { padding ->
        if (events.isEmpty()) {
            EmptyHint("Nessuna attività ricorrente. Dillo all'assistente, es. \"lavatrice ogni giorno\".", padding)
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(events, key = { it.id }) { event ->
                    RecurringRow(event, onDelete = { viewModel.delete(event.id) })
                }
            }
        }
    }
}

@Composable
private fun RecurringRow(event: CalendarEvent, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                event.recurrence?.let {
                    Text(
                        it.label.ifBlank { "Ricorrente" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onDelete) {
                Icon(Icons.Filled.Delete, "Elimina", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SearchScreen(onClose: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    SectionScaffold("Ricerca", onClose) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cerca eventi") },
                singleLine = true,
            )
            if (query.isNotBlank() && results.isEmpty()) {
                EmptyHint("Nessun risultato.", PaddingValues(top = 24.dp))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.id }) { EventCard(it) }
                }
            }
        }
    }
}

@Composable
fun SummaryScreen(onClose: () -> Unit, viewModel: SummaryViewModel = hiltViewModel()) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    SectionScaffold("Riepilogo", onClose) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("Eventi questa settimana", summary.thisWeek)
            StatCard("Eventi questo mese", summary.thisMonth)
            StatCard("Attività ricorrenti", summary.recurring)
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

