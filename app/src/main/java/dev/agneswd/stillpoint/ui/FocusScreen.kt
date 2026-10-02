package dev.agneswd.stillpoint.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.ActiveFocus
import dev.agneswd.stillpoint.data.BlockMode
import dev.agneswd.stillpoint.data.FocusPhase
import dev.agneswd.stillpoint.data.FocusSession
import dev.agneswd.stillpoint.data.FocusSound
import dev.agneswd.stillpoint.data.Settings
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.data.updateSettings
import dev.agneswd.stillpoint.focus.Focus
import dev.agneswd.stillpoint.guard.formatDuration
import dev.agneswd.stillpoint.guard.formatMinutes
import dev.agneswd.stillpoint.guard.time
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FocusScreen(navigator: Navigator) {
    val context = LocalContext.current
    val dao = context.app.dao
    val focus by dao.activeFocusFlow().collectAsState(null)
    val settings by dao.settings().collectAsState(null)
    val sessions by dao.sessions().collectAsState(emptyList())
    var editing by remember { mutableStateOf<FocusSession?>(null) }

    // Ask for notes when a session ends while this screen is open.
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(focus == null, sessions.firstOrNull()?.id) {
        if (focus != null) wasRunning = true
        else if (wasRunning) {
            wasRunning = false
            editing = sessions.firstOrNull()
        }
    }

    val s = settings ?: return
    val update: SettingsUpdate = { change -> context.app.scope.launch { dao.updateSettings(change) } }

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            val running = focus
            if (running != null) Running(running) else Idle(s, sessions)
        }
        if (focus == null) {
            item { FocusOptions(s, navigator, update) }
        }
        item { History(s, sessions, update) }
        items(sessions.take(30), key = { it.id }) { session ->
            ListRow(
                title = formatDuration(session.focusedMillis) + if (session.tag.isNotBlank()) ", ${session.tag}" else "",
                subtitle = listOf(dateText(session.startedAt), if (session.completed) null else "ended early", session.notes.ifBlank { null })
                    .filterNotNull().joinToString(" · "),
                onClick = { editing = session },
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    editing?.let { session -> SessionDialog(session, onDismiss = { editing = null }) }
}

/** Saves a change to the settings row. */
typealias SettingsUpdate = ((Settings) -> Settings) -> Unit

@Composable
private fun Idle(s: Settings, sessions: List<FocusSession>) {
    val context = LocalContext.current
    var tag by remember { mutableStateOf("") }
    val recentTags = remember(sessions) { sessions.map { it.tag }.filter { it.isNotBlank() }.distinct().take(5) }
    Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 40.dp)) {
        Text("%d:00".format(s.focusMinutes), style = NumberStyle)
        Text(
            if (s.focusRounds > 1) "${s.focusRounds} rounds with ${formatMinutes(s.breakMinutes)} breaks, about ${formatMinutes(s.focusRounds * s.focusMinutes + (s.focusRounds - 1) * s.breakMinutes)} in all"
            else "One round",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(tag, { tag = it }, label = { Text("What are you working on?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (recentTags.isNotEmpty()) {
            Row {
                recentTags.forEach { recent -> TextButton(onClick = { tag = recent }) { Text(recent) } }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { context.app.scope.launch { Focus.start(context, tag) } },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Start focus") }
    }
}

@Composable
private fun Running(focus: ActiveFocus) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(focus) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val left = (focus.phaseEndsAt - now).coerceAtLeast(0)
    val total = (focus.phaseEndsAt - focus.phaseStartedAt).coerceAtLeast(1)
    val ring = MaterialTheme.colorScheme.primary
    val breakRing = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when (focus.phase) {
                FocusPhase.FOCUS -> if (focus.rounds > 1) "Round ${focus.round} of ${focus.rounds}" else "Focus"
                FocusPhase.BREAK -> "Break"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (focus.phase == FocusPhase.FOCUS) ring else breakRing,
        )
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(260.dp)) {
                val stroke = 10.dp.toPx()
                drawArc(track, 0f, 360f, false, style = Stroke(stroke))
                drawArc(
                    if (focus.phase == FocusPhase.FOCUS) ring else breakRing,
                    -90f,
                    360f * left / total,
                    false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Text("%d:%02d".format(left / 60_000, left / 1000 % 60), style = NumberStyle)
        }
        Spacer(Modifier.height(24.dp))
        if (focus.tag.isNotBlank()) Text(focus.tag, style = MaterialTheme.typography.titleMedium)
        Text("Ends at ${time(focus.phaseEndsAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        if (focus.strict) {
            Text("This is a strict session. It cannot end early.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            OutlinedButton(onClick = { context.app.scope.launch { Focus.giveUp(context) } }) { Text("End session") }
        }
    }
}

@Composable
private fun FocusOptions(s: Settings, navigator: Navigator, update: SettingsUpdate) {
    SectionTitle("Session")
    Stepper("Focus length", s.focusMinutes, 5..180, 5, ::formatMinutes) { v -> update { it.copy(focusMinutes = v) } }
    Stepper("Break length", s.breakMinutes, 0..30, 1, ::formatMinutes) { v -> update { it.copy(breakMinutes = v) } }
    Stepper("Rounds", s.focusRounds, 1..8, 1, Int::toString) { v -> update { it.copy(focusRounds = v) } }

    SectionTitle("During focus")
    val count = s.focusPackages.size
    ListRow(
        if (s.focusMode == BlockMode.LISTED) "Blocked apps" else "Allowed apps",
        if (count == 0) "None chosen" else appCount(count),
        onClick = {
            navigator.push(
                Route.PickApps(if (s.focusMode == BlockMode.LISTED) "Block during focus" else "Allow during focus", s.focusPackages, single = false) { picked ->
                    update { it.copy(focusPackages = picked) }
                },
            )
        },
    )
    SwitchRow("Block every other app", "Only the chosen apps work during focus.", s.focusMode == BlockMode.ALL_EXCEPT) { on ->
        update { it.copy(focusMode = if (on) BlockMode.ALL_EXCEPT else BlockMode.LISTED) }
    }
    SwitchRow("Strict", "You cannot end the session early.", s.focusStrict) { on -> update { it.copy(focusStrict = on) } }
    SwitchRow("Lock the home screen", "Pressing home brings you back here.", s.focusLockHome) { on -> update { it.copy(focusLockHome = on) } }
    ListRow("Sound") {
        Row {
            FocusSound.entries.forEach { sound ->
                TextButton(onClick = { update { it.copy(focusSound = sound) } }) {
                    Text(
                        sound.name.lowercase().replaceFirstChar(Char::uppercase),
                        color = if (s.focusSound == sound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun History(s: Settings, sessions: List<FocusSession>, update: SettingsUpdate) {
    val byDay = remember(sessions) { sessions.groupBy { dayOf(it.startedAt) }.mapValues { (_, list) -> list.sumOf { it.focusedMillis } } }
    val goal = s.focusGoalMinutes * 60_000L
    val today = LocalDate.now()
    // The streak counts back from yesterday. Today joins it once the goal is met.
    var day = if ((byDay[today] ?: 0) >= goal) today else today.minusDays(1)
    var streak = 0
    while (goal > 0 && (byDay[day] ?: 0) >= goal) {
        streak++
        day = day.minusDays(1)
    }
    SectionTitle("History")
    Stepper("Daily goal", s.focusGoalMinutes, 15..600, 15, ::formatMinutes) { v -> update { it.copy(focusGoalMinutes = v) } }
    ListRow(
        "${formatDuration(byDay[today] ?: 0)} today",
        when (streak) {
            0 -> "Meet your daily goal to start a streak."
            1 -> "1 day streak"
            else -> "$streak day streak"
        },
    )
}

@Composable
private fun SessionDialog(session: FocusSession, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var tag by remember { mutableStateOf(session.tag) }
    var notes by remember { mutableStateOf(session.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("You focused for ${formatDuration(session.focusedMillis)}") },
        text = {
            Column {
                OutlinedTextField(tag, { tag = it }, label = { Text("Tag") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(notes, { notes = it }, label = { Text("What did you get done?") }, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.app.scope.launch { context.app.dao.saveSession(session.copy(tag = tag.trim(), notes = notes.trim())) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = {
                context.app.scope.launch { context.app.dao.deleteSession(session) }
                onDismiss()
            }) { Text("Delete") }
        },
    )
}

private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

private fun dateText(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormat)
