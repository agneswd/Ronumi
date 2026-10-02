package dev.agneswd.stillpoint.ui

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
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.AppLimit
import dev.agneswd.stillpoint.data.BlockMode
import dev.agneswd.stillpoint.data.LimitMode
import dev.agneswd.stillpoint.data.Schedule
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.guard.PolicyActions
import dev.agneswd.stillpoint.guard.Rules
import dev.agneswd.stillpoint.guard.formatMinutes
import dev.agneswd.stillpoint.guard.minuteText
import dev.agneswd.stillpoint.insights.limitStreak
import dev.agneswd.stillpoint.ui.design.ButtonKind
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.ChunkyCard
import dev.agneswd.stillpoint.ui.design.Flame
import dev.agneswd.stillpoint.ui.design.Mood
import dev.agneswd.stillpoint.ui.design.Pebble
import dev.agneswd.stillpoint.ui.design.ScreenTitle
import dev.agneswd.stillpoint.ui.design.Sp
import dev.agneswd.stillpoint.ui.design.appear
import dev.agneswd.stillpoint.ui.design.popIn
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
        ScreenTitle("Blocks", Modifier.padding(horizontal = ScreenPadding, vertical = 14.dp))
        if (!access.ready) {
            ChunkyCard(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), fill = Sp.colors.danger.copy(alpha = 0.1f), onClick = { navigator.push(Route.Settings) }) {
                Text("Blocks need usage access and accessibility. Tap to allow them.", style = MaterialTheme.typography.titleSmall, color = Sp.colors.danger)
            }
        }
        PauseBanner(s.pauseBlocksUntil)

        SectionTitle("App limits", action = {
            AddButton("Add app limit") {
                navigator.push(Route.PickApps("Choose an app to limit", emptySet(), single = true) { picked -> picked.firstOrNull()?.let { editingLimit = AppLimit(it, 30) } })
            }
        })
        Group(Modifier.appear(0)) {
            if (limits.isEmpty()) Empty("Give your most-used apps a daily budget.")
            limits.forEach { limit ->
                val streak = limitStreak(usage, limit.packageName)
                ListRow(
                    app.catalog.label(limit.packageName),
                    "${formatMinutes(limit.minutesPerDay)} a day, ${if (limit.mode == LimitMode.STRICT) "strict" else "gentle"}",
                    onClick = { editingLimit = limit },
                    leading = { AppIcon(limit.packageName) },
                ) {
                    if (streak > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Flame(size = 18.dp)
                            Text("$streak", style = MaterialTheme.typography.titleSmall, color = Sp.colors.flame)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    MintSwitch(limit.enabled) { on -> app.scope.launch { dao.saveLimit(limit.copy(enabled = on)) } }
                }
            }
        }

        SectionTitle("Schedules", action = { AddButton("Add schedule") { navigator.push(Route.EditSchedule(null)) } })
        Column(Modifier.padding(horizontal = ScreenPadding).appear(60), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (schedules.isEmpty()) {
                ChunkyCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) { Empty("Block apps at set times, like study hours or bedtime.") }
            }
            schedules.forEach { schedule ->
                ScheduleCard(schedule, onClick = { navigator.push(Route.EditSchedule(schedule)) }) { on ->
                    app.scope.launch { PolicyActions.saveSchedule(context, schedule.copy(enabled = on)) }
                }
            }
        }

        SectionTitle("More blocks")
        Group(Modifier.appear(120)) {
            val shortsOn = shortsApps.count { it.get(s) }
            ListRow(
                "Short videos",
                listOfNotNull(
                    if (shortsOn == 0) "Off" else "$shortsOn of ${shortsApps.size} feeds closed",
                    "study mode".takeIf { s.youtubeStudyMode },
                ).joinToString(", "),
                onClick = { navigator.push(Route.ShortVideos) },
                leading = { IconTile(R.drawable.ic_video, Sp.colors.rose) },
            ) { Chevron() }
            ListRow(
                "Websites",
                listOfNotNull(
                    "adult sites".takeIf { s.blockAdultSites },
                    when {
                        s.siteAllowList -> "only ${sites.size} allowed"
                        sites.isNotEmpty() -> "${sites.size} blocked"
                        else -> null
                    },
                ).joinToString(", ").ifEmpty { "Off" }.replaceFirstChar(Char::uppercase),
                onClick = { navigator.push(Route.Websites) },
                leading = { IconTile(R.drawable.ic_globe, Sp.colors.brand) },
            ) { Chevron() }
            ListRow(
                "Notifications",
                when {
                    s.heldPackages.isEmpty() -> "Off"
                    held.isNotEmpty() -> "${held.size} waiting"
                    else -> "Holding from ${appCount(s.heldPackages.size)}"
                },
                onClick = { navigator.push(Route.Notifications) },
                leading = { IconTile(R.drawable.ic_bell, Sp.colors.flame) },
            ) { Chevron() }
            ListRow(
                "Strict mode",
                if (s.protection) "On" else "Off",
                onClick = { navigator.push(Route.Strict) },
                leading = { IconTile(R.drawable.ic_lock, Sp.colors.mintLip) },
            ) { Chevron() }
        }

        if (s.pauseBlocksUntil <= System.currentTimeMillis()) {
            ChunkyButton(
                "Pause blocks for 10 minutes",
                { app.scope.launch { pause(context, 10) } },
                Modifier.fillMaxWidth().padding(start = ScreenPadding, end = ScreenPadding, top = 28.dp),
                kind = ButtonKind.SECONDARY,
                icon = painterResource(R.drawable.ic_pause),
            )
            Hint("Limits, schedules and content blocks wait. Focus sessions stay blocked.", Modifier.align(Alignment.CenterHorizontally))
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
            Toast.makeText(context, "Blocks cannot pause during a strict block.", Toast.LENGTH_LONG).show()
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
            Pebble(Mood.SLEEPY, size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Blocks are paused", style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                Text("%d:%02d left".format(left / 60, left % 60), style = MaterialTheme.typography.bodyMedium, color = Sp.colors.flameLip)
            }
            ChunkyButton("Resume", { context.app.scope.launch { pause(context, 0) } }, kind = ButtonKind.FLAME, height = 42.dp)
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
    ChunkyButton("Add", onClick, Modifier.semantics { contentDescription = label }, kind = ButtonKind.SECONDARY, height = 38.dp, icon = painterResource(R.drawable.ic_plus))
}

@Composable
fun MintSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = Sp.colors.mint, uncheckedTrackColor = Sp.colors.surfaceHigh, uncheckedBorderColor = Sp.colors.border),
    )
}

/** Strict mode hides the controls while a block runs, so the user cannot undo it in a weak moment. */
@Composable
private fun LockedNotice() {
    Column(Modifier.fillMaxSize().padding(ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Pebble(Mood.STRICT, size = 160.dp)
        Spacer(Modifier.height(16.dp))
        Text("Blocks are locked", style = MaterialTheme.typography.headlineMedium, color = Sp.colors.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Strict mode is on and a focus session or schedule is running. You can change your blocks when it ends.",
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
                Text("Daily limit for ${app.catalog.label(limit.packageName)}", style = MaterialTheme.typography.titleLarge, color = Sp.colors.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChunkyButton("-", { minutes = (minutes - if (minutes > 60) 15 else 5).coerceAtLeast(1) }, Modifier.width(56.dp).semantics { contentDescription = "Decrease daily limit" }, kind = ButtonKind.SECONDARY)
                    Text(formatMinutes(minutes), style = MaterialTheme.typography.displaySmall, color = Sp.colors.brand, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    ChunkyButton("+", { minutes = (minutes + if (minutes >= 60) 15 else 5).coerceAtMost(12 * 60) }, Modifier.width(56.dp).semantics { contentDescription = "Increase daily limit" }, kind = ButtonKind.SECONDARY)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceButton("Gentle", !strict, Modifier.weight(1f)) { strict = false }
                    ChoiceButton("Strict", strict, Modifier.weight(1f)) { strict = true }
                }
                Text(
                    if (strict) "No extra time until midnight." else "Wait 10 seconds, then use a pass for 5 more minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sp.colors.textDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text("Remind me after", style = MaterialTheme.typography.titleSmall, color = Sp.colors.text)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 15, 30).forEach { option ->
                        ChunkyButton(
                            if (option == 0) "Off" else "${option}m",
                            { reminder = option },
                            Modifier.weight(1f),
                            kind = if (reminder == option) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
                            height = 40.dp,
                        )
                    }
                }
                if (warning || deleteWarning) {
                    Text(
                        "This weakens your limit. Tap again to confirm.",
                        color = Sp.colors.danger,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                ChunkyButton("Save", {
                    if (!isNew && !warning && (minutes > limit.minutesPerDay || !strict && limit.mode == LimitMode.STRICT)) {
                        warning = true
                        return@ChunkyButton
                    }
                    app.scope.launch { app.dao.saveLimit(limit.copy(minutesPerDay = minutes, reminderMinutes = reminder, mode = if (strict) LimitMode.STRICT else LimitMode.GENTLE)) }
                    onDismiss()
                }, Modifier.fillMaxWidth())
                ChunkyButton(
                    if (isNew) "Cancel" else "Delete limit",
                    {
                        if (!isNew && !deleteWarning) { deleteWarning = true; return@ChunkyButton }
                        if (!isNew) app.scope.launch { app.dao.deleteLimit(limit) }
                        onDismiss()
                    },
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.GHOST,
                )
            }
        }
    }
}

fun scheduleSummary(schedule: Schedule): String {
    val window = if (schedule.startMinute == schedule.endMinute) "All day" else "${minuteText(schedule.startMinute)} to ${minuteText(schedule.endMinute)}"
    val apps = when (schedule.mode) {
        BlockMode.LISTED -> "${appCount(schedule.packages.size)} blocked"
        BlockMode.ALL_EXCEPT -> "all but ${appCount(schedule.packages.size)} blocked"
    }
    return "$window, ${daysText(schedule.days)}, $apps"
}

fun daysText(mask: Int): String = when (mask) {
    0b1111111 -> "every day"
    0b0011111 -> "weekdays"
    0b1100000 -> "weekends"
    0 -> "no days"
    else -> DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }
        .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}
