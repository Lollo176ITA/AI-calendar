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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.lorenzo.aicalendar.ui.profile.ProfileForm

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val showSystemCalendar by viewModel.showSystemCalendar.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setShowSystemCalendar(granted) }

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
            Button(
                onClick = { viewModel.save(); onClose() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salva") }
        }
    }
}

@Composable
private fun SystemCalendarRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text("Calendario del telefono", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Mostra anche gli eventi di Google/Samsung Calendar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
