package dev.agneswd.stillpoint.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.AppLimit
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.guard.formatDuration
import dev.agneswd.stillpoint.guard.formatMinutes
import dev.agneswd.stillpoint.usage.DayUsage
import dev.agneswd.stillpoint.usage.UsageReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TodayScreen(navigator: Navigator) {
    val context = LocalContext.current
    val dao = context.app.dao
    val access = rememberAccess()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val days by produceState(emptyList<DayUsage>(), refresh, access.usage) {
        value = withContext(Dispatchers.IO) { context.app.usage.recentDays(7) }
    }
    val limits by dao.limits().collectAsState(emptyList())
    val sessions by dao.sessions().collectAsState(emptyList())
    val settings by dao.settings().collectAsState(null)
    var editing by remember { mutableStateOf<AppLimit?>(null) }

    val today = days.lastOrNull()
    val todayStart = UsageReader.startOfDay(LocalDate.now())
    val focusedToday = sessions.filter { it.startedAt >= todayStart }.sumOf { it.focusedMillis }
    val limitByPackage = limits.associateBy { it.packageName }

    LazyColumn(Modifier.fillMaxWidth()) {
        if (!access.ready) {
            item {
                SectionTitle("Finish setup")
                Text(
                    "Stillpoint needs two permissions before it can count or block anything. Both stay on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
                AccessRows(access, includeOptional = false)
            }
        }

        item {
            Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 40.dp)) {
                Text("Screen time today", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(today?.totalMillis ?: 0), style = NumberStyle)
                Text(summary(days), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
                WeekBars(days)
            }
        }

        item {
            SectionTitle("Focus")
            val goal = settings?.focusGoalMinutes ?: 0
            ListRow(
                "${formatDuration(focusedToday)} of ${formatMinutes(goal)}",
                if (focusedToday >= goal * 60_000L) "You reached today's goal." else "Today's focus goal",
                onClick = { navigator.tab = Tab.FOCUS },
            )
            ShareBar(
                if (goal == 0) 0f else focusedToday / (goal * 60_000f),
                Modifier.padding(horizontal = ScreenPadding),
            )
        }

        item { SectionTitle("Most used") }
        if (today != null && today.perApp.isEmpty()) {
            item { ListRow("Nothing yet today", if (access.usage) null else "Allow usage access to see your apps.") }
        }
        val top = today?.perApp.orEmpty().take(12)
        val most = top.firstOrNull()?.second ?: 1L
        items(top, key = { it.first }) { (pkg, millis) ->
            val limit = limitByPackage[pkg]
            Column {
                ListRow(
                    title = context.app.catalog.label(pkg),
                    subtitle = limit?.let { "Limit ${formatMinutes(it.minutesPerDay)}" + if (it.enabled) "" else " (off)" },
                    onClick = { editing = limit ?: AppLimit(pkg, 30) },
                    leading = { AppIcon(pkg) },
                    trailing = { Text(formatDuration(millis), style = MaterialTheme.typography.bodyLarge) },
                )
                ShareBar(millis.toFloat() / most, Modifier.padding(start = ScreenPadding + 52.dp, end = ScreenPadding, bottom = 4.dp))
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    editing?.let { limit ->
        LimitDialog(limit, isNew = limitByPackage[limit.packageName] == null, onDismiss = { editing = null })
    }
}

private fun summary(days: List<DayUsage>): String {
    val today = days.lastOrNull() ?: return ""
    val unlocks = "${today.unlocks} unlocks"
    val past = days.dropLast(1).filter { it.totalMillis > 0 }
    if (past.isEmpty()) return unlocks
    val average = past.sumOf { it.totalMillis } / past.size
    if (average == 0L) return unlocks
    val change = ((today.totalMillis - average) * 100.0 / average).roundToInt()
    val direction = if (change <= 0) "less" else "more"
    return "$unlocks, ${abs(change)}% $direction than your daily average"
}

/** Seven days of screen time. Today is the last bar, in the accent color. */
@Composable
private fun WeekBars(days: List<DayUsage>) {
    if (days.isEmpty()) return
    val max = days.maxOf { it.totalMillis }.coerceAtLeast(1)
    val past = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val now = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val gap = 10.dp.toPx()
        val width = (size.width - gap * (days.size - 1)) / days.size
        days.forEachIndexed { i, day ->
            val x = i * (width + gap)
            val h = (size.height * day.totalMillis / max).coerceAtLeast(3.dp.toPx())
            drawRoundRect(if (i == days.lastIndex) now else past, Offset(x, size.height - h), Size(width, h), CornerRadius(6.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        days.forEach { day ->
            Text(
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
