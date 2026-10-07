package dev.agneswd.ronumi.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.AppLimit
import dev.agneswd.ronumi.data.BlockMode
import dev.agneswd.ronumi.data.LimitMode
import dev.agneswd.ronumi.data.Schedule
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.guard.PolicyActions
import dev.agneswd.ronumi.guard.Rules
import dev.agneswd.ronumi.guard.minuteText
import dev.agneswd.ronumi.insights.limitStreak
import dev.agneswd.ronumi.ui.design.ButtonKind
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.ChunkyCard
import dev.agneswd.ronumi.ui.design.Flame
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.ui.design.Ronumi
import dev.agneswd.ronumi.ui.design.ScreenTitle
import dev.agneswd.ronumi.ui.design.Sfx
import dev.agneswd.ronumi.ui.design.Sound
import dev.agneswd.ronumi.ui.design.Sp
import dev.agneswd.ronumi.ui.design.appear
import dev.agneswd.ronumi.ui.design.popIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun BlocksScreen(navigator: Navigator) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val app = context.app
    val dao = app.dao
    val settings by dao.settings().collectAsState(null)
    val limits by dao.limits().collectAsState(emptyList())
    val schedules by dao.schedules().collectAsState(emptyList())
    val sites by dao.sites().collectAsState(emptyList())
    val held by dao.held().collectAsState(emptyList())
    val focus by dao.activeFocusFlow().collectAsState(null)
    val usage by dao.usageDays().collectAsState(emptyList())
    val access = rememberAccess()
    var editingLimit by navigator::editingLimit
    val s = settings ?: return

    if (s.protection && Rules(schedules = schedules, focus = focus).locked(LocalDateTime.now())) {
        LockedNotice()
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(stringResource(R.string.blocks_blocks), Modifier.padding(horizontal = ScreenPadding, vertical = 14.dp))
        if (!access.ready) {
            ChunkyCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), fill = Sp.colors.danger.copy(alpha = 0.1f), onClick = { context.openGuardSetup { navigator.push(Route.Settings) } }) {
                Text(stringResource(R.string.blocks_permissions_missing), style = MaterialTheme.typography.titleSmall, color = Sp.colors.danger)
            }
        }
        PauseBanner(s.pauseBlocksUntil)

        SectionTitle(stringResource(R.string.blocks_app_limits), action = {
            AddButton(stringResource(R.string.blocks_add_app_limit)) {
                navigator.push(Route.PickApps(resources.getString(R.string.blocks_limit_app_picker_title), emptySet(), single = true) { picked -> picked.firstOrNull()?.let { editingLimit = AppLimit(it, 30) } })
            }
        })
        Group(Modifier.appear(0)) {
            if (limits.isEmpty()) Empty(stringResource(R.string.blocks_limits_empty))
            limits.forEach { limit ->
                val streak = limitStreak(usage, limit.packageName)
                ListRow(
                    app.catalog.label(limit.packageName),
                    stringResource(if (limit.mode == LimitMode.STRICT) R.string.blocks_limit_strict_summary else R.string.blocks_limit_gentle_summary, formatMinutes(limit.minutesPerDay)),
                    onClick = { editingLimit = limit },
                    leading = { AppIcon(limit.packageName) },
                ) {
                    if (streak > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Flame(size = 18.dp)
                            Text(stringResource(R.string.blocks_pass_count, streak), style = MaterialTheme.typography.titleSmall, color = Sp.colors.flame)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    MintSwitch(limit.enabled) { on -> app.scope.launch { PolicyActions.changeBlocks(context) { dao.saveLimit(limit.copy(enabled = on)) } } }
                }
            }
        }

        SectionTitle(stringResource(R.string.blocks_schedules), action = { AddButton(stringResource(R.string.blocks_add_schedule)) { navigator.push(Route.EditSchedule(null)) } })
        Column(Modifier.padding(horizontal = ScreenPadding).appear(60), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (schedules.isEmpty()) {
                ChunkyCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) { Empty(stringResource(R.string.blocks_schedules_empty)) }
            }
            schedules.forEach { schedule ->
                ScheduleCard(schedule, onClick = { navigator.push(Route.EditSchedule(schedule)) }) { on ->
                    app.scope.launch { PolicyActions.saveSchedule(context, schedule.copy(enabled = on)) }
                }
            }
        }

        SectionTitle(stringResource(R.string.blocks_more_blocks))
        Group(Modifier.appear(120)) {
            val shortsOn = shortsApps.count { it.get(s) }
            ListRow(
                stringResource(R.string.blocks_short_videos),
                when {
                    shortsOn == 0 -> stringResource(if (s.youtubeStudyMode) R.string.blocks_shorts_disabled_study else R.string.blocks_off)
                    s.youtubeStudyMode -> pluralStringResource(R.plurals.blocks_shorts_enabled_study, shortsOn, shortsOn, shortsApps.size)
                    else -> pluralStringResource(R.plurals.blocks_shorts_summary, shortsOn, shortsOn, shortsApps.size)
                },
                onClick = { navigator.push(Route.ShortVideos) },
                leading = { IconTile(R.drawable.ic_video, Sp.colors.rose) },
            ) { Chevron() }
            ListRow(
                stringResource(R.string.blocks_websites),
                when {
                    s.siteAllowList -> pluralStringResource(if (s.blockAdultSites) R.plurals.blocks_sites_adult_allowed else R.plurals.blocks_sites_allowed, sites.size, sites.size)
                    sites.isNotEmpty() -> pluralStringResource(if (s.blockAdultSites) R.plurals.blocks_sites_adult_blocked else R.plurals.blocks_sites_blocked, sites.size, sites.size)
                    s.blockAdultSites -> stringResource(R.string.blocks_sites_adult)
                    else -> stringResource(R.string.blocks_off)
                },
                onClick = { navigator.push(Route.Websites) },
                leading = { IconTile(R.drawable.ic_globe, Sp.colors.brand) },
            ) { Chevron() }
            ListRow(
                stringResource(R.string.blocks_notifications),
                when {
                    s.heldPackages.isEmpty() -> stringResource(R.string.blocks_off)
                    held.isNotEmpty() -> pluralStringResource(R.plurals.blocks_waiting, held.size, held.size)
                    else -> pluralStringResource(R.plurals.blocks_held_apps, s.heldPackages.size, s.heldPackages.size)
                },
                onClick = { navigator.push(Route.Notifications) },
                leading = { IconTile(R.drawable.ic_bell, Sp.colors.flame) },
            ) { AppSelectionPreview(s.heldPackages, maxVisible = 2) }
            ListRow(
                stringResource(R.string.blocks_strict_mode),
                if (s.protection) stringResource(R.string.blocks_on) else stringResource(R.string.blocks_off),
                onClick = { navigator.push(Route.Strict) },
                leading = { IconTile(R.drawable.ic_lock, Sp.colors.mintLip) },
            ) { Chevron() }
        }

        if (s.pauseBlocksUntil <= System.currentTimeMillis()) {
            ChunkyButton(
                stringResource(R.string.blocks_pause_button),
                { app.scope.launch { pause(context, 10) } },
                Modifier.fillMaxWidth().padding(start = ScreenPadding, end = ScreenPadding, top = 28.dp),
                kind = ButtonKind.SECONDARY,
                icon = painterResource(R.drawable.ic_pause),
            )
            Hint(stringResource(R.string.blocks_pause_description), Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(32.dp))
    }

    editingLimit?.let { limit ->
        LimitDialog(limit, isNew = limits.none { it.packageName == limit.packageName }, onDismiss = { editingLimit = null })
    }
}

private suspend fun pause(context: Context, minutes: Int) {
    if (!PolicyActions.pauseBlocks(context, minutes)) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.blocks_pause_locked_message), Toast.LENGTH_LONG).show()
        }
    }
}

/** Shows how long blocks stay paused, with a button to end the pause. */
@Composable
private fun PauseBanner(until: Long) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis(), until) {
        while (value < until) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    if (until <= now) return
    val left = (until - now) / 1000
    ChunkyCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp).popIn(), fill = Sp.colors.flame.copy(alpha = 0.12f), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ronumi(Mood.SLEEPY, size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.blocks_pause_title), style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                Text(stringResource(R.string.blocks_pause_remaining, left / 60, left % 60), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.flameLip)
            }
            ChunkyButton(stringResource(R.string.blocks_resume), { context.app.scope.launch { pause(context, 0) } }, kind = ButtonKind.FLAME, height = 42.dp)
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim, modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp))
}

@Composable
fun Chevron() {
    Icon(painterResource(R.drawable.ic_chevron), null, tint = Sp.colors.textDim, modifier = Modifier.size(18.dp))
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    ChunkyButton(stringResource(R.string.blocks_add), onClick, Modifier.semantics { contentDescription = label }, kind = ButtonKind.SECONDARY, height = 38.dp, icon = painterResource(R.drawable.ic_plus))
}

@Composable
fun MintSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked,
        { on ->
            onChange(on)
        },
        colors = SwitchDefaults.colors(checkedTrackColor = Sp.colors.mint, uncheckedTrackColor = Sp.colors.surfaceHigh, uncheckedBorderColor = Sp.colors.border),
    )
}

/** Strict mode hides the controls while a block runs, so the user cannot undo it in a weak moment. */
@Composable
fun LockedNotice() {
    Column(Modifier.fillMaxSize().padding(ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Ronumi(Mood.STRICT, size = 160.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.blocks_locked_title), style = MaterialTheme.typography.headlineMedium, color = Sp.colors.text)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.blocks_locked_description),
            style = MaterialTheme.typography.titleMedium,
            color = Sp.colors.textDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1.4f))
    }
}

/** Sets or changes the daily limit of one app. */
@Composable
fun LimitDialog(limit: AppLimit, isNew: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var minutes by remember { mutableIntStateOf(limit.minutesPerDay) }
    var strict by remember { mutableStateOf(limit.mode == LimitMode.STRICT) }
    var reminder by remember { mutableIntStateOf(limit.reminderMinutes) }
    var warning by remember { mutableStateOf(false) }
    var deleteWarning by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        ChunkyCard(Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(limit.packageName, 56.dp)
                Spacer(Modifier.height(10.dp))
                val decreaseLabel = stringResource(R.string.blocks_decrease_daily_limit)
                val increaseLabel = stringResource(R.string.blocks_increase_daily_limit)
                Text(stringResource(R.string.blocks_daily_limit_for, app.catalog.label(limit.packageName)), style = MaterialTheme.typography.titleLarge, color = Sp.colors.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChunkyButton("", { minutes = (minutes - if (minutes > 60) 15 else 5).coerceAtLeast(1) }, Modifier.width(56.dp).semantics { contentDescription = decreaseLabel }, kind = ButtonKind.SECONDARY, icon = painterResource(R.drawable.ic_minus))
                    Text(formatMinutes(minutes), style = MaterialTheme.typography.displaySmall, color = Sp.colors.brand, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    ChunkyButton("", { minutes = (minutes + if (minutes >= 60) 15 else 5).coerceAtMost(12 * 60) }, Modifier.width(56.dp).semantics { contentDescription = increaseLabel }, kind = ButtonKind.SECONDARY, icon = painterResource(R.drawable.ic_plus))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceButton(stringResource(R.string.blocks_gentle), !strict, Modifier.weight(1f)) { strict = false }
                    ChoiceButton(stringResource(R.string.blocks_strict), strict, Modifier.weight(1f)) { strict = true }
                }
                Text(
                    if (strict) stringResource(R.string.blocks_strict_limit_description) else stringResource(R.string.blocks_gentle_limit_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Sp.colors.textDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.blocks_remind_me_after), style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 15, 30).forEach { option ->
                        ChunkyButton(
                            if (option == 0) stringResource(R.string.blocks_off) else pluralStringResource(R.plurals.blocks_reminder_minutes, option, option),
                            { reminder = option },
                            Modifier.weight(1f),
                            kind = if (reminder == option) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
                            height = 40.dp,
                        )
                    }
                }
                if (warning || deleteWarning) {
                    Text(
                        stringResource(R.string.blocks_limit_weaken_confirmation),
                        color = Sp.colors.danger,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                ChunkyButton(stringResource(R.string.blocks_save), {
                    if (!isNew && !warning && (minutes > limit.minutesPerDay || !strict && limit.mode == LimitMode.STRICT)) {
                        warning = true
                        return@ChunkyButton
                    }
                    app.scope.launch {
                        PolicyActions.changeBlocks(context) {
                            app.dao.saveLimit(limit.copy(minutesPerDay = minutes, reminderMinutes = reminder, mode = if (strict) LimitMode.STRICT else LimitMode.GENTLE))
                        }
                    }
                    onDismiss()
                }, Modifier.fillMaxWidth())
                ChunkyButton(
                    if (isNew) stringResource(R.string.blocks_cancel) else stringResource(R.string.blocks_delete_limit),
                    {
                        if (!isNew && !deleteWarning) { deleteWarning = true; return@ChunkyButton }
                        if (!isNew) app.scope.launch { PolicyActions.changeBlocks(context) { app.dao.deleteLimit(limit) } }
                        onDismiss()
                    },
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.GHOST,
                )
            }
        }
    }
}

@Composable
fun scheduleSummary(schedule: Schedule, use24: Boolean): String {
    val window = if (schedule.startMinute == schedule.endMinute) stringResource(R.string.blocks_all_day) else stringResource(R.string.blocks_schedule_time_range, minuteText(schedule.startMinute, use24), minuteText(schedule.endMinute, use24))
    val apps = when (schedule.mode) {
        BlockMode.LISTED -> pluralStringResource(R.plurals.schedule_apps_blocked, schedule.packages.size, schedule.packages.size)
        BlockMode.ALL_EXCEPT -> pluralStringResource(R.plurals.schedule_apps_except, schedule.packages.size, schedule.packages.size)
    }
    return stringResource(R.string.blocks_schedule_summary, window, daysText(schedule.days), apps)
}

@Composable
fun daysText(mask: Int): String = when (mask) {
    0b1111111 -> stringResource(R.string.blocks_every_day)
    0b0011111 -> stringResource(R.string.blocks_weekdays)
    0b1100000 -> stringResource(R.string.blocks_weekends)
    0 -> stringResource(R.string.blocks_no_days)
    else -> DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }
        .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}
