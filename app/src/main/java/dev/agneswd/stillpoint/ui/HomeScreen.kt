package dev.agneswd.stillpoint.ui

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.game.GameState
import dev.agneswd.stillpoint.game.Quest
import dev.agneswd.stillpoint.guard.formatDuration
import dev.agneswd.stillpoint.guard.formatMinutes
import dev.agneswd.stillpoint.guard.isActive
import dev.agneswd.stillpoint.guard.minuteText
import dev.agneswd.stillpoint.ui.design.ButtonKind
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.ChunkyCard
import dev.agneswd.stillpoint.ui.design.ChunkyProgress
import dev.agneswd.stillpoint.ui.design.DayPart
import dev.agneswd.stillpoint.ui.design.ScheduleIcon
import dev.agneswd.stillpoint.ui.design.LightPalette
import dev.agneswd.stillpoint.ui.design.Mood
import dev.agneswd.stillpoint.ui.design.Sp
import dev.agneswd.stillpoint.ui.design.Tag
import dev.agneswd.stillpoint.ui.design.XpBolt
import dev.agneswd.stillpoint.ui.design.appear
import dev.agneswd.stillpoint.ui.design.pulse
import dev.agneswd.stillpoint.usage.DayUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun HomeScreen(navigator: Navigator, game: GameState?) {
    val context = LocalContext.current
    val app = context.app
    val access = rememberAccess()
    val schedules by app.dao.schedules().collectAsState(emptyList())
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val today by produceState<DayUsage?>(null, refresh, access.usage) {
        value = withContext(Dispatchers.IO) { app.usage.day(LocalDate.now()) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 110.dp)) {
            GameBar(game, onOpen = { navigator.tab = Tab.PROGRESS })
            if (!access.ready) {
                SetupNudge(Modifier.padding(horizontal = ScreenPadding).appear(0)) { navigator.push(Route.Settings) }
            }
            val (mood, line) = greeting(game)
            PebbleSays(line, mood, Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp).appear(60), pebbleSize = 96.dp)
            Text(
                "Pebble wardrobe",
                Modifier.align(Alignment.End).clip(RoundedCornerShape(12.dp)).clickable { navigator.push(Route.Wardrobe) }
                    .padding(horizontal = ScreenPadding, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge, color = Sp.colors.brand,
            )
            GoalCard(game, Modifier.padding(horizontal = ScreenPadding).appear(120))
            SectionTitle("Daily quests", action = { Tag("Resets at midnight", Sp.colors.textDim) })
            QuestCard(game?.quests.orEmpty(), Modifier.padding(horizontal = ScreenPadding).appear(180))
            SectionTitle("Screen time")
            ScreenTimeCard(today, Modifier.padding(horizontal = ScreenPadding).appear(240)) { navigator.tab = Tab.PROGRESS }
            val next = remember(schedules) { nextSchedule(schedules) }
            if (next != null) {
                SectionTitle("Up next")
                ChunkyCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).appear(300), onClick = { navigator.tab = Tab.PLANNER }, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScheduleIcon(next.first.icon, next.first.startMinute, size = 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(next.first.name, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                            Text("${minuteText(next.first.startMinute)} to ${minuteText(next.first.endMinute)}", style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                        }
                        Tag(next.second, if (next.second == "Now") Sp.colors.mint else Sp.colors.brand)
                    }
                }
            }
        }
        // The big start button stays at the bottom, above the tab bar.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Cards fade out under the button instead of meeting a hard edge.
                .background(Brush.verticalGradient(0f to Sp.colors.background.copy(alpha = 0f), 0.35f to Sp.colors.background))
                .padding(start = ScreenPadding, end = ScreenPadding, top = 28.dp, bottom = 12.dp),
        ) {
            ChunkyButton(
                "Start focus",
                { navigator.push(Route.FocusSetup) },
                Modifier.fillMaxWidth().pulse(0.02f),
                kind = ButtonKind.MINT,
                icon = painterResource(R.drawable.ic_play),
                height = 58.dp,
            )
        }
    }
}

/** Pebble's mood and line for the home screen. It reacts to the streak, the goal and the time. */
private fun greeting(game: GameState?): Pair<Mood, String> {
    val hour = LocalTime.now().hour
    val feeling = game?.disposition?.feeling
    return when {
        game == null -> Mood.IDLE to "Hi there!"
        feeling == dev.agneswd.stillpoint.game.PebbleFeeling.CELEBRATE -> Mood.CELEBRATE to "You hit today's goal! I'm so proud of you."
        feeling == dev.agneswd.stillpoint.game.PebbleFeeling.PROUD -> Mood.PROUD to "Look at us keeping a rhythm. Nice work!"
        feeling == dev.agneswd.stillpoint.game.PebbleFeeling.HAPPY -> Mood.HAPPY to "Nice work so far. ${formatMinutes(game.goalMinutes - game.todayMinutes)} to go!"
        feeling == dev.agneswd.stillpoint.game.PebbleFeeling.DOWN -> Mood.SAD to "I've missed our focus time. One small session is a fresh start."
        feeling == dev.agneswd.stillpoint.game.PebbleFeeling.QUIET -> Mood.THINK to "Let's ease back in. A few focused minutes will help."
        hour >= 22 -> Mood.SLEEPY to "It's late. Put the phone down and rest. I will too."
        game.streak > 0 -> Mood.IDLE to "Our ${game.streak} day streak is still going. Ready for a little focus?"
        hour < 11 -> Mood.WAVE to "Good morning! A short focus now makes the whole day easier."
        else -> Mood.IDLE to "Ready when you are. One session at a time."
    }
}

@Composable
private fun SetupNudge(modifier: Modifier, onFix: () -> Unit) {
    ChunkyCard(modifier.fillMaxWidth().padding(bottom = 8.dp), fill = Sp.colors.danger.copy(alpha = 0.1f), onClick = onFix) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠️", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Finish setup", style = MaterialTheme.typography.titleMedium, color = Sp.colors.danger)
                Text("Stillpoint can't block anything yet. Tap to allow the permissions.", style = MaterialTheme.typography.bodySmall, color = Sp.colors.text)
            }
        }
    }
}

@Composable
private fun GoalCard(game: GameState?, modifier: Modifier) {
    val goal = game?.goalMinutes ?: 60
    val done = game?.todayMinutes ?: 0
    ChunkyCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalRing(done.toFloat() / goal, Modifier.size(112.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatMinutes(done), style = MaterialTheme.typography.headlineSmall, color = Sp.colors.text)
                    Text("of ${formatMinutes(goal)}", style = MaterialTheme.typography.labelMedium, color = Sp.colors.textDim)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("Today's focus", style = MaterialTheme.typography.titleLarge, color = Sp.colors.text)
                Spacer(Modifier.height(10.dp))
                // One dot per day this week. Full dots met the goal.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    game?.week.orEmpty().forEach { (date, minutes) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val met = minutes >= goal
                            val some = minutes > 0
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(11.dp))
                                    .background(if (met) Sp.colors.flame else if (some) Sp.colors.flame.copy(alpha = 0.3f) else Sp.colors.surfaceHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (met) Icon(painterResource(R.drawable.ic_check), null, tint = LightPalette.text, modifier = Modifier.size(14.dp))
                            }
                            Text(
                                date.dayOfWeek.name.take(1),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (date == LocalDate.now()) Sp.colors.text else Sp.colors.textDim,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A thick ring that fills up to [fraction], with content in the middle. */
@Composable
fun GoalRing(fraction: Float, modifier: Modifier, color: Color = Sp.colors.flame, content: @Composable () -> Unit) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow), label = "ring")
    val track = Sp.colors.surfaceHigh
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.width * 0.11f
            val inset = stroke / 2
            drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
fun QuestCard(quests: List<Quest>, modifier: Modifier) {
    var expanded by remember { mutableStateOf<String?>(null) }
    ChunkyCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            quests.forEach { quest ->
                Column {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Show quest details") {
                            expanded = if (expanded == quest.id) null else quest.id
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (quest.done) Sp.colors.gold else Sp.colors.gold.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (quest.done) Icon(painterResource(R.drawable.ic_check), null, tint = LightPalette.text, modifier = Modifier.size(22.dp)) else XpBolt(size = 22.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(quest.title, style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ChunkyProgress(quest.fraction, Modifier.weight(1f), color = Sp.colors.gold, height = 12.dp)
                                Spacer(Modifier.width(8.dp))
                                // A minimum width keeps the bars the same length on every row.
                                Text("${quest.progress} / ${quest.target}", Modifier.widthIn(min = 52.dp), style = MaterialTheme.typography.labelMedium, color = Sp.colors.textDim, textAlign = TextAlign.End)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("+${quest.xp} XP", style = MaterialTheme.typography.labelMedium, color = Sp.colors.text)
                    }
                    AnimatedVisibility(expanded == quest.id) {
                        Column(Modifier.padding(start = 52.dp, top = 10.dp)) {
                            Text(quest.category, style = MaterialTheme.typography.labelMedium, color = Sp.colors.brand)
                            Text(
                                quest.detail,
                                style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim,
                            )
                            Text(
                                if (quest.done) "${quest.xp} XP earned. This reward is already in your total."
                                else "${quest.xp} XP is added automatically when you finish this quest.",
                                Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim,
                            )
                        }
                    }
                }
            }
            Text("Tap a quest for its rules.", style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim)
        }
    }
}

@Composable
private fun ScreenTimeCard(today: DayUsage?, modifier: Modifier, onClick: () -> Unit) {
    ChunkyCard(modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatDuration(today?.totalMillis ?: 0), style = MaterialTheme.typography.headlineMedium, color = Sp.colors.text)
                    Text("${today?.unlocks ?: 0} unlocks today", style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    today?.perApp.orEmpty().take(3).forEach { (pkg, _) ->
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Sp.colors.background).padding(2.dp)) { AppIcon(pkg, 36.dp) }
                    }
                }
            }
            val top = today?.perApp.orEmpty().take(4)
            val total = today?.totalMillis?.coerceAtLeast(1) ?: 1
            if (top.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                // A stacked bar of the most used apps.
                Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Sp.colors.surfaceHigh)) {
                    val colors = listOf(Sp.colors.brand, Sp.colors.rose, Sp.colors.flame, Sp.colors.mint)
                    top.forEachIndexed { i, (_, ms) ->
                        Box(Modifier.weight((ms.toFloat() / total).coerceAtLeast(0.001f)).fillMaxSize().background(colors[i]))
                    }
                    val rest = 1f - top.sumOf { it.second }.toFloat() / total
                    if (rest > 0.001f) Spacer(Modifier.weight(rest))
                }
            }
        }
    }
}

fun dayPartAt(minute: Int): DayPart = when {
    minute < 12 * 60 -> DayPart.MORNING
    minute < 17 * 60 -> DayPart.AFTERNOON
    minute < 20 * 60 -> DayPart.EVENING
    else -> DayPart.NIGHT
}

/** The schedule that runs now or starts next today, with a short label for when. */
private fun nextSchedule(schedules: List<dev.agneswd.stillpoint.data.Schedule>): Pair<dev.agneswd.stillpoint.data.Schedule, String>? {
    val now = LocalDateTime.now()
    val minute = now.hour * 60 + now.minute
    val enabled = schedules.filter { it.enabled }
    enabled.firstOrNull { it.isActive(now) }?.let { return it to "Now" }
    val bit = 1 shl (now.dayOfWeek.value - 1)
    return enabled.filter { it.days and bit != 0 && it.startMinute > minute }
        .minByOrNull { it.startMinute }
        ?.let { s ->
            val wait = s.startMinute - minute
            s to if (wait < 60) "In ${wait}m" else "In ${wait / 60}h"
        }
}
