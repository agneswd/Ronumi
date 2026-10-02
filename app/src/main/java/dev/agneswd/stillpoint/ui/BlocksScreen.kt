package dev.agneswd.stillpoint.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.AppLimit
import dev.agneswd.stillpoint.data.BlockMode
import dev.agneswd.stillpoint.data.BlockedSite
import dev.agneswd.stillpoint.data.LimitMode
import dev.agneswd.stillpoint.data.Schedule
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.data.updateSettings
import dev.agneswd.stillpoint.guard.Rules
import dev.agneswd.stillpoint.guard.formatMinutes
import dev.agneswd.stillpoint.guard.hostOf
import dev.agneswd.stillpoint.guard.minuteText
import kotlinx.coroutines.launch
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
    val access = rememberAccess()
    var editingLimit by navigator::editingLimit
    val focus by dao.activeFocusFlow().collectAsState(null)
    val s = settings ?: return
    val update: SettingsUpdate = { change -> app.scope.launch { dao.updateSettings(change) } }

    if (s.protection && Rules(schedules = schedules, focus = focus).locked(LocalDateTime.now())) {
        LockedNotice()
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            if (!access.ready) {
                Text(
                    "Blocks need usage access and accessibility. Turn them on in Setup.",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 24.dp),
                )
            }
            SectionTitle("App limits")
        }
        items(limits, key = { "limit-" + it.packageName }) { limit ->
            ListRow(
                app.catalog.label(limit.packageName),
                "${formatMinutes(limit.minutesPerDay)} a day, ${if (limit.mode == LimitMode.STRICT) "strict" else "gentle"}",
                onClick = { editingLimit = limit },
                leading = { AppIcon(limit.packageName) },
            ) {
                Switch(limit.enabled, { on -> app.scope.launch { dao.saveLimit(limit.copy(enabled = on)) } })
            }
        }
        item {
            ListRow("Add app limit", onClick = {
                navigator.push(
                    Route.PickApps("Choose an app to limit", emptySet(), single = true) { picked ->
                        picked.firstOrNull()?.let { editingLimit = AppLimit(it, 30) }
                    },
                )
            })
        }

        item { SectionTitle("Schedules") }
        items(schedules, key = { "schedule-" + it.id }) { schedule ->
            ListRow(
                schedule.name,
                scheduleSummary(schedule),
                onClick = { navigator.push(Route.EditSchedule(schedule)) },
            ) {
                Switch(schedule.enabled, { on -> app.scope.launch { dao.saveSchedule(schedule.copy(enabled = on)) } })
            }
        }
        item { ListRow("Add schedule", onClick = { navigator.push(Route.EditSchedule(null)) }) }

        item {
            SectionTitle("Short videos")
            Text(
                "Closes the short video feed and keeps the rest of the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
            SwitchRow("YouTube Shorts", checked = s.blockYoutubeShorts) { on -> update { it.copy(blockYoutubeShorts = on) } }
            SwitchRow("Instagram Reels", checked = s.blockInstagramReels) { on -> update { it.copy(blockInstagramReels = on) } }
            SwitchRow("Snapchat Spotlight", checked = s.blockSnapchatSpotlight) { on -> update { it.copy(blockSnapchatSpotlight = on) } }
            SwitchRow("Facebook Reels", checked = s.blockFacebookReels) { on -> update { it.copy(blockFacebookReels = on) } }
        }

        item {
            SectionTitle("Websites")
            SwitchRow("Block adult sites", "A built-in list of common adult domains and words.", s.blockAdultSites) { on ->
                update { it.copy(blockAdultSites = on) }
            }
            AddSiteField { domain -> app.scope.launch { dao.addSite(BlockedSite(domain)) } }
        }
        items(sites, key = { "site-" + it.domain }) { site ->
            ListRow(site.domain) {
                TextButton(onClick = { app.scope.launch { dao.deleteSite(site) } }) { Text("Remove") }
            }
        }

        item {
            SectionTitle("Notifications")
            if (!access.listener) {
                Text(
                    "Allow notification access in Setup to hold notifications.",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
            ListRow(
                "Hold notifications from",
                if (s.heldPackages.isEmpty()) "No apps" else appCount(s.heldPackages.size),
                onClick = {
                    navigator.push(
                        Route.PickApps("Hold notifications from", s.heldPackages, single = false) { picked ->
                            update { it.copy(heldPackages = picked) }
                        },
                    )
                },
            )
            SwitchRow(
                "Hold all day",
                if (s.holdAlways) "Held at all times." else "Held only during focus and schedules.",
                s.holdAlways,
            ) { on -> update { it.copy(holdAlways = on) } }
            ListRow("Held notifications", "See what came in while you focused.", onClick = { navigator.push(Route.Held) })
        }

        item {
            SectionTitle("Strict mode")
            SwitchRow(
                "Lock Stillpoint while blocks run",
                "During focus and schedules, the settings pages that can turn off or remove Stillpoint close at once.",
                s.protection,
            ) { on -> update { it.copy(protection = on) } }
            Spacer(Modifier.height(32.dp))
        }
    }

    editingLimit?.let { limit ->
        LimitDialog(limit, isNew = limits.none { it.packageName == limit.packageName }, onDismiss = { editingLimit = null })
    }
}

/** Strict mode hides the controls while a block runs, so the user cannot undo it in a weak moment. */
@Composable
private fun LockedNotice() {
    Column(Modifier.padding(ScreenPadding).padding(top = 40.dp)) {
        Text("Blocks are locked", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "Strict mode is on and a focus round or schedule is running. You can change your blocks when it ends.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddSiteField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val host = hostOf(text)
    val submit = {
        if (host != null) {
            onAdd(host)
            text = ""
        }
    }
    Row(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text,
            { text = it },
            label = { Text("Block a site, like example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = submit, enabled = host != null) { Text("Add") }
    }
}

/** Sets or changes the daily limit of one app. */
@Composable
fun LimitDialog(limit: AppLimit, isNew: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var minutes by remember { mutableIntStateOf(limit.minutesPerDay) }
    var strict by remember { mutableStateOf(limit.mode == LimitMode.STRICT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limit for ${app.catalog.label(limit.packageName)}") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Daily time", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { minutes = (minutes - if (minutes > 60) 15 else 5).coerceAtLeast(1) }) { Text("-") }
                    Text(formatMinutes(minutes), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    TextButton(onClick = { minutes = (minutes + if (minutes >= 60) 15 else 5).coerceAtMost(12 * 60) }) { Text("+") }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Strict")
                        Text(
                            if (strict) "No extra time until midnight." else "After a 10 second wait you can take 5 more minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(strict, { strict = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                app.scope.launch { app.dao.saveLimit(limit.copy(minutesPerDay = minutes, mode = if (strict) LimitMode.STRICT else LimitMode.GENTLE)) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            if (!isNew) {
                TextButton(onClick = {
                    app.scope.launch { app.dao.deleteLimit(limit) }
                    onDismiss()
                }) { Text("Delete") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

fun scheduleSummary(schedule: Schedule): String {
    val window = if (schedule.startMinute == schedule.endMinute) "All day" else "${minuteText(schedule.startMinute)}-${minuteText(schedule.endMinute)}"
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
