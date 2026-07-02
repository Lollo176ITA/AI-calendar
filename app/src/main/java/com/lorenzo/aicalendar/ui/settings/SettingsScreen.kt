@file:OptIn(ExperimentalMaterial3Api::class)

package com.lorenzo.aicalendar.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.lorenzo.aicalendar.data.calendar.SystemCalendarWriter
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorenzo.aicalendar.data.assistant.NanoStatus
import com.lorenzo.aicalendar.ui.profile.ProfileForm
import java.util.Locale

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val showSystemCalendar by viewModel.showSystemCalendar.collectAsStateWithLifecycle()
    val voiceAutoSend by viewModel.voiceAutoSend.collectAsStateWithLifecycle()
    val voiceReplies by viewModel.voiceReplies.collectAsStateWithLifecycle()
    val localAiOnly by viewModel.localAiOnly.collectAsStateWithLifecycle()
    val nanoStatus by viewModel.nanoStatus.collectAsStateWithLifecycle()
    val syncToSystemCalendar by viewModel.syncToSystemCalendar.collectAsStateWithLifecycle()
    val systemCalendarId by viewModel.systemCalendarId.collectAsStateWithLifecycle()
    val writableCalendars by viewModel.writableCalendars.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setShowSystemCalendar(granted) }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.setSyncToSystemCalendar(grants[Manifest.permission.WRITE_CALENDAR] == true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Il tuo profilo", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            ProfileForm(
                profile = draft,
                onChange = viewModel::update,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            SystemCalendarRow(
                checked = showSystemCalendar,
                onToggle = { want ->
                    if (!want) {
                        viewModel.setShowSystemCalendar(false)
                    } else if (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.setShowSystemCalendar(true)
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            SettingSwitchRow(
                icon = Icons.Filled.EditCalendar,
                title = "Scrivi su Google Calendar",
                subtitle = "Gli eventi creati qui finiscono anche nel calendario del telefono",
                checked = syncToSystemCalendar,
                onToggle = { want ->
                    if (!want) {
                        viewModel.setSyncToSystemCalendar(false)
                    } else if (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.setSyncToSystemCalendar(true)
                    } else {
                        writePermissionLauncher.launch(
                            arrayOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR),
                        )
                    }
                },
            )
            if (syncToSystemCalendar && writableCalendars.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                CalendarPickerRow(
                    calendars = writableCalendars,
                    selectedId = systemCalendarId,
                    onSelect = viewModel::setSystemCalendarId,
                )
            }
            Spacer(Modifier.height(12.dp))
            SettingSwitchRow(
                icon = Icons.Filled.Mic,
                title = "Invio automatico dopo la dettatura",
                subtitle = "Quello che detti viene mandato subito all'assistente",
                checked = voiceAutoSend,
                onToggle = viewModel::setVoiceAutoSend,
            )
            Spacer(Modifier.height(12.dp))
            SettingSwitchRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "Risposte vocali",
                subtitle = "L'assistente legge ad alta voce le risposte ai messaggi dettati",
                checked = voiceReplies,
                onToggle = viewModel::setVoiceReplies,
            )
            Spacer(Modifier.height(12.dp))
            SettingSwitchRow(
                icon = Icons.Filled.Shield,
                title = "Elabora solo con AI locali",
                subtitle = "Le richieste non escono dal telefono: usa l'AI integrata (Gemini Nano) quando disponibile",
                checked = localAiOnly,
                onToggle = viewModel::setLocalAiOnly,
            )
            Spacer(Modifier.height(4.dp))
            NanoStatusRow(nanoStatus)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { viewModel.save(); onClose() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salva") }
        }
    }
}

@Composable
private fun SystemCalendarRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    SettingSwitchRow(
        icon = Icons.AutoMirrored.Filled.EventNote,
        title = "Calendario del telefono",
        subtitle = "Mostra anche gli eventi di Google/Samsung Calendar",
        checked = checked,
        onToggle = onToggle,
    )
}

/** Which device calendar mirrored events are written to. */
@Composable
private fun CalendarPickerRow(
    calendars: List<SystemCalendarWriter.WritableCalendar>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = calendars.firstOrNull { it.id == selectedId } ?: calendars.first()

    Row(
        modifier = Modifier.fillMaxWidth().clickable { open = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text("Calendario di destinazione", style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(selected.name, selected.account.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Scegli calendario")
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            calendars.forEach { calendar ->
                DropdownMenuItem(
                    text = {
                        Text(
                            listOfNotNull(calendar.name, calendar.account.takeIf { it.isNotBlank() })
                                .joinToString(" · "),
                        )
                    },
                    onClick = {
                        onSelect(calendar.id)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * Live state of Gemini Nano under the local-AI toggle, so "why is the local AI not answering?"
 * has a visible answer: unsupported device, model download (with progress), ready, or failed.
 */
@Composable
private fun NanoStatusRow(status: NanoStatus) {
    val text = when (status) {
        NanoStatus.Unknown -> "AI del telefono: verifica in corso…"
        NanoStatus.Unsupported -> "AI del telefono: non supportata su questo dispositivo"
        is NanoStatus.Downloading -> buildString {
            append("AI del telefono: download del modello")
            val done = status.downloadedBytes
            if (done != null) {
                append(" · ").append(formatBytes(done))
                status.totalBytes?.let { append(" di ").append(formatBytes(it)) }
            } else {
                append(" in corso…")
            }
        }
        NanoStatus.Ready -> "AI del telefono: Gemini Nano pronto"
        is NanoStatus.Failed ->
            "AI del telefono: download non riuscito" + (status.message?.let { " ($it)" } ?: "")
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (status is NanoStatus.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            val done = (status as? NanoStatus.Downloading)?.downloadedBytes
            val total = (status as? NanoStatus.Downloading)?.totalBytes
            if (done != null && total != null && total > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.ITALIAN, "%.1f GB", bytes / 1e9)
    else -> "${bytes / 1_000_000} MB"
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
