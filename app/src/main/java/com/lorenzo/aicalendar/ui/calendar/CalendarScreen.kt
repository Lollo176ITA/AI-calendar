@file:OptIn(ExperimentalMaterial3Api::class)

package com.lorenzo.aicalendar.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lorenzo.aicalendar.domain.model.CalendarEvent
import com.lorenzo.aicalendar.domain.model.EventSource
import com.lorenzo.aicalendar.ui.AppDestinations
import com.lorenzo.aicalendar.ui.assistant.AssistantChatPanel
import com.lorenzo.aicalendar.ui.assistant.AssistantInputBar
import com.lorenzo.aicalendar.ui.assistant.AssistantViewModel
import com.lorenzo.aicalendar.ui.assistant.TtsSpeaker
import com.lorenzo.aicalendar.ui.assistant.VoiceInputController
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val IT = Locale.ITALIAN
private val monthTitleFmt = DateTimeFormatter.ofPattern("LLLL yyyy", IT)
private val dayTitleFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", IT)
private val dayShortFmt = DateTimeFormatter.ofPattern("d MMM", IT)

/**
 * Unified home: the agenda plus the assistant. A messaging-style input bar (with mic) is
 * always docked at the bottom, and the conversation expands as a collapsible panel over
 * the calendar — talking to the AI is zero taps away from app launch.
 */
@Composable
fun CalendarScreen(
    onNavigate: (String) -> Unit,
    voiceTrigger: Int = 0,
    viewModel: CalendarViewModel = hiltViewModel(),
    assistantViewModel: AssistantViewModel = hiltViewModel(),
) {
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val eventsByDay by viewModel.eventsByDay.collectAsStateWithLifecycle()
    val today = viewModel.today()

    val messages by assistantViewModel.messages.collectAsStateWithLifecycle()
    val sending by assistantViewModel.sending.collectAsStateWithLifecycle()
    val voiceAutoSend by assistantViewModel.voiceAutoSend.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var chatOpen by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }

    val tts = remember { TtsSpeaker(context) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    LaunchedEffect(Unit) { assistantViewModel.speakReplies.collect { tts.speak(it) } }

    val sendMessage: (String, Boolean) -> Unit = { text, viaVoice ->
        assistantViewModel.send(text, viaVoice)
        input = ""
        chatOpen = true
    }

    val voice = remember {
        VoiceInputController(
            context = context,
            onListeningChange = { listening = it },
            onPartial = { input = it },
            onFinal = { text ->
                if (voiceAutoSend) {
                    sendMessage(text, true)
                } else {
                    input = text
                    chatOpen = true
                }
            },
            onErrorMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
        )
    }
    DisposableEffect(Unit) { onDispose { voice.destroy() } }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voice.start()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Per usare la voce serve il permesso del microfono.") }
        }
    }
    val startVoice: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            voice.start()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // App shortcut "Parla con l'assistente": open the conversation with the mic already on.
    LaunchedEffect(voiceTrigger) {
        if (voiceTrigger > 0) {
            chatOpen = true
            startVoice()
        }
    }

    val onEventClick: (CalendarEvent) -> Unit = { event ->
        // System-calendar events are read-only mirrors — don't open the editable detail screen.
        if (event.source != EventSource.SYSTEM) {
            onNavigate(AppDestinations.event(event.id.substringBefore("@")))
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CalendarDrawer(onItem = { route ->
                scope.launch { drawerState.close() }
                onNavigate(route)
            })
        },
    ) {
    Scaffold(
        topBar = {
            CalendarTopBar(
                viewMode = viewMode,
                onSetViewMode = viewModel::setViewMode,
                onProfile = { scope.launch { drawerState.open() } },
                onSettings = { onNavigate(AppDestinations.SETTINGS) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AssistantInputBar(
                value = input,
                onValueChange = { input = it },
                listening = listening,
                sending = sending,
                onMic = startVoice,
                onStopVoice = { voice.stop() },
                onSend = { sendMessage(input, false) },
                modifier = Modifier.navigationBarsPadding().imePadding(),
                onTapWhileCollapsed = { if (!chatOpen && messages.isNotEmpty()) chatOpen = true },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.weight(1f)) {
                when (viewMode) {
                    CalendarViewMode.DAY -> DayView(
                        date = selectedDate,
                        events = eventsByDay[selectedDate].orEmpty(),
                        isToday = selectedDate == today,
                        onPrev = viewModel::previous,
                        onNext = viewModel::next,
                        onToday = viewModel::goToday,
                        onEventClick = onEventClick,
                    )
                    CalendarViewMode.WEEK -> WeekView(
                        selectedDate = selectedDate,
                        today = today,
                        eventsByDay = eventsByDay,
                        onSelect = viewModel::selectDate,
                        onPrev = viewModel::previous,
                        onNext = viewModel::next,
                        onToday = viewModel::goToday,
                        onEventClick = onEventClick,
                    )
                    CalendarViewMode.MONTH -> MonthView(
                        selectedDate = selectedDate,
                        today = today,
                        eventsByDay = eventsByDay,
                        onSelect = viewModel::selectDate,
                        onEventClick = onEventClick,
                    )
                }
            }
            AnimatedVisibility(visible = chatOpen) {
                AssistantChatPanel(
                    messages = messages,
                    sending = sending,
                    onCollapse = { chatOpen = false },
                    onPickExample = { input = it },
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.55f),
                )
            }
        }
    }
    }
}

@Composable
private fun CalendarDrawer(onItem: (String) -> Unit) {
    ModalDrawerSheet {
        Text(
            "AI-calendar",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(24.dp),
        )
        HorizontalDivider()
        val items = listOf(
            Triple(AppDestinations.UPCOMING, "Prossimi eventi", Icons.Filled.Event),
            Triple(AppDestinations.RECURRING, "Ricorrenti", Icons.Filled.Repeat),
            Triple(AppDestinations.SEARCH, "Ricerca", Icons.Filled.Search),
            Triple(AppDestinations.SUMMARY, "Riepilogo", Icons.Filled.BarChart),
            Triple(AppDestinations.SETTINGS, "Impostazioni", Icons.Filled.Settings),
        )
        items.forEach { (route, label, icon) ->
            NavigationDrawerItem(
                label = { Text(label) },
                icon = { Icon(icon, contentDescription = null) },
                selected = false,
                onClick = { onItem(route) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

@Composable
private fun CalendarTopBar(
    viewMode: CalendarViewMode,
    onSetViewMode: (CalendarViewMode) -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    val labels = mapOf(
        CalendarViewMode.DAY to "Giorno",
        CalendarViewMode.WEEK to "Settimana",
        CalendarViewMode.MONTH to "Mese",
    )
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Calendario") },
        navigationIcon = {
            IconButton(onProfile) { Icon(Icons.Filled.Person, contentDescription = "Profilo") }
        },
        actions = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Cambia vista")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                CalendarViewMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(labels.getValue(mode)) },
                        onClick = { onSetViewMode(mode); menuOpen = false },
                        leadingIcon = {
                            if (mode == viewMode) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                    )
                }
            }
            IconButton(onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Impostazioni") }
        },
    )
}

@Composable
private fun PeriodNav(title: String, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Precedente") }
        Text(
            text = title.replaceFirstChar { it.uppercase(IT) },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        IconButton(onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Successivo") }
        TextButton(onToday) { Text("Oggi") }
    }
}

@Composable
private fun DayView(
    date: LocalDate,
    events: List<CalendarEvent>,
    isToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
) {
    PeriodNav(
        title = if (isToday) "Oggi" else date.format(dayTitleFmt),
        onPrev = onPrev,
        onNext = onNext,
        onToday = onToday,
    )
    DayEventsList(events, date, onEventClick)
}

@Composable
private fun WeekView(
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    onSelect: (LocalDate) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
) {
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }
    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val days = remember(weekStart) { (0L..6L).map { weekStart.plusDays(it) } }

    PeriodNav(
        title = "${days.first().format(dayShortFmt)} – ${days.last().format(dayShortFmt)}",
        onPrev = onPrev,
        onNext = onNext,
        onToday = onToday,
    )
    DaysOfWeekHeader(firstDayOfWeek)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        for (day in days) {
            DayCell(
                modifier = Modifier.weight(1f),
                date = day,
                inMonth = true,
                selected = day == selectedDate,
                isToday = day == today,
                hasEvents = eventsByDay.containsKey(day),
                onClick = { onSelect(day) },
            )
        }
    }
    DayEventsList(eventsByDay[selectedDate].orEmpty(), selectedDate, onEventClick)
}

@Composable
private fun MonthView(
    selectedDate: LocalDate,
    today: LocalDate,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    onSelect: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
) {
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }
    val state = rememberCalendarState(
        startMonth = remember { YearMonth.now().minusMonths(60) },
        endMonth = remember { YearMonth.now().plusMonths(60) },
        firstVisibleMonth = remember { YearMonth.from(selectedDate) },
        firstDayOfWeek = firstDayOfWeek,
    )
    val scope = rememberCoroutineScope()
    val visibleMonth = state.firstVisibleMonth.yearMonth

    PeriodNav(
        title = visibleMonth.format(monthTitleFmt),
        onPrev = { scope.launch { state.animateScrollToMonth(visibleMonth.minusMonths(1)) } },
        onNext = { scope.launch { state.animateScrollToMonth(visibleMonth.plusMonths(1)) } },
        onToday = { scope.launch { state.animateScrollToMonth(YearMonth.now()) }; onSelect(today) },
    )
    DaysOfWeekHeader(firstDayOfWeek)
    HorizontalCalendar(
        state = state,
        dayContent = { day: CalendarDay ->
            DayCell(
                date = day.date,
                inMonth = day.position == DayPosition.MonthDate,
                selected = day.date == selectedDate,
                isToday = day.date == today,
                hasEvents = eventsByDay.containsKey(day.date),
                onClick = { onSelect(day.date) },
            )
        },
    )
    DayEventsList(eventsByDay[selectedDate].orEmpty(), selectedDate, onEventClick)
}

@Composable
private fun DaysOfWeekHeader(firstDayOfWeek: DayOfWeek) {
    val days = remember(firstDayOfWeek) { daysOfWeek(firstDayOfWeek) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        for (day in days) {
            Text(
                text = day.getDisplayName(TextStyle.SHORT, IT).replaceFirstChar { it.uppercase(IT) },
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(colors.primary) else Modifier)
            .then(if (isToday && !selected) Modifier.border(1.dp, colors.primary, CircleShape) else Modifier)
            .clickable(enabled = inMonth, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !inMonth -> colors.onSurfaceVariant.copy(alpha = 0.35f)
                    selected -> colors.onPrimary
                    isToday -> colors.primary
                    else -> colors.onSurface
                },
            )
            if (hasEvents && inMonth) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.onPrimary else colors.primary),
                )
            }
        }
    }
}

@Composable
private fun DayEventsList(
    events: List<CalendarEvent>,
    date: LocalDate,
    onEventClick: (CalendarEvent) -> Unit,
) {
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Nessun evento per ${date.format(dayTitleFmt)}.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(events, key = { it.id }) { event ->
                EventCard(event, onClick = { onEventClick(event) })
            }
        }
    }
}
