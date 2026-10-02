package dev.agneswd.stillpoint.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.ActiveFocus
import dev.agneswd.stillpoint.data.BlockMode
import dev.agneswd.stillpoint.data.FocusPhase
import dev.agneswd.stillpoint.data.FocusSound
import dev.agneswd.stillpoint.data.Settings
import dev.agneswd.stillpoint.data.TimerMode
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.data.updateSettings
import dev.agneswd.stillpoint.focus.Focus
import dev.agneswd.stillpoint.game.gameState
import dev.agneswd.stillpoint.guard.formatMinutes
import dev.agneswd.stillpoint.ui.design.ButtonKind
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.ChunkyCard
import dev.agneswd.stillpoint.ui.design.Confetti
import dev.agneswd.stillpoint.ui.design.CountUp
import dev.agneswd.stillpoint.ui.design.Flame
import dev.agneswd.stillpoint.ui.design.FocusBackdrop
import dev.agneswd.stillpoint.ui.design.FocusTheme
import dev.agneswd.stillpoint.ui.design.Mood
import dev.agneswd.stillpoint.ui.design.NumberStyle
import dev.agneswd.stillpoint.ui.design.Pebble
import dev.agneswd.stillpoint.ui.design.Sp
import dev.agneswd.stillpoint.ui.design.XpBolt
import dev.agneswd.stillpoint.ui.design.appear
import dev.agneswd.stillpoint.ui.design.popIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun themeOf(name: String): FocusTheme = FocusTheme.entries.firstOrNull { it.name == name } ?: FocusTheme.LAKE

// ---------- Setup ----------

/** Choose how the next session runs, then start it. */
@Composable
fun FocusSetup(navigator: Navigator, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val settings by app.dao.settings().collectAsState(null)
    val sessions by app.dao.sessions().collectAsState(emptyList())
    var tag by remember { mutableStateOf("") }
    val s = settings ?: return
    val update: SettingsUpdate = { change -> app.scope.launch { app.dao.updateSettings(change) } }
    val recentTags = remember(sessions) { sessions.map { it.tag }.filter { it.isNotBlank() && it != "First focus" }.distinct().take(6) }

    Column(Modifier.fillMaxSize().background(Sp.colors.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Focus setup", onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Live preview of the chosen theme.
            Box(
                Modifier.padding(horizontal = ScreenPadding).fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                FocusBackdrop(themeOf(s.focusTheme), Modifier.fillMaxSize(), center = Offset(0.5f, 0.45f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (s.timerMode == TimerMode.STOPWATCH) "0:00" else "%d:00".format(s.focusMinutes),
                        style = NumberStyle.copy(fontSize = NumberStyle.fontSize * 0.8f),
                        color = Color.White,
                    )
                    Text(modeLine(s), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                }
            }
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = ScreenPadding, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(FocusTheme.entries) { theme ->
                    val on = theme.name == s.focusTheme
                    // The picture and its label are one tap target.
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { update { it.copy(focusTheme = theme.name) } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(64.dp, 84.dp).clip(RoundedCornerShape(16.dp))
                                .border(if (on) 3.dp else 0.dp, Sp.colors.brand, RoundedCornerShape(16.dp)),
                        ) { FocusBackdrop(theme, Modifier.fillMaxSize()) }
                        Text(theme.label, style = MaterialTheme.typography.labelSmall, color = if (on) Sp.colors.brand else Sp.colors.textDim)
                    }
                }
            }

            SectionTitle("Mode")
            Row(Modifier.padding(horizontal = ScreenPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TimerMode.TIMER to "Timer", TimerMode.STOPWATCH to "Stopwatch", TimerMode.POMODORO to "Pomodoro").forEach { (mode, label) ->
                    ChoiceButton(label, s.timerMode == mode, Modifier.weight(1f)) { update { it.copy(timerMode = mode) } }
                }
            }
            if (s.timerMode != TimerMode.STOPWATCH) {
                FlowRow(Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 25, 45, 60, 90).forEach { m ->
                        ChoiceButton(formatMinutes(m), s.focusMinutes == m, Modifier.width(64.dp)) { update { it.copy(focusMinutes = m) } }
                    }
                }
                Stepper("Focus length", s.focusMinutes, 5..240, 5, ::formatMinutes) { v -> update { it.copy(focusMinutes = v) } }
            }
            if (s.timerMode == TimerMode.POMODORO) {
                Stepper("Short break", s.breakMinutes, 1..30, 1, ::formatMinutes) { v -> update { it.copy(breakMinutes = v) } }
                Stepper("Long break", s.longBreakMinutes, 5..60, 5, ::formatMinutes) { v -> update { it.copy(longBreakMinutes = v) } }
                Stepper("Rounds", s.focusRounds, 2..12, 1, Int::toString) { v -> update { it.copy(focusRounds = v) } }
            }

            SectionTitle("What are you working on?")
            OutlinedTextField(
                tag, { tag = it },
                placeholder = { Text("Maths, reading, a side project...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Sp.colors.border, focusedBorderColor = Sp.colors.brand),
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
            )
            if (recentTags.isNotEmpty()) {
                FlowRow(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentTags.forEach { t ->
                        Text(
                            t,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (t == tag) Sp.colors.onFill else Sp.colors.brand,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (t == tag) Sp.colors.brand else Sp.colors.brandSoft)
                                .clickable { tag = t }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            SectionTitle("Blocking")
            ListRow(
                if (s.focusMode == BlockMode.LISTED) "Blocked apps" else "Allowed apps",
                if (s.focusPackages.isEmpty()) "None chosen" else appCount(s.focusPackages.size),
                leading = { IconTile(R.drawable.ic_tab_blocks, Sp.colors.rose) },
                onClick = {
                    navigator.push(
                        Route.PickApps(if (s.focusMode == BlockMode.LISTED) "Block during focus" else "Allow during focus", s.focusPackages, single = false) { picked ->
                            update { it.copy(focusPackages = picked) }
                        },
                    )
                },
            ) { Icon(painterResource(R.drawable.ic_chevron), null, tint = Sp.colors.textDim, modifier = Modifier.size(18.dp)) }
            SwitchRow("Block every other app", "Only the chosen apps work.", s.focusMode == BlockMode.ALL_EXCEPT, leading = { IconTile(R.drawable.ic_lock, Sp.colors.brand) }) { on ->
                update { it.copy(focusMode = if (on) BlockMode.ALL_EXCEPT else BlockMode.LISTED) }
            }
            SwitchRow("Strict mode", "No pausing and no quitting early.", s.focusStrict, leading = { IconTile(R.drawable.ic_lock, Sp.colors.danger) }) { on -> update { it.copy(focusStrict = on) } }
            SwitchRow("Lock the home screen", "Home brings you back to the timer.", s.focusLockHome, leading = { IconTile(R.drawable.ic_tab_home, Sp.colors.flame) }) { on -> update { it.copy(focusLockHome = on) } }

            SectionTitle("Sound")
            // Three choices a row, so new sounds wrap instead of squeezing the labels.
            Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusSound.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { sound ->
                            ChoiceButton(sound.name.lowercase().replaceFirstChar(Char::uppercase), s.focusSound == sound, Modifier.weight(1f)) { update { it.copy(focusSound = sound) } }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        ChunkyButton(
            "Start",
            {
                app.scope.launch {
                    Focus.start(context, tag)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        navigator.focusMinimized = false
                        onClose()
                    }
                }
            },
            Modifier.fillMaxWidth().padding(ScreenPadding),
            kind = ButtonKind.MINT,
            icon = painterResource(R.drawable.ic_play),
            height = 58.dp,
        )
    }
}

private fun modeLine(s: Settings) = when (s.timerMode) {
    TimerMode.TIMER -> "Timer"
    TimerMode.STOPWATCH -> "Stopwatch"
    TimerMode.POMODORO -> "${s.focusRounds} rounds, ${formatMinutes(s.breakMinutes)} breaks"
}

// ---------- Running session ----------

private val tips = listOf(
    "Your distracting apps are blocked. Stay with it.",
    "Breathe in for four. Breathe out for six.",
    "Phone face down, eyes on the task.",
    "Stuck? Do the smallest next step.",
    "Every minute here counts toward your streak.",
    "Thirsty? A sip of water helps you think.",
)

/** The full-screen timer while a session runs. */
@Composable
fun FocusSession(focus: ActiveFocus, onMinimize: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(focus) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    var tip by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(12_000)
            tip = (tip + 1) % tips.size
        }
    }
    var askGiveUp by remember { mutableStateOf(false) }
    BackHandler(onBack = onMinimize)

    val clock = if (focus.running) now else focus.pausedAt
    val stopwatch = focus.timerMode == TimerMode.STOPWATCH
    val elapsed = (clock - focus.phaseStartedAt).coerceAtLeast(0)
    val left = (focus.phaseEndsAt - clock).coerceAtLeast(0)
    val total = (focus.phaseEndsAt - focus.phaseStartedAt).coerceAtLeast(1)
    val shown = if (stopwatch) elapsed else left
    val fraction = if (stopwatch) (elapsed % 3_600_000) / 3_600_000f else left.toFloat() / total
    val firstFocus = focus.tag == "First focus"
    val firstBlocked = focus.packages.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        FocusBackdrop(themeOf(focus.theme), Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassButton(R.drawable.ic_chevron, "Minimize", rotate = 90f, onClick = onMinimize)
                Spacer(Modifier.weight(1f))
                if (focus.tag.isNotBlank()) GlassLabel(focus.tag)
                Spacer(Modifier.weight(1f))
                if (focus.strict) GlassButton(R.drawable.ic_lock, "Strict mode is on") {} else Spacer(Modifier.size(44.dp))
            }
            Spacer(Modifier.weight(0.6f))
            Text(
                when {
                    !focus.running -> "PAUSED"
                    focus.phase == FocusPhase.BREAK -> "BREAK TIME"
                    focus.rounds > 1 -> "ROUND ${focus.round} OF ${focus.rounds}"
                    stopwatch -> "STOPWATCH"
                    else -> "FOCUS"
                },
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawCircle(Color.Black.copy(alpha = 0.18f), size.width / 2 - stroke)
                    drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(Color.White, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text(clockText(shown), style = NumberStyle, color = Color.White)
            }
            Spacer(Modifier.weight(0.5f))
            AnimatedContent(tip, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tip") { i ->
                GlassLabel(if (firstFocus && firstBlocked != null) "Try opening one of your blocked apps. I'll stop it!" else tips[i], big = true)
            }
            Pebble(
                when {
                    !focus.running -> Mood.SLEEPY
                    focus.phase == FocusPhase.BREAK -> Mood.HAPPY
                    else -> Mood.CALM
                },
                size = 110.dp,
            )
            Spacer(Modifier.height(12.dp))
            if (firstFocus && firstBlocked != null) {
                ChunkyButton(
                    "Try opening ${app.catalog.label(firstBlocked)}",
                    { context.packageManager.getLaunchIntentForPackage(firstBlocked)?.let(context::startActivity) },
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.SECONDARY,
                )
            } else if (stopwatch) {
                ChunkyButton("I'm done", { app.scope.launch { Focus.stopStopwatch(context) } }, Modifier.fillMaxWidth(), kind = ButtonKind.MINT)
            } else if (!focus.strict) {
                ChunkyButton(
                    if (focus.running) "Pause" else "Resume",
                    { app.scope.launch { if (focus.running) Focus.pause(context) else Focus.resume(context) } },
                    Modifier.fillMaxWidth(),
                    kind = if (focus.running) ButtonKind.SECONDARY else ButtonKind.MINT,
                    icon = painterResource(if (focus.running) R.drawable.ic_pause else R.drawable.ic_play),
                )
            }
            if (!focus.strict) {
                Text(
                    "GIVE UP",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { askGiveUp = true }.padding(14.dp),
                )
            } else {
                Text("Strict mode: this session can't end early.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(14.dp))
            }
        }
    }

    if (askGiveUp) {
        Dialog(onDismissRequest = { askGiveUp = false }) {
            ChunkyCard(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Pebble(Mood.SAD, size = 110.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Wait, don't go!", style = MaterialTheme.typography.headlineSmall, color = Sp.colors.text)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "If you stop now, you lose the bonus XP for finishing. You've got this!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sp.colors.textDim,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    ChunkyButton("Keep focusing", { askGiveUp = false }, Modifier.fillMaxWidth())
                    ChunkyButton(
                        "End session",
                        {
                            askGiveUp = false
                            app.scope.launch { Focus.giveUp(context) }
                        },
                        Modifier.fillMaxWidth(),
                        kind = ButtonKind.GHOST,
                    )
                }
            }
        }
    }
}

fun clockText(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = totalSeconds / 60 % 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun GlassButton(icon: Int, label: String, rotate: Float = 0f, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), label, tint = Color.White, modifier = Modifier.size(20.dp).rotate(rotate))
    }
}

@Composable
private fun GlassLabel(text: String, big: Boolean = false) {
    Text(
        text,
        style = if (big) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 16.dp, vertical = if (big) 12.dp else 8.dp),
    )
}

// ---------- Celebration ----------

/** The reward screen after a session: confetti, XP, streak, and a place for notes. */
@Composable
fun Celebration(sessionId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val sessions by app.dao.sessions().collectAsState(null)
    val settings by app.dao.settings().collectAsState(null)
    val all = sessions ?: return
    val s = settings ?: return
    val session = all.firstOrNull { it.id == sessionId } ?: return
    val before = remember(all, s) { gameState(all.filter { it.id != sessionId }, s) }
    val after = remember(all, s) { gameState(all, s) }
    val xpGained = after.xp - before.xp
    val streakUp = after.streak > before.streak
    val levelUp = after.level.number > before.level.number
    var notes by remember { mutableStateOf(session.notes) }
    var noteOpen by remember { mutableStateOf(false) }
    BackHandler(onBack = onDone)

    Box(Modifier.fillMaxSize().background(Sp.colors.background)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Pebble(if (session.completed) Mood.CELEBRATE else Mood.HAPPY, Modifier.popIn(), size = 170.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                if (session.completed) "Session complete!" else "Nice effort!",
                style = MaterialTheme.typography.headlineLarge,
                color = Sp.colors.text,
                modifier = Modifier.appear(150),
            )
            Text(
                if (session.completed) "You stayed with it to the end." else "Every minute counts. Next time, go all the way!",
                style = MaterialTheme.typography.titleMedium,
                color = Sp.colors.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.appear(250),
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RewardTile("FOCUS", Sp.colors.brand, Modifier.weight(1f).popIn(400)) {
                    CountUp((session.focusedMillis / 60_000).toInt(), MaterialTheme.typography.headlineSmall, Sp.colors.brand, delayMillis = 400) { "${it}m" }
                }
                RewardTile("XP", Sp.colors.goldLip, Modifier.weight(1f).popIn(550)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XpBolt(size = 22.dp)
                        CountUp(xpGained, MaterialTheme.typography.headlineSmall, Sp.colors.goldLip, delayMillis = 550) { "+$it" }
                    }
                }
                RewardTile("STREAK", Sp.colors.flame, Modifier.weight(1f).popIn(700)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Flame(size = 24.dp, lit = after.streakSafeToday)
                        Text("${after.streak}", style = MaterialTheme.typography.headlineSmall, color = Sp.colors.flame)
                    }
                }
            }
            if (streakUp || levelUp) {
                Spacer(Modifier.height(16.dp))
                ChunkyCard(Modifier.fillMaxWidth().popIn(900), fill = Sp.colors.flame.copy(alpha = 0.1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (levelUp) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Sp.colors.brand), contentAlignment = Alignment.Center) {
                                Text("${after.level.number}", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            }
                        } else {
                            Flame(size = 44.dp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                if (levelUp) "Level up!" else "Streak extended!",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (levelUp) Sp.colors.brand else Sp.colors.flame,
                            )
                            Text(
                                if (levelUp) "You reached level ${after.level.number}." else "${after.streak} day${if (after.streak == 1) "" else "s"} in a row. Come back tomorrow!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Sp.colors.text,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(noteOpen) {
                OutlinedTextField(
                    notes, { notes = it },
                    placeholder = { Text("What did you get done?") },
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Sp.colors.border, focusedBorderColor = Sp.colors.brand),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!noteOpen) {
                ChunkyButton("Add a note", { noteOpen = true }, Modifier.fillMaxWidth(), kind = ButtonKind.GHOST)
            }
            Spacer(Modifier.weight(1f))
            ChunkyButton(
                "Continue",
                {
                    if (notes != session.notes) app.scope.launch { app.dao.saveSession(session.copy(notes = notes.trim())) }
                    onDone()
                },
                Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
        Confetti(sessionId, Modifier.fillMaxSize())
    }
}

@Composable
private fun RewardTile(label: String, color: Color, modifier: Modifier, value: @Composable () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, color, RoundedCornerShape(18.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(color).padding(vertical = 4.dp),
        )
        Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) { value() }
    }
}

/** A floating chip over the tabs while a minimized session runs. Tap it to go back. */
@Composable
fun FocusChip(focus: ActiveFocus, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(focus) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val shown = if (focus.timerMode == TimerMode.STOPWATCH) now - focus.phaseStartedAt else (focus.phaseEndsAt - (if (focus.running) now else focus.pausedAt)).coerceAtLeast(0)
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Sp.colors.brand)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pebble(Mood.CALM, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            "${if (focus.phase == FocusPhase.BREAK) "Break" else "Focusing"} ${clockText(shown)}",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}
