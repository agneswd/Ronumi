package dev.agneswd.stillpoint.ui

import android.provider.Settings
import android.app.NotificationManager
import android.os.Build
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import dev.agneswd.stillpoint.ui.design.Sfx
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.data.updateSettings
import dev.agneswd.stillpoint.guard.Rules
import dev.agneswd.stillpoint.guard.formatMinutes
import dev.agneswd.stillpoint.ui.design.Mood
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun SettingsScreen(navigator: Navigator, onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val access = rememberAccess()
    val showPermissions = navigator.showPermissions
    val settings by app.dao.settings().collectAsState(null)
    val schedules by app.dao.schedules().collectAsState(emptyList())
    val focus by app.dao.activeFocusFlow().collectAsState(null)
    val locked = settings?.protection == true && Rules(schedules = schedules, focus = focus).locked(LocalDateTime.now())

    // Keep the stored scroll offset until the full settings content is ready.
    val s = settings ?: return
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onClose)
        Column(Modifier.weight(1f).verticalScroll(navigator.settingsScroll)) {
            SectionTitle("Permissions")
            if (access.allAllowed) {
                ListRow("All permissions allowed", if (showPermissions) "Hide details" else "Review permissions", onClick = { navigator.showPermissions = !showPermissions })
            }
            if (!access.allAllowed || showPermissions) {
                Column(Modifier.padding(horizontal = ScreenPadding)) {
                    AccessRows(access, includeOptional = true, onlyMissing = !showPermissions)
                }
            }

            SectionTitle("Daily goal")
            Group {
                Stepper("Focus goal", s.focusGoalMinutes, 5..600, 5, { formatMinutes(it) }) { value ->
                    app.scope.launch { app.dao.updateSettings { it.copy(focusGoalMinutes = value) } }
                }
                ListRow("Goal days", daysText(s.goalDays).replaceFirstChar(Char::uppercase))
                DayChoices(s.goalDays) { value -> app.scope.launch { app.dao.updateSettings { it.copy(goalDays = value) } } }
                Spacer(Modifier.height(12.dp))
            }
            Group(Modifier.padding(top = 12.dp)) {
                ListRow("Productive apps", if (s.productivePackages.isEmpty()) "Reports count no app as productive." else appCount(s.productivePackages.size), onClick = {
                    navigator.push(Route.PickApps("Productive apps", s.productivePackages, single = false) { picked ->
                        app.scope.launch { app.dao.updateSettings { it.copy(productivePackages = picked) } }
                    })
                }) { AppSelectionPreview(s.productivePackages) }
            }

            SectionTitle("Appearance")
            Group {
                ListRow("App theme", "System follows your phone's light or dark setting.")
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (mode, label) ->
                        ChoiceButton(label, s.themeMode == mode, Modifier.weight(1f)) {
                            app.scope.launch { app.dao.updateSettings { it.copy(themeMode = mode) } }
                        }
                    }
                }
                ListRow("Clock", "System follows the time format on your phone.")
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    listOf("SYSTEM" to "System", "H12" to "12-hour", "H24" to "24-hour").forEach { (mode, label) ->
                        ChoiceButton(label, s.clockFormat == mode, Modifier.weight(1f)) {
                            app.scope.launch { app.dao.updateSettings { it.copy(clockFormat = mode) } }
                        }
                    }
                }
            }

            SectionTitle("Sound")
            Group {
                var sounds by remember { mutableStateOf(Sfx.enabled) }
                SwitchRow("Sound effects", "Focus changes, rewards, and setup. Uses the media volume.", sounds) { on ->
                    Sfx.enabled = on
                    sounds = on
                }
            }

            SectionTitle("Notifications")
            Group {
                SwitchRow("Focus updates", "Messages when focus rounds and sessions end.", s.notifyFocusEvents) { on ->
                    app.scope.launch { app.dao.updateSettings { it.copy(notifyFocusEvents = on) } }
                }
                if (Build.VERSION.SDK_INT >= 36) {
                    SwitchRow("Live focus timer", "Shows the time left in the status bar while you focus.", s.liveFocusTimer) { on ->
                        app.scope.launch { app.dao.updateSettings { it.copy(liveFocusTimer = on) } }
                    }
                    if (s.liveFocusTimer && !context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()) {
                        ListRow(
                            "Allow live updates",
                            "Android turned off live updates for Stillpoint. Tap to allow them.",
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            },
                            trailing = { Chevron() },
                        )
                    }
                }
                SwitchRow("Planned focus reminders", "Reminders for scheduled focus. Automatic starts stay enabled.", s.notifyPlanReminders) { on ->
                    app.scope.launch { app.dao.updateSettings { it.copy(notifyPlanReminders = on) } }
                }
                SwitchRow("Inbox summaries", "Alerts at your chosen delivery times. Held messages stay in your inbox.", s.notifyInboxSummaries) { on ->
                    app.scope.launch { app.dao.updateSettings { it.copy(notifyInboxSummaries = on) } }
                }
                ListRow("Android notification settings", "Control notification sound and visibility.", onClick = {
                    context.openFirst(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Chevron() }
            }

            dev.agneswd.stillpoint.Distribution.UpdateSettings(s)

            BackupSettings(restoreLocked = locked || focus != null)

            SectionTitle("About")
            PebbleSays(
                androidx.compose.ui.res.stringResource(dev.agneswd.stillpoint.R.string.distribution_privacy),
                Mood.WAVE,
                Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                pebbleSize = 80.dp,
            )
            Group(Modifier.padding(top = 12.dp)) {
                ListRow("Source code", "GPLv3 at github.com/agneswd/Stillpoint", onClick = {
                    context.openFirst(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/agneswd/Stillpoint")))
                }) { Chevron() }
                val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ListRow("Version", version)
            }
            Hint("Nunito uses the SIL Open Font License. UI sounds from the Versilian Community Sample Library and focus recordings from Freesound use CC0. License texts are included in this app.")
            Spacer(Modifier.height(32.dp))
        }
    }
}
