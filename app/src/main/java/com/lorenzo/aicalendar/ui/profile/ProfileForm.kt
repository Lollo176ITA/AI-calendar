@file:OptIn(ExperimentalMaterial3Api::class)

package com.lorenzo.aicalendar.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lorenzo.aicalendar.domain.profile.Profession
import com.lorenzo.aicalendar.domain.profile.Sex
import com.lorenzo.aicalendar.domain.profile.UserProfile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val IT = Locale.ITALIAN
private val birthFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", IT)

/** Shared editable profile form, used by onboarding and settings. */
@Composable
fun ProfileForm(
    profile: UserProfile,
    onChange: (UserProfile) -> Unit,
    modifier: Modifier = Modifier,
    showRoutine: Boolean = true,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = profile.firstName,
            onValueChange = { onChange(profile.copy(firstName = it)) },
            label = { Text("Nome") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = profile.lastName,
            onValueChange = { onChange(profile.copy(lastName = it)) },
            label = { Text("Cognome") },
            modifier = Modifier.fillMaxWidth(),
        )

        Choice(
            label = "Sesso",
            options = listOf(Sex.MALE to "Uomo", Sex.FEMALE to "Donna", Sex.OTHER to "Altro"),
            selected = profile.sex,
            onSelect = { onChange(profile.copy(sex = it)) },
        )

        BirthDateField(profile.birthDate) { onChange(profile.copy(birthDate = it)) }

        OutlinedTextField(
            value = profile.city,
            onValueChange = { onChange(profile.copy(city = it)) },
            label = { Text("Città") },
            modifier = Modifier.fillMaxWidth(),
        )

        Choice(
            label = "Professione",
            options = listOf(
                Profession.STUDENT to "Studente",
                Profession.WORKER to "Lavoratore",
                Profession.OTHER to "Altro",
            ),
            selected = profile.profession,
            onSelect = { onChange(profile.copy(profession = it)) },
        )

        if (showRoutine) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "La tua routine",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = profile.routine,
                    onValueChange = { onChange(profile.copy(routine = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = {
                        Text("Es: lun-ven lavoro 9-18, palestra mar/gio alle 19, domenica libero")
                    },
                )
            }
        }
    }
}

@Composable
private fun <T> Choice(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, text) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(text) }
            }
        }
    }
}

@Composable
private fun BirthDateField(date: LocalDate?, onPick: (LocalDate) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Data di nascita", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(date?.format(birthFormatter) ?: "Seleziona")
        }
    }
    if (show) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
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
        ) { DatePicker(state = pickerState) }
    }
}
