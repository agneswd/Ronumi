package dev.agneswd.ronumi.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.agneswd.ronumi.ui.design.LightPalette
import dev.agneswd.ronumi.ui.design.Sound
import dev.agneswd.ronumi.ui.design.Sfx
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
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
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.insights.UsageCategory
import dev.agneswd.ronumi.insights.usageCategory
import dev.agneswd.ronumi.insights.report
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
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.game.Badge
import dev.agneswd.ronumi.game.GameState
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.ChunkyCard
import dev.agneswd.ronumi.ui.design.ChunkyProgress
import dev.agneswd.ronumi.ui.design.Flame
import dev.agneswd.ronumi.ui.design.Medal
import dev.agneswd.ronumi.ui.design.companionMood
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.ui.design.Pebble
import dev.agneswd.ronumi.ui.design.ScreenTitle
import dev.agneswd.ronumi.insights.Report
import dev.agneswd.ronumi.ui.design.Sp
import dev.agneswd.ronumi.ui.design.XpBolt
import dev.agneswd.ronumi.ui.design.appear
import dev.agneswd.ronumi.ui.design.popIn
import dev.agneswd.ronumi.usage.DayUsage
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
        value = withContext(Dispatchers.IO) { app.usageRefresh.refresh(7) }
    }
    var badgeFilter by remember { mutableStateOf("All") }
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
            ScreenTitle(stringResource(R.string.progress_progress), Modifier.weight(1f))
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).clickable { navigator.push(Route.Settings) },
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.progress_settings), tint = Sp.colors.textDim, modifier = Modifier.size(26.dp)) }
        }

        // Level and XP.
        Row(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp).appear(0), verticalAlignment = Alignment.CenterVertically) {
            Pebble(game.companionMood(), size = 100.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.progress_level, g.level.number), style = MaterialTheme.typography.headlineMedium, color = Sp.colors.brand)
                Spacer(Modifier.height(8.dp))
                ChunkyProgress(g.level.fraction)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.progress_xp_to_level, g.level.xpInLevel, g.level.xpForNext, g.level.number + 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = Sp.colors.textDim,
                )
            }
        }

        ListRow(stringResource(R.string.progress_pebble_wardrobe), stringResource(R.string.progress_wardrobe_description),
            onClick = { navigator.push(Route.Wardrobe) },
            trailing = { Chevron() })

        // An active streak stays lit while today's next step is still pending.
        val activeStreak = g.streak > 0 || g.streakSafeToday
        val remainingStreakMinutes = (dev.agneswd.ronumi.game.STREAK_MINUTES - g.todayMinutes).coerceAtLeast(0)
        val streakToday = java.time.LocalDate.now()
        val streakMessage = when {
            g.streakSafeToday -> stringResource(R.string.progress_streak_complete)
            !dev.agneswd.ronumi.game.studyDay(s, streakToday) -> if (activeStreak) stringResource(R.string.progress_rest_streak_active) else stringResource(R.string.progress_rest_streak_empty)
            streakToday.toString() in s.frozenDays -> stringResource(R.string.progress_streak_frozen)
            activeStreak -> pluralStringResource(R.plurals.progress_streak_remaining, remainingStreakMinutes, remainingStreakMinutes)
            else -> pluralStringResource(R.plurals.progress_streak_start, dev.agneswd.ronumi.game.STREAK_MINUTES, dev.agneswd.ronumi.game.STREAK_MINUTES)
        }
        ChunkyCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp).appear(80)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flame(size = 56.dp, lit = activeStreak)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            pluralStringResource(R.plurals.progress_day_streak, g.streak, g.streak), style = MaterialTheme.typography.headlineSmall,
                            color = if (!activeStreak) Sp.colors.textDim else if (Sp.colors.dark) Sp.colors.flame else Color(0xFFA64D00),
                        )
                        Text(
                            streakMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Sp.colors.text,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_freeze), null, tint = Color(0xFF7CC8FF), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        pluralStringResource(R.plurals.progress_freeze_count, g.freezes, g.freezes),
                        style = MaterialTheme.typography.bodySmall,
                        color = Sp.colors.textDim,
                    )
                }
            }
        }

        // Numbers.
        Row(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp).appear(140), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile({ Icon(painterResource(R.drawable.ic_timer), null, tint = Sp.colors.brand, modifier = Modifier.size(24.dp)) }, formatMinutes(g.totalMinutes), stringResource(R.string.progress_total_focus), Modifier.weight(1f))
            StatTile({ Icon(painterResource(R.drawable.ic_check), null, tint = Sp.colors.mint, modifier = Modifier.size(24.dp)) }, stringResource(R.string.progress_count, g.sessions), stringResource(R.string.progress_filter_sessions), Modifier.weight(1f))
            StatTile({ XpBolt(size = 24.dp) }, stringResource(R.string.progress_count, g.xp), stringResource(R.string.progress_total_xp), Modifier.weight(1f))
        }

        SectionTitle(stringResource(R.string.progress_focus_this_week))
        ChunkyCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).appear(200)) {
            Bars(g.week.map { it.first.dayOfWeek.getDisplayName(TextStyle.NARROW, androidx.compose.ui.platform.LocalLocale.current.platformLocale) to it.second.toFloat() }, goal = g.goalMinutes.toFloat(), color = Sp.colors.brand) {
                formatMinutes(it.toInt())
            }
        }

        SectionTitle(stringResource(R.string.progress_focus_reports))
        Row(Modifier.padding(horizontal = ScreenPadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to stringResource(R.string.progress_today), 7 to stringResource(R.string.progress_week), 30 to stringResource(R.string.progress_month)).forEach { (days, label) ->
                ChoiceButton(label, period == days, Modifier.weight(1f)) { period = days }
            }
        }
        ReportCard(totals, Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 12.dp).appear(200))

        SectionTitle(stringResource(R.string.progress_screen_time))
        ChunkyCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).appear(260)) {
            Column {
                val today = days.lastOrNull()
                // The day whose apps show below. Today until the user taps another bar.
                var picked by remember { mutableStateOf<java.time.LocalDate?>(null) }
                val shown = days.firstOrNull { it.date == picked } ?: today
                val locale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
                val past = days.dropLast(1).filter { it.totalMillis > 0 }
                val average = if (past.isEmpty()) 0L else past.sumOf { it.totalMillis } / past.size
                Text(
                    if (shown == null || shown == today) stringResource(R.string.progress_usage_today, formatDuration(shown?.totalMillis ?: 0)) else stringResource(R.string.progress_usage_day, formatDuration(shown.totalMillis), shown.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)),
                    style = MaterialTheme.typography.titleLarge,
                    color = Sp.colors.text,
                )
                if (average > 0 && today != null && shown == today) {
                    val less = today.totalMillis <= average
                    Text(
                        stringResource(if (less) R.string.progress_average_less else R.string.progress_average_more, formatDuration(kotlin.math.abs(today.totalMillis - average))),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (less) Sp.colors.mintLip else Sp.colors.danger,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.progress_usage_chart_help), style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim)
                Spacer(Modifier.height(6.dp))
                Bars(
                    days.map { it.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale) to it.totalMillis / 60_000f },
                    goal = null,
                    color = Sp.colors.textDim,
                    selected = days.indexOf(shown),
                    names = days.map { it.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale) },
                    onSelect = { picked = days[it].date },
                ) {
                    formatMinutes(it.toInt())
                }
                if (shown != null && shown.perApp.isEmpty()) {
                    Text(stringResource(R.string.progress_usage_empty), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
                }
                shown?.perApp?.take(5)?.let { top ->
                    if (top.isNotEmpty()) Spacer(Modifier.height(10.dp))
                    val most = top.firstOrNull()?.second ?: 1L
                    top.forEach { (pkg, ms) ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(pkg, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.catalog.label(pkg), style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                                Spacer(Modifier.height(4.dp))
                                val category = usageCategory(pkg, s, essentials)
                                ShareBar(ms.toFloat() / most, color = when (category) {
                                    UsageCategory.PRODUCTIVE -> Sp.colors.mint
                                    UsageCategory.DISTRACTING -> Sp.colors.rose
                                    UsageCategory.OTHER -> Sp.colors.textDim
                                })
                                Text(
                                    when (category) {
                                        UsageCategory.PRODUCTIVE -> stringResource(R.string.progress_productive)
                                        UsageCategory.DISTRACTING -> stringResource(R.string.progress_distracting)
                                        UsageCategory.OTHER -> stringResource(R.string.progress_other_apps)
                                    },
                                    style = MaterialTheme.typography.labelSmall, color = Sp.colors.textDim,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(formatDuration(ms), style = MaterialTheme.typography.titleSmall, color = Sp.colors.textDim)
                        }
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.progress_badges), action = { Text(stringResource(R.string.progress_badges_earned_count, g.badges.count { it.unlocked }, g.badges.size), style = MaterialTheme.typography.titleMedium, color = Sp.colors.textDim) })
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = ScreenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("All", "Next", "Earned", "Sessions", "Time", "Rhythm", "Purpose", "Quests").forEach { filter ->
                Text(
                    stringResource(badgeFilterLabel(filter)),
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (badgeFilter == filter) Sp.colors.brandSoft else Sp.colors.background)
                        .selectable(badgeFilter == filter, role = Role.Tab) { badgeFilter = filter }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (badgeFilter == filter) Sp.colors.brand else Sp.colors.textDim,
                )
            }
        }
        val visibleBadges = when (badgeFilter) {
            "All" -> g.badges
            "Next" -> g.badges.filterNot { it.unlocked }.sortedByDescending { it.progress }.take(6)
            "Earned" -> g.badges.filter { it.unlocked }
            else -> g.badges.filter { badgeGroup(it.id) == badgeFilter }
        }
        if (visibleBadges.isEmpty()) {
            Text(
                if (badgeFilter == "Earned") stringResource(R.string.progress_badges_earned_empty) else stringResource(R.string.progress_badges_next_empty),
                Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim,
            )
        }
        Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            visibleBadges.chunked(3).forEachIndexed { row, chunk ->
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
                    Text(stringResource(badge.titleRes), style = MaterialTheme.typography.headlineSmall, color = Sp.colors.text)
                    Text(stringResource(badge.detailRes), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    if (badge.unlocked) {
                        Text(stringResource(R.string.progress_unlocked), style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                    } else {
                        ChunkyProgress(badge.progress, color = Sp.colors.gold)
                        Text(stringResource(R.string.progress_badge_percent, (badge.progress * 100).toInt()), style = MaterialTheme.typography.labelMedium, color = Sp.colors.textDim, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    ChunkyButton(stringResource(R.string.progress_nice), { openBadge = null }, Modifier.fillMaxWidth())
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
            Text(stringResource(R.string.progress_average_focus, formatMinutes(totals.averageMinutes.toInt())), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)

            val used = totals.screenMillis
            if (used > 0) {
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.progress_period_usage, formatDuration(used)), style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                Spacer(Modifier.height(8.dp))
                val categories = listOf(
                    Triple(stringResource(R.string.progress_productive), totals.productiveMillis, Sp.colors.mint),
                    Triple(stringResource(R.string.progress_distracting), totals.distractingMillis, Sp.colors.rose),
                    Triple(stringResource(R.string.progress_other_apps), totals.uncategorizedMillis, Sp.colors.textDim),
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
                Text(stringResource(R.string.progress_usage_categories_help),
                    style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim,
                    modifier = Modifier.padding(top = 8.dp))
            }

            if (totals.tags.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.progress_by_tag), style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                val most = totals.tags.maxOf { it.second }.coerceAtLeast(1)
                totals.tags.forEach { (tag, minutes) ->
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (tag == dev.agneswd.ronumi.insights.UNTAGGED_REPORT_KEY) stringResource(R.string.progress_untagged) else tag.displayName(), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.text, modifier = Modifier.width(96.dp), maxLines = 1)
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
                    Text(stringResource(R.string.progress_count, totals.notificationsHeld), style = MaterialTheme.typography.titleLarge, color = Sp.colors.text)
                    Text(stringResource(R.string.progress_notifications_held), style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim)
                }
                Column(Modifier.weight(1f)) {
                    Text(totals.timeSavedMillis?.let { formatDuration(it) } ?: stringResource(R.string.progress_soon), style = MaterialTheme.typography.titleLarge, color = Sp.colors.text)
                    Text(if (totals.timeSavedMillis == null) stringResource(R.string.progress_saved_time_help) else stringResource(R.string.progress_time_saved), style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim)
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
        Text(stringResource(R.string.progress_legend_label, label), style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim)
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
/**
 * A week of bars. The [selected] bar gets the full color. With [onSelect], each bar is a button,
 * and [names] describe the bars to screen readers.
 */
fun Bars(
    values: List<Pair<String, Float>>,
    goal: Float?,
    color: Color,
    selected: Int = values.lastIndex,
    names: List<String> = values.map { it.first },
    onSelect: ((Int) -> Unit)? = null,
    label: @Composable (Float) -> String,
) {
    if (values.isEmpty()) return
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700)) }
    val max = maxOf(values.maxOf { it.second }, goal ?: 0f, 1f)
    val track = Sp.colors.surfaceHigh
    val goalColor = Sp.colors.flame
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.progress_most, label(values.maxOf { it.second })), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Sp.colors.textDim)
            // Names the dashed line, so it does not read as the top of the scale.
            if (goal != null && goal > 0f) Text(stringResource(R.string.progress_goal, label(goal)), style = MaterialTheme.typography.labelMedium, color = goalColor)
        }
        Spacer(Modifier.height(8.dp))
        Box {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val gap = 10.dp.toPx()
            val w = (size.width - gap * (values.size - 1)) / values.size
            values.forEachIndexed { i, (_, v) ->
                val x = i * (w + gap)
                drawRoundRect(track.copy(alpha = if (onSelect != null && i == selected) 1f else 0.5f), Offset(x, 0f), Size(w, size.height), CornerRadius(8.dp.toPx()))
                // A short day still shows a small bar, so it does not look like an empty day.
                val h = (if (v > 0f) maxOf(size.height * v / max, 10.dp.toPx()) else 0f) * grow.value
                if (h > 0f) {
                    val c = if (i == selected) color else color.copy(alpha = 0.55f)
                    drawRoundRect(c, Offset(x, size.height - h), Size(w, h), CornerRadius(8.dp.toPx()))
                }
            }
            if (goal != null && goal > 0f) {
                val y = size.height * (1f - goal / max)
                drawLine(goalColor, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
        }
        if (onSelect != null) {
            // One button over each bar, so taps and screen readers both reach a day.
            Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                values.indices.forEach { i ->
                    val description = stringResource(R.string.progress_chart_description, names[i], label(values[i].second))
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                            .selectable(i == selected, role = Role.Tab) { onSelect(i) }
                            .semantics { contentDescription = description },
                    )
                }
            }
        }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            values.forEachIndexed { i, (day, _) ->
                Text(day, style = MaterialTheme.typography.labelMedium, color = if (onSelect != null && i == selected) Sp.colors.text else Sp.colors.textDim, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One drawn glyph for each badge. They sit on the medal in ink. */
private val badgeIcons = mapOf(
    "first" to R.drawable.ic_badge_first, "sessions5" to R.drawable.ic_badge_sessions5,
    "sessions10" to R.drawable.ic_badge_sessions10, "sessions25" to R.drawable.ic_badge_sessions25,
    "sessions50" to R.drawable.ic_badge_sessions50, "sessions100" to R.drawable.ic_badge_sessions100,
    "hours1" to R.drawable.ic_badge_hours1, "hours5" to R.drawable.ic_badge_hours5,
    "hours10" to R.drawable.ic_badge_hours10, "hours25" to R.drawable.ic_badge_hours25,
    "hours50" to R.drawable.ic_badge_hours50, "hours100" to R.drawable.ic_badge_hours100,
    "streak3" to R.drawable.ic_badge_streak3, "streak7" to R.drawable.ic_badge_streak7,
    "streak14" to R.drawable.ic_badge_streak14, "streak30" to R.drawable.ic_badge_streak30,
    "days7" to R.drawable.ic_badge_days7, "days30" to R.drawable.ic_badge_days30,
    "days100" to R.drawable.ic_badge_days100, "goals1" to R.drawable.ic_badge_goals1,
    "goals7" to R.drawable.ic_badge_goals7, "goals30" to R.drawable.ic_badge_goals30,
    "named1" to R.drawable.ic_badge_named1, "named10" to R.drawable.ic_badge_named10,
    "notes1" to R.drawable.ic_badge_notes1, "notes10" to R.drawable.ic_badge_notes10,
    "quests10" to R.drawable.ic_badge_quests10, "quests50" to R.drawable.ic_badge_quests50,
    "questday1" to R.drawable.ic_badge_questday1, "questday7" to R.drawable.ic_badge_questday7,
    "marathon" to R.drawable.ic_badge_marathon, "early" to R.drawable.ic_badge_early,
    "night" to R.drawable.ic_badge_night,
)

private fun badgeGroup(id: String): String = when {
    id == "first" || id.startsWith("sessions") -> "Sessions"
    id.startsWith("hours") || id in setOf("marathon", "early", "night") -> "Time"
    id.startsWith("streak") || id.startsWith("days") || id.startsWith("goals") -> "Rhythm"
    id.startsWith("named") || id.startsWith("notes") -> "Purpose"
    else -> "Quests"
}

@Composable
private fun BadgeMedal(badge: Badge, size: androidx.compose.ui.unit.Dp) {
    val (fill, lip) = when (badgeGroup(badge.id)) {
        "Sessions" -> Sp.colors.mint to Sp.colors.mintLip
        "Time" -> Sp.colors.brand to Sp.colors.brandLip
        "Rhythm" -> Sp.colors.flame to Sp.colors.flameLip
        "Purpose" -> Sp.colors.rose to Sp.colors.roseLip
        else -> Sp.colors.gold to Sp.colors.goldLip
    }
    Box(contentAlignment = Alignment.Center) {
        Medal(fill, lip, locked = !badge.unlocked, size = size)
        if (badge.unlocked) {
            Icon(painterResource(badgeIcons[badge.id] ?: R.drawable.ic_badge_goals30), null, tint = LightPalette.text, modifier = Modifier.size(size * 0.4f))
        } else {
            Icon(painterResource(R.drawable.ic_lock), stringResource(R.string.progress_locked), tint = Sp.colors.textDim, modifier = Modifier.size(size * 0.34f))
        }
    }
}

@Composable
private fun BadgeView(badge: Badge, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BadgeMedal(badge, 70.dp)
        Spacer(Modifier.height(6.dp))
        if (!badge.unlocked) {
            ChunkyProgress(badge.progress, Modifier.padding(horizontal = 8.dp), color = Sp.colors.gold, height = 6.dp)
        } else {
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(badge.titleRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (badge.unlocked) Sp.colors.text else Sp.colors.textDim,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

@androidx.annotation.StringRes
private fun badgeFilterLabel(id: String): Int = when (id) {
    "All" -> R.string.progress_filter_all
    "Next" -> R.string.progress_filter_next
    "Earned" -> R.string.progress_filter_earned
    "Sessions" -> R.string.progress_filter_sessions
    "Time" -> R.string.progress_filter_time
    "Rhythm" -> R.string.progress_filter_rhythm
    "Purpose" -> R.string.progress_filter_purpose
    else -> R.string.progress_filter_quests
}
