package com.lorenzo.aicalendar.ui.assistant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lorenzo.aicalendar.domain.chat.ChatMessage
import com.lorenzo.aicalendar.domain.chat.ChatRole

/**
 * The assistant conversation, shown as a collapsible panel over the agenda on the
 * unified home screen. Empty conversation → capability welcome with tappable examples.
 */
@Composable
fun AssistantChatPanel(
    messages: List<ChatMessage>,
    sending: Boolean,
    onCollapse: () -> Unit,
    onPickExample: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, sending) {
        val count = messages.size + if (sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Surface(modifier = modifier, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Assistente",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onCollapse) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Chiudi conversazione")
                }
            }
            if (messages.isEmpty() && !sending) {
                AssistantWelcome(modifier = Modifier.weight(1f), onPick = onPickExample)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.id }) { MessageBubble(it) }
                    if (sending) item { Bubble(fromUser = false, text = "…") }
                }
            }
        }
    }
}

/**
 * Message-app style input bar docked at the bottom of the home screen: text field plus a
 * mic that morphs into stop-while-listening and send-when-there-is-text.
 */
@Composable
fun AssistantInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    listening: Boolean,
    sending: Boolean,
    onMic: () -> Unit,
    onStopVoice: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onTapWhileCollapsed: () -> Unit = {},
) {
    Surface(modifier = modifier, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onTapWhileCollapsed()
                    onValueChange(it)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (listening) "Ti ascolto…" else "Scrivi o detta un messaggio…") },
                maxLines = 4,
                readOnly = listening,
            )
            when {
                listening -> {
                    val pulse by rememberInfiniteTransition(label = "mic-pulse").animateFloat(
                        initialValue = 1f,
                        targetValue = 0.4f,
                        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                        label = "mic-pulse-alpha",
                    )
                    FilledIconButton(onClick = onStopVoice, modifier = Modifier.alpha(pulse)) {
                        Icon(Icons.Filled.Stop, contentDescription = "Ferma la dettatura")
                    }
                }

                value.isBlank() -> FilledIconButton(onClick = onMic, enabled = !sending) {
                    Icon(Icons.Filled.Mic, contentDescription = "Detta un messaggio")
                }

                else -> FilledIconButton(onClick = onSend, enabled = !sending) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Invia")
                }
            }
        }
    }
}

@Composable
private fun AssistantWelcome(modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val examples = listOf(
        "Pranzo con Anna domani alle 13",
        "Ricordami la visita medica un sabato al mese",
        "Sposta la riunione alle 15",
        "Cosa ho in agenda questa settimana?",
    )
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Crea, sposta o cancella eventi parlando in modo naturale — con il microfono o " +
                "scrivendo. Gestisco anche le ricorrenze e ti avviso sulle sovrapposizioni.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        examples.forEach { example ->
            Card(
                onClick = { onPick(example) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(
                    example,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Bubble(fromUser = message.role == ChatRole.USER, text = message.text)
}

@Composable
private fun Bubble(fromUser: Boolean, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (fromUser) colors.primary else colors.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (fromUser) colors.onPrimary else colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
