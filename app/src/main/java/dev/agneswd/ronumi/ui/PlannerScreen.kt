package dev.agneswd.ronumi.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Icon
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
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.Schedule
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.game.rewardDate
import dev.agneswd.ronumi.guard.dayBit
import dev.agneswd.ronumi.guard.minuteText
import dev.agneswd.ronumi.guard.time
import dev.agneswd.ronumi.ui.design.ButtonKind
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.ChunkyCard
import dev.agneswd.ronumi.ui.design.DayPart
import dev.agneswd.ronumi.ui.design.DayPartIcon
import dev.agneswd.ronumi.ui.design.ScheduleIcon
import dev.agneswd.ronumi.ui.design.ScreenTitle
import dev.agneswd.ronumi.ui.design.LightPalette
import dev.agneswd.ronumi.ui.design.Sp
import dev.agneswd.ronumi.ui.design.appear
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
    val use24 = rememberUse24Hour()
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val usage by produceState(0L, selected) {
        value = withContext(Dispatchers.IO) { app.usage.day(selected).totalMillis }
    }
    val goal = (settings?.focusGoalMinutes ?: 60) * 60_000L
    val byDay = remember(sessions) { sessions.groupBy { it.rewardDate() } }
    val daySessions = byDay[selected].orEmpty().sortedBy { it.startedAt }
    val daySchedules = schedules.filter { it.days and dayBit(selected.dayOfWeek) != 0 }.sortedBy { it.startMinute }
    val missing = DayPart.entries.filter { part -> schedules.none { it.startMinute == part.start && it.endMinute == part.end } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = ScreenPadding, vertical = 14.dp), verticalAlignment = Alignment.Bottom) {
            ScreenTitle(selected.month.getDisplayName(TextStyle.FULL, androidx.compose.ui.platform.LocalLocale.current.platformLocale))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.planner_day_number, selected.year), style = MaterialTheme.typography.headlineMedium, color = Sp.colors.textDim)
        }
        WeekStrip(selected, byDay.mapValues { (_, l) -> l.sumOf { it.focusedMillis } >= goal }) { selected = it }
        Row(Modifier.padding(horizontal = ScreenPadding, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile(stringResource(R.string.planner_focus), formatDuration(daySessions.sumOf { it.focusedMillis }), Sp.colors.brand, Modifier.weight(1f))
            SummaryTile(stringResource(R.string.planner_screen_time), formatDuration(usage), Sp.colors.rose, Modifier.weight(1f))
        }

        SectionTitle(if (selected == LocalDate.now()) stringResource(R.string.planner_today) else selected.dayOfWeek.getDisplayName(TextStyle.FULL, androidx.compose.ui.platform.LocalLocale.current.platformLocale))
        if (daySchedules.isEmpty() && daySessions.isEmpty()) {
            Text(
                stringResource(R.string.planner_schedules_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = Sp.colors.textDim,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
        }
        Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            daySchedules.forEachIndexed { i, schedule ->
                ScheduleCard(schedule, Modifier.appear(i * 50), onClick = { navigator.push(Route.EditSchedule(schedule)) }) { on ->
                    app.scope.launch { dev.agneswd.ronumi.guard.PolicyActions.saveSchedule(context, schedule.copy(enabled = on)) }
                }
            }
            daySessions.forEach { session ->
                ChunkyCard(Modifier.fillMaxWidth(), fill = Sp.colors.surface, contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(if (session.completed) Sp.colors.mint else Sp.colors.surfaceHigh), contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(if (session.completed) R.drawable.ic_check else R.drawable.ic_timer),
                                null,
                                Modifier.size(24.dp),
                                tint = if (session.completed) LightPalette.text else Sp.colors.textDim,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(session.tag.ifBlank { stringResource(R.string.planner_focus_session) }.displayName(), style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                            Text(stringResource(R.string.planner_session_time_range, time(session.startedAt, use24), time(session.endedAt, use24)), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                        }
                        Text(formatDuration(session.focusedMillis), style = MaterialTheme.typography.titleMedium, color = Sp.colors.brand)
                    }
                }
            }
        }

        if (missing.isNotEmpty()) {
            SectionTitle(stringResource(R.string.planner_suggested))
            Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                missing.forEach { part ->
                    ChunkyCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DayPartIcon(part, size = 48.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(part.scheduleNameRes), style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                                Text(stringResource(R.string.planner_schedule_time_range, minuteText(part.start, use24), minuteText(part.end, use24)), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                            }
                            ChunkyButton(
                                stringResource(R.string.planner_add),
                                {
                                    app.scope.launch {
                                        dev.agneswd.ronumi.guard.PolicyActions.saveSchedule(context,
                                            Schedule(name = part.scheduleId, startMinute = part.start, endMinute = part.end, packages = settings?.focusPackages.orEmpty()),
                                        )
                                    }
                                },
                                kind = ButtonKind.SECONDARY,
                                height = 40.dp,
                            )
                        }
                    }
                }
            }
        }
        ChunkyButton(
            stringResource(R.string.planner_add_schedule),
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
                    color = if (on) Sp.colors.onFill else Sp.colors.textDim,
                )
                Text(
                    stringResource(R.string.planner_day_number, date.dayOfMonth),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (on) Sp.colors.onFill else if (today) Sp.colors.brand else Sp.colors.text,
                )
                Box(
                    Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (goalMet[date] == true) (if (on) Sp.colors.onFill else Sp.colors.flame) else Color.Transparent),
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
            ScheduleIcon(schedule.icon, schedule.startMinute, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(schedule.name.displayName(), style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                Text(scheduleSummary(schedule, rememberUse24Hour()), style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim, textAlign = TextAlign.Start)
            }
            MintSwitch(schedule.enabled, onToggle)
        }
    }
}
