package dev.agneswd.stillpoint.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.Schedule
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.game.day
import dev.agneswd.stillpoint.guard.dayBit
import dev.agneswd.stillpoint.guard.formatDuration
import dev.agneswd.stillpoint.guard.minuteText
import dev.agneswd.stillpoint.guard.time
import dev.agneswd.stillpoint.ui.design.ButtonKind
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.ChunkyCard
import dev.agneswd.stillpoint.ui.design.DayPart
import dev.agneswd.stillpoint.ui.design.DayPartIcon
import dev.agneswd.stillpoint.ui.design.ScreenTitle
import dev.agneswd.stillpoint.ui.design.Sp
import dev.agneswd.stillpoint.ui.design.appear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun PlannerScreen(navigator: Navigator) {
    val context = LocalContext.current
    val app = context.app
    val schedules by app.dao.schedules().collectAsState(emptyList())
    val sessions by app.dao.sessions().collectAsState(emptyList())
    val settings by app.dao.settings().collectAsState(null)
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val usage by produceState(0L, selected) {
        value = withContext(Dispatchers.IO) { app.usage.day(selected).totalMillis }
    }
    val goal = (settings?.focusGoalMinutes ?: 60) * 60_000L
    val byDay = remember(sessions) { sessions.groupBy { day(it.startedAt) } }
    val daySessions = byDay[selected].orEmpty().sortedBy { it.startedAt }
    val daySchedules = schedules.filter { it.days and dayBit(selected.dayOfWeek) != 0 }.sortedBy { it.startMinute }
    val missing = DayPart.entries.filter { part -> schedules.none { it.startMinute == part.start && it.endMinute == part.end } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = ScreenPadding, vertical = 14.dp), verticalAlignment = Alignment.Bottom) {
            ScreenTitle(selected.month.getDisplayName(TextStyle.FULL, androidx.compose.ui.platform.LocalLocale.current.platformLocale))
            Spacer(Modifier.width(8.dp))
            Text("${selected.year}", style = MaterialTheme.typography.headlineMedium, color = Sp.colors.textDim)
        }
        WeekStrip(selected, byDay.mapValues { (_, l) -> l.sumOf { it.focusedMillis } >= goal }) { selected = it }
        Row(Modifier.padding(horizontal = ScreenPadding, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile("Focus", formatDuration(daySessions.sumOf { it.focusedMillis }), Sp.colors.brand, Modifier.weight(1f))
            SummaryTile("Screen time", formatDuration(usage), Sp.colors.rose, Modifier.weight(1f))
        }

        SectionTitle(if (selected == LocalDate.now()) "Today" else selected.dayOfWeek.getDisplayName(TextStyle.FULL, androidx.compose.ui.platform.LocalLocale.current.platformLocale))
        if (daySchedules.isEmpty() && daySessions.isEmpty()) {
            Text(
                "Nothing planned. Add a schedule to block distractions at the same time each day.",
                style = MaterialTheme.typography.bodyMedium,
                color = Sp.colors.textDim,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
        }
        Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            daySchedules.forEachIndexed { i, schedule ->
                ScheduleCard(schedule, Modifier.appear(i * 50), onClick = { navigator.push(Route.EditSchedule(schedule)) }) { on ->
                    app.scope.launch { dev.agneswd.stillpoint.guard.PolicyActions.saveSchedule(context, schedule.copy(enabled = on)) }
                }
            }
            daySessions.forEach { session ->
                ChunkyCard(Modifier.fillMaxWidth(), fill = Sp.colors.surface, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(if (session.completed) Sp.colors.mint else Sp.colors.surfaceHigh), contentAlignment = Alignment.Center) {
                            Text(if (session.completed) "✓" else "~", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.tag.ifBlank { "Focus session" }, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                            Text("${time(session.startedAt)} to ${time(session.endedAt)}", style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                        }
                        Text(formatDuration(session.focusedMillis), style = MaterialTheme.typography.titleMedium, color = Sp.colors.brand)
                    }
                }
            }
        }

        if (missing.isNotEmpty()) {
            SectionTitle("Suggested")
            Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                missing.forEach { part ->
                    ChunkyCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DayPartIcon(part, size = 48.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${part.label} focus", style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                                Text("${minuteText(part.start)} to ${minuteText(part.end)}, every day", style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                            }
                            ChunkyButton(
                                "Add",
                                {
                                    app.scope.launch {
                                        dev.agneswd.stillpoint.guard.PolicyActions.saveSchedule(context,
                                            Schedule(name = "${part.label} focus", startMinute = part.start, endMinute = part.end, packages = settings?.focusPackages.orEmpty()),
                                        )
                                    }
                                },
                                Modifier.width(84.dp),
                                kind = ButtonKind.SECONDARY,
                                height = 40.dp,
                            )
                        }
                    }
                }
            }
        }
        ChunkyButton(
            "Add schedule",
            { navigator.push(Route.EditSchedule(null)) },
            Modifier.fillMaxWidth().padding(ScreenPadding),
            icon = painterResource(R.drawable.ic_plus),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, goalMet: Map<LocalDate, Boolean>, onSelect: (LocalDate) -> Unit) {
    val monday = selected.with(DayOfWeek.MONDAY)
    Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding - 4.dp)) {
        (0..6).map { monday.plusDays(it.toLong()) }.forEach { date ->
            val on = date == selected
            val today = date == LocalDate.now()
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 3.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) Sp.colors.brand else Color.Transparent)
                    .clickable { onSelect(date) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, androidx.compose.ui.platform.LocalLocale.current.platformLocale),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) Color.White.copy(alpha = 0.85f) else Sp.colors.textDim,
                )
                Text(
                    "${date.dayOfMonth}",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (on) Color.White else if (today) Sp.colors.brand else Sp.colors.text,
                )
                Box(
                    Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (goalMet[date] == true) (if (on) Color.White else Sp.colors.flame) else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, color: Color, modifier: Modifier) {
    ChunkyCard(modifier, fill = color.copy(alpha = 0.08f)) {
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = Sp.colors.text)
        }
    }
}

@Composable
fun ScheduleCard(schedule: Schedule, modifier: Modifier = Modifier, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    ChunkyCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DayPartIcon(dayPartAt(schedule.startMinute), size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(schedule.name, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                Text(scheduleSummary(schedule), style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim, textAlign = TextAlign.Start)
            }
            Switch(
                schedule.enabled, onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = Sp.colors.mint, uncheckedTrackColor = Sp.colors.surfaceHigh, uncheckedBorderColor = Sp.colors.border),
            )
        }
    }
}
