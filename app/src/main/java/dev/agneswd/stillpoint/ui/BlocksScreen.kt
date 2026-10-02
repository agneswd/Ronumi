package dev.agneswd.stillpoint.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.AppLimit
import dev.agneswd.stillpoint.data.BlockMode
import dev.agneswd.stillpoint.data.BlockedSite
import dev.agneswd.stillpoint.data.LimitMode
import dev.agneswd.stillpoint.data.Schedule
import dev.agneswd.stillpoint.data.Settings
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.data.updateSettings
import dev.agneswd.stillpoint.guard.Rules
import dev.agneswd.stillpoint.guard.formatMinutes
import dev.agneswd.stillpoint.guard.hostOf
import dev.agneswd.stillpoint.guard.minuteText
import dev.agneswd.stillpoint.ui.design.ButtonKind
import dev.agneswd.stillpoint.ui.design.ChunkyButton
import dev.agneswd.stillpoint.ui.design.ChunkyCard
import dev.agneswd.stillpoint.ui.design.Mood
import dev.agneswd.stillpoint.ui.design.Pebble
import dev.agneswd.stillpoint.ui.design.ScreenTitle
import dev.agneswd.stillpoint.ui.design.Sp
import dev.agneswd.stillpoint.ui.design.appear
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

private data class ShortsApp(val pkg: String, val name: String, val get: (Settings) -> Boolean, val set: (Settings, Boolean) -> Settings)

private val shortsApps = listOf(
    ShortsApp("com.google.android.youtube", "YouTube Shorts", { it.blockYoutubeShorts }, { s, v -> s.copy(blockYoutubeShorts = v) }),
    ShortsApp("com.instagram.android", "Instagram Reels", { it.blockInstagramReels }, { s, v -> s.copy(blockInstagramReels = v) }),
    ShortsApp("com.snapchat.android", "Snapchat Spotlight", { it.blockSnapchatSpotlight }, { s, v -> s.copy(blockSnapchatSpotlight = v) }),
    ShortsApp("com.facebook.katana", "Facebook Reels", { it.blockFacebookReels }, { s, v -> s.copy(blockFacebookReels = v) }),
)

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
    val access = rememberAccess()
    var editingLimit by navigator::editingLimit
    val s = settings ?: return
    val update: SettingsUpdate = { change -> app.scope.launch { dao.updateSettings(change) } }

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

        SectionTitle("App limits", action = {
            AddButton {
                navigator.push(Route.PickApps("Choose an app to limit", emptySet(), single = true) { picked -> picked.firstOrNull()?.let { editingLimit = AppLimit(it, 30) } })
            }
        })
        Group(Modifier.appear(0)) {
            if (limits.isEmpty()) Empty("Give your most-used apps a daily budget.")
            limits.forEach { limit ->
                ListRow(
                    app.catalog.label(limit.packageName),
                    "${formatMinutes(limit.minutesPerDay)} a day, ${if (limit.mode == LimitMode.STRICT) "strict" else "gentle"}",
                    onClick = { editingLimit = limit },
                    leading = { AppIcon(limit.packageName) },
                ) { MintSwitch(limit.enabled) { on -> app.scope.launch { dao.saveLimit(limit.copy(enabled = on)) } } }
            }
        }

        SectionTitle("Schedules", action = { AddButton { navigator.push(Route.EditSchedule(null)) } })
        Column(Modifier.padding(horizontal = ScreenPadding).appear(60), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (schedules.isEmpty()) {
                ChunkyCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) { Empty("Block apps at set times, like study hours or bedtime.") }
            }
            schedules.forEach { schedule ->
                ScheduleCard(schedule, onClick = { navigator.push(Route.EditSchedule(schedule)) }) { on ->
                    app.scope.launch { dao.saveSchedule(schedule.copy(enabled = on)) }
                }
            }
        }

        SectionTitle("Short videos")
        Group(Modifier.appear(120)) {
            shortsApps.forEach { item ->
                SwitchRow(item.name, if (item.get(s)) "The feed closes, the app stays." else null, item.get(s), leading = { AppIcon(item.pkg) }) { on -> update { item.set(it, on) } }
            }
        }

        SectionTitle("Websites")
        Group(Modifier.appear(180)) {
            SwitchRow("Block adult sites", "A built-in list of adult domains and words.", s.blockAdultSites, leading = { IconTile(R.drawable.ic_lock, Sp.colors.danger) }) { on ->
                update { it.copy(blockAdultSites = on) }
            }
            AddSiteField { domain -> app.scope.launch { dao.addSite(BlockedSite(domain)) } }
            sites.forEach { site ->
                ListRow(site.domain, leading = { IconTile(R.drawable.ic_globe, Sp.colors.brand) }) {
                    Text(
                        "REMOVE",
                        style = MaterialTheme.typography.labelLarge,
                        color = Sp.colors.danger,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { app.scope.launch { dao.deleteSite(site) } }.padding(8.dp),
                    )
                }
            }
        }

        SectionTitle("Notifications")
        Group(Modifier.appear(240)) {
            if (!access.listener) {
                Text(
                    "Allow notification access in Settings to hold notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Sp.colors.danger,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 6.dp),
                )
            }
            ListRow(
                "Hold notifications from",
                if (s.heldPackages.isEmpty()) "No apps" else appCount(s.heldPackages.size),
                leading = { IconTile(R.drawable.ic_bell, Sp.colors.flame) },
                onClick = {
                    navigator.push(Route.PickApps("Hold notifications from", s.heldPackages, single = false) { picked -> update { it.copy(heldPackages = picked) } })
                },
            ) { Chevron() }
            SwitchRow(
                "Hold all day",
                if (s.holdAlways) "Held at all times." else "Held only during focus and schedules.",
                s.holdAlways,
            ) { on -> update { it.copy(holdAlways = on) } }
            ListRow(
                "Notification inbox",
                if (held.isEmpty()) "Nothing waiting" else "${held.size} waiting",
                leading = { IconTile(R.drawable.ic_tag, Sp.colors.mint) },
                onClick = { navigator.push(Route.Held) },
            ) { Chevron() }
        }

        SectionTitle("Strict mode")
        Group(Modifier.appear(300)) {
            SwitchRow(
                "Lock Stillpoint while blocks run",
                "During focus and schedules, nobody can turn off or remove Stillpoint, and this tab locks.",
                s.protection,
                leading = { IconTile(R.drawable.ic_lock, Sp.colors.brand) },
            ) { on -> update { it.copy(protection = on) } }
        }
        Spacer(Modifier.height(32.dp))
    }

    editingLimit?.let { limit ->
        LimitDialog(limit, isNew = limits.none { it.packageName == limit.packageName }, onDismiss = { editingLimit = null })
    }
}

/** A bordered panel that groups related rows. */
@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ChunkyCard(modifier.fillMaxWidth().padding(horizontal = ScreenPadding), contentPadding = 0.dp) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
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
private fun AddButton(onClick: () -> Unit) {
    ChunkyButton("Add", onClick, Modifier.width(96.dp), kind = ButtonKind.SECONDARY, height = 38.dp, icon = painterResource(R.drawable.ic_plus))
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
        Pebble(Mood.GUARD, size = 160.dp)
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
            placeholder = { Text("Block a site, like example.com") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Sp.colors.border, focusedBorderColor = Sp.colors.brand),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        ChunkyButton("Add", submit, Modifier.width(80.dp), enabled = host != null, height = 48.dp)
    }
}

/** Sets or changes the daily limit of one app. */
@Composable
fun LimitDialog(limit: AppLimit, isNew: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    var minutes by remember { mutableIntStateOf(limit.minutesPerDay) }
    var strict by remember { mutableStateOf(limit.mode == LimitMode.STRICT) }
    Dialog(onDismissRequest = onDismiss) {
        ChunkyCard(Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(limit.packageName, 56.dp)
                Spacer(Modifier.height(10.dp))
                Text("Daily limit for ${app.catalog.label(limit.packageName)}", style = MaterialTheme.typography.titleLarge, color = Sp.colors.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChunkyButton("-", { minutes = (minutes - if (minutes > 60) 15 else 5).coerceAtLeast(1) }, Modifier.width(56.dp), kind = ButtonKind.SECONDARY)
                    Text(formatMinutes(minutes), style = MaterialTheme.typography.displaySmall, color = Sp.colors.brand, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    ChunkyButton("+", { minutes = (minutes + if (minutes >= 60) 15 else 5).coerceAtMost(12 * 60) }, Modifier.width(56.dp), kind = ButtonKind.SECONDARY)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Strict", style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                        Text(
                            if (strict) "No extra time until midnight." else "After a 10 second wait you can take 5 more minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Sp.colors.textDim,
                        )
                    }
                    MintSwitch(strict) { strict = it }
                }
                Spacer(Modifier.height(16.dp))
                ChunkyButton("Save", {
                    app.scope.launch { app.dao.saveLimit(limit.copy(minutesPerDay = minutes, mode = if (strict) LimitMode.STRICT else LimitMode.GENTLE)) }
                    onDismiss()
                }, Modifier.fillMaxWidth())
                ChunkyButton(
                    if (isNew) "Cancel" else "Delete limit",
                    {
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
