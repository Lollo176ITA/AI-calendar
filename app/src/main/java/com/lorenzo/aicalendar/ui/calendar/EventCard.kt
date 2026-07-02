package com.lorenzo.aicalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)

/**
 * One agenda event: colored accent bar (primary = app events, tertiary = phone-calendar
 * mirrors), start/end times on the left, title with recurrence/location/origin below.
 */
@Composable
fun EventCard(event: CalendarEvent, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val fromSystem = event.source == EventSource.SYSTEM
    val accent = if (fromSystem) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(52.dp)) {
                if (event.allDay) {
                    Text(
                        "Tutto il giorno",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = event.start.atZone(event.zone).format(timeFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = event.effectiveEnd.atZone(event.zone).format(timeFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = event.title, style = MaterialTheme.typography.titleMedium)
                event.recurrence?.label?.takeIf { it.isNotBlank() }?.let { label ->
                    DetailRow(icon = { RepeatIcon() }, text = label)
                }
                if (!event.location.isNullOrBlank()) {
                    DetailRow(icon = { PlaceIcon() }, text = event.location)
                }
                if (fromSystem) {
                    DetailRow(
                        icon = { PhoneIcon() },
                        text = "dal telefono" + (event.notes?.let { " · $it" } ?: ""),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RepeatIcon() = DetailIcon(Icons.Filled.Repeat)

@Composable
private fun PlaceIcon() = DetailIcon(Icons.Outlined.Place)

@Composable
private fun PhoneIcon() = DetailIcon(Icons.Outlined.Smartphone)

@Composable
private fun DetailIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
