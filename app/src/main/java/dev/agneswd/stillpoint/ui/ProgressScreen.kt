package dev.agneswd.stillpoint.ui

import dev.agneswd.stillpoint.ui.design.Sound
import dev.agneswd.stillpoint.ui.design.Sfx
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.insights.report
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.game.Badge
import dev.agneswd.stillpoint.game.GameState
import dev.agneswd.stillpoint.guard.formatDuration
import dev.agneswd.stillpoint.guard.formatMinutes
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.ChunkyCard
import dev.agneswd.stillpoint.ui.design.ChunkyProgress
import dev.agneswd.stillpoint.ui.design.Flame
import dev.agneswd.stillpoint.ui.design.Medal
import dev.agneswd.stillpoint.ui.design.Mood
import dev.agneswd.stillpoint.ui.design.Pebble
import dev.agneswd.stillpoint.ui.design.ScreenTitle
import dev.agneswd.stillpoint.insights.Report
import dev.agneswd.stillpoint.ui.design.Sp
import dev.agneswd.stillpoint.ui.design.XpBolt
import dev.agneswd.stillpoint.ui.design.appear
import dev.agneswd.stillpoint.ui.design.popIn
import dev.agneswd.stillpoint.usage.DayUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ProgressScreen(navigator: Navigator, game: GameState?) {
    val context = LocalContext.current
    val app = context.app
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val days by produceState(emptyList<DayUsage>(), refresh) {
        value = withContext(Dispatchers.IO) { app.usage.recentDays(7) }
    }
    var openBadge by remember { mutableStateOf<Badge?>(null) }
    val sessions by app.dao.sessions().collectAsState(emptyList())
    val settings by app.dao.settings().collectAsState(null)
    val records by app.dao.usageDays().collectAsState(emptyList())
    var period by remember { mutableIntStateOf(7) }
    val s = settings ?: return
    val essentials by produceState(emptySet<String>(), refresh) {
        value = withContext(Dispatchers.IO) { app.catalog.essentials() }
    }
    val totals = remember(sessions, records, s, period, essentials) {
        report(sessions, records, s, period, essentialPackages = essentials)
    }
    val g = game ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = ScreenPadding, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Progress", Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).clickable { navigator.push(Route.Settings) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_settings), "Settings", tint = Sp.colors.textDim, modifier = Modifier.size(26.dp)) }
        }

        // Level and XP.
        Row(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp).appear(0), verticalAlignment = Alignment.CenterVertically) {
            Pebble(Mood.PROUD, size = 100.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Level ${g.level.number}", style = MaterialTheme.typography.headlineMedium, color = Sp.colors.brand)
                Spacer(Modifier.height(8.dp))
                ChunkyProgress(g.level.fraction)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${g.level.xpInLevel} / ${g.level.xpForNext} XP to level ${g.level.number + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sp.colors.textDim,
                )
            }
        }

        // Streak.
        ChunkyCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp).appear(80), fill = Sp.colors.flame.copy(alpha = 0.08f)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flame(size = 56.dp, lit = g.streakSafeToday)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${g.streak} day streak", style = MaterialTheme.typography.headlineSmall, color = Sp.colors.flame)
                        Text(
                            if (g.streakSafeToday) "Today is done. See you tomorrow!" else "Focus 10 minutes today to keep it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sp.colors.text,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("❄️", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${g.freezes} streak freeze${if (g.freezes == 1) "" else "s"}. A freeze saves your streak on a missed day. Earn one every 7 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Sp.colors.textDim,
                    )
                }
            }
        }

        // Numbers.
        Row(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp).appear(140), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile({ Icon(painterResource(R.drawable.ic_timer), null, tint = Sp.colors.brand, modifier = Modifier.size(24.dp)) }, formatMinutes(g.totalMinutes), "Total focus", Modifier.weight(1f))
            StatTile({ Icon(painterResource(R.drawable.ic_check), null, tint = Sp.colors.mint, modifier = Modifier.size(24.dp)) }, "${g.sessions}", "Sessions", Modifier.weight(1f))
            StatTile({ XpBolt(size = 24.dp) }, "${g.xp}", "Total XP", Modifier.weight(1f))
        }

        SectionTitle("Focus this week")
        ChunkyCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).appear(200)) {
            Bars(g.week.map { it.first.dayOfWeek.getDisplayName(TextStyle.NARROW, androidx.compose.ui.platform.LocalLocale.current.platformLocale) to it.second.toFloat() }, goal = g.goalMinutes.toFloat(), color = Sp.colors.brand) {
                formatMinutes(it.toInt())
            }
        }

        SectionTitle("Focus reports")
        Row(Modifier.padding(horizontal = ScreenPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "Today", 7 to "Week", 30 to "Month").forEach { (days, label) ->
                ChoiceButton(label, period == days, Modifier.weight(1f)) { period = days }
            }
        }
        ReportCard(totals, Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 12.dp).appear(200))

        SectionTitle("Screen time")
        ChunkyCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).appear(260)) {
            Column {
                val today = days.lastOrNull()
                val past = days.dropLast(1).filter { it.totalMillis > 0 }
                val average = if (past.isEmpty()) 0L else past.sumOf { it.totalMillis } / past.size
                Text(formatDuration(today?.totalMillis ?: 0) + " today", style = MaterialTheme.typography.titleLarge, color = Sp.colors.text)
                if (average > 0 && today != null) {
                    val less = today.totalMillis <= average
                    Text(
                        "${formatDuration(kotlin.math.abs(today.totalMillis - average))} ${if (less) "less" else "more"} than your daily average",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (less) Sp.colors.mintLip else Sp.colors.danger,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Bars(days.map { it.date.dayOfWeek.getDisplayName(TextStyle.NARROW, androidx.compose.ui.platform.LocalLocale.current.platformLocale) to it.totalMillis / 60_000f }, goal = null, color = Sp.colors.rose) {
                    formatMinutes(it.toInt())
                }
                today?.perApp?.take(5)?.let { top ->
                    if (top.isNotEmpty()) Spacer(Modifier.height(10.dp))
                    val most = top.firstOrNull()?.second ?: 1L
                    top.forEach { (pkg, ms) ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(pkg, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.catalog.label(pkg), style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                                Spacer(Modifier.height(4.dp))
                                ShareBar(ms.toFloat() / most, color = Sp.colors.rose)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(formatDuration(ms), style = MaterialTheme.typography.titleSmall, color = Sp.colors.textDim)
                        }
                    }
                }
            }
        }

        SectionTitle("Badges", action = { Text("${g.badges.count { it.unlocked }} / ${g.badges.size}", style = MaterialTheme.typography.titleMedium, color = Sp.colors.textDim) })
        Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            g.badges.chunked(3).forEachIndexed { row, chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    chunk.forEachIndexed { i, badge ->
                        BadgeView(badge, Modifier.weight(1f).popIn(300 + (row * 3 + i) * 60)) {
                            openBadge = badge
                        }
                    }
                    repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    openBadge?.let { badge ->
        Dialog(onDismissRequest = { openBadge = null }) {
            ChunkyCard(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BadgeMedal(badge, 96.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(badge.title, style = MaterialTheme.typography.headlineSmall, color = Sp.colors.text)
                    Text(badge.detail, style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    if (badge.unlocked) {
                        Text("Unlocked!", style = MaterialTheme.typography.titleMedium, color = Sp.colors.mintLip)
                    } else {
                        ChunkyProgress(badge.progress, color = Sp.colors.gold)
                        Text("${(badge.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Sp.colors.textDim, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    ChunkyButton("Nice", { openBadge = null }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The report for the chosen period: focus, where screen time went, tags, held notifications and time saved. */
@Composable
private fun ReportCard(totals: Report, modifier: Modifier) {
    ChunkyCard(modifier.fillMaxWidth()) {
        Column {
            Text(formatMinutes(totals.focusMinutes.toInt()), style = MaterialTheme.typography.headlineMedium, color = Sp.colors.brand)
            Text("focus, ${formatMinutes(totals.averageMinutes.toInt())} a day on average", style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)

            val used = totals.screenMillis
            if (used > 0) {
                Spacer(Modifier.height(18.dp))
                Text("${formatDuration(used)} screen time in this period", style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                Spacer(Modifier.height(8.dp))
                val categories = listOf(
                    Triple("Productive", totals.productiveMillis, Sp.colors.mint),
                    Triple("Distracting", totals.distractingMillis, Sp.colors.rose),
                    Triple("Other apps", totals.uncategorizedMillis, Sp.colors.textDim),
                )
                Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                    categories.forEach { (_, duration, color) ->
                        if (duration > 0) Box(Modifier.weight(duration.toFloat() / used).fillMaxHeight().background(color))
                    }
                }
                categories.forEach { (label, duration, color) ->
                    Spacer(Modifier.height(8.dp))
                    Legend(label, formatDuration(duration), color, Modifier.fillMaxWidth())
                }
                Text("Productive apps are your choices. Distracting apps follow your focus block list. Other apps are the rest.",
                    style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim,
                    modifier = Modifier.padding(top = 8.dp))
            }

            if (totals.tags.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("By tag", style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                val most = totals.tags.maxOf { it.second }.coerceAtLeast(1)
                totals.tags.forEach { (tag, minutes) ->
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tag, style = MaterialTheme.typography.bodyMedium, color = Sp.colors.text, modifier = Modifier.width(96.dp), maxLines = 1)
                        ShareBar(minutes.toFloat() / most, Modifier.weight(1f))
                        Text(formatMinutes(minutes.toInt()), style = MaterialTheme.typography.titleSmall, color = Sp.colors.textDim, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Sp.colors.border))
            Spacer(Modifier.height(14.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("${totals.notificationsHeld}", style = MaterialTheme.typography.titleLarge, color = Sp.colors.text)
                    Text("notifications held", style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim)
                }
                Column(Modifier.weight(1f)) {
                    Text(totals.timeSavedMillis?.let(::formatDuration) ?: "Soon", style = MaterialTheme.typography.titleLarge, color = Sp.colors.mintLip)
                    Text(if (totals.timeSavedMillis == null) "time saved, after 7 days of data" else "time saved", style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim)
                }
            }
        }
    }
}

@Composable
private fun Legend(label: String, value: String, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$label ", style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim)
        Text(value, style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
    }
}

@Composable
private fun StatTile(icon: @Composable () -> Unit, value: String, label: String, modifier: Modifier) {
    ChunkyCard(modifier, contentPadding = 12.dp) {
        Column {
            icon()
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = Sp.colors.text, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Sp.colors.textDim)
        }
    }
}

/** A small bar chart. Bars grow in when the chart first shows. [goal] draws a dashed line. */
@Composable
fun Bars(values: List<Pair<String, Float>>, goal: Float?, color: Color, label: (Float) -> String) {
    if (values.isEmpty()) return
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700)) }
    val max = maxOf(values.maxOf { it.second }, goal ?: 0f, 1f)
    val track = Sp.colors.surfaceHigh
    val goalColor = Sp.colors.flame
    Column {
        Text("Most: ${label(values.maxOf { it.second })}", style = MaterialTheme.typography.labelMedium, color = Sp.colors.textDim)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val gap = 10.dp.toPx()
            val w = (size.width - gap * (values.size - 1)) / values.size
            values.forEachIndexed { i, (_, v) ->
                val x = i * (w + gap)
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(8.dp.toPx()))
                val h = size.height * (v / max) * grow.value
                if (h > 0f) {
                    val c = if (i == values.lastIndex) color else color.copy(alpha = 0.55f)
                    drawRoundRect(c, Offset(x, size.height - h), Size(w, h), CornerRadius(8.dp.toPx()))
                }
            }
            if (goal != null && goal > 0f) {
                val y = size.height * (1f - goal / max)
                drawLine(goalColor, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            values.forEach { (day, _) ->
                Text(day, style = MaterialTheme.typography.labelMedium, color = Sp.colors.textDim, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

private val badgeLooks = mapOf(
    "first" to "🌱", "streak3" to "🔥", "streak7" to "🎯", "streak30" to "🏔️", "hours10" to "🌊",
    "hours50" to "⛰️", "marathon" to "🏃", "early" to "🌅", "night" to "🦉", "sessions50" to "🧱",
)

@Composable
private fun BadgeMedal(badge: Badge, size: androidx.compose.ui.unit.Dp) {
    val colors = listOf(Sp.colors.brand to Sp.colors.brandLip, Sp.colors.flame to Sp.colors.flameLip, Sp.colors.mint to Sp.colors.mintLip, Sp.colors.rose to Sp.colors.roseLip, Sp.colors.gold to Sp.colors.goldLip)
    val (fill, lip) = colors[kotlin.math.abs(badge.id.hashCode()) % colors.size]
    Box(contentAlignment = Alignment.Center) {
        Medal(fill, lip, locked = !badge.unlocked, size = size)
        if (badge.unlocked) {
            Text(badgeLooks[badge.id] ?: "⭐", style = if (size > 80.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall)
        } else {
            Icon(painterResource(R.drawable.ic_lock), "Locked", tint = Sp.colors.textDim, modifier = Modifier.size(size * 0.34f))
        }
    }
}

@Composable
private fun BadgeView(badge: Badge, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BadgeMedal(badge, 70.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            badge.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (badge.unlocked) Sp.colors.text else Sp.colors.textDim,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (!badge.unlocked) {
            Spacer(Modifier.height(4.dp))
            ChunkyProgress(badge.progress, Modifier.padding(horizontal = 8.dp), color = Sp.colors.gold, height = 6.dp)
        }
    }
}
