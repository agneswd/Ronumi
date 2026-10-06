package dev.agneswd.ronumi.ui

import dev.agneswd.ronumi.R
import androidx.compose.ui.res.stringResource
import android.provider.Settings
import android.app.NotificationManager
import android.os.Build
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import dev.agneswd.ronumi.ui.design.Sfx
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
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.data.updateSettings
import dev.agneswd.ronumi.guard.Rules
import dev.agneswd.ronumi.ui.design.Mood
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun SettingsScreen(navigator: Navigator, onClose: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalResources.current
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
        TopBar(stringResource(R.string.settings_settings), onClose)
        Column(Modifier.weight(1f).verticalScroll(navigator.settingsScroll)) {
            SectionTitle(stringResource(R.string.settings_permissions))
            if (access.allAllowed) {
                ListRow(stringResource(R.string.settings_all_permissions_allowed), if (showPermissions) stringResource(R.string.settings_hide_details) else stringResource(R.string.settings_review_permissions), onClick = { navigator.showPermissions = !showPermissions })
            }
            if (!access.allAllowed || showPermissions) {
                Column(Modifier.padding(horizontal = ScreenPadding)) {
                    AccessRows(access, includeOptional = true, onlyMissing = !showPermissions)
                }
            }

            SectionTitle(stringResource(R.string.settings_daily_goal))
            Group {
                Stepper(stringResource(R.string.settings_focus_goal), s.focusGoalMinutes, 5..600, 5, { formatMinutes(it) }) { value ->
                    app.scope.launch { app.dao.updateSettings { it.copy(focusGoalMinutes = value) } }
                }
                ListRow(stringResource(R.string.settings_goal_days), when (s.goalDays) {
                    0b1111111 -> stringResource(R.string.settings_days_every_day)
                    0b0011111 -> stringResource(R.string.settings_days_weekdays)
                    0b1100000 -> stringResource(R.string.settings_days_weekends)
                    0 -> stringResource(R.string.settings_days_none)
                    else -> daysText(s.goalDays)
                })
                DayChoices(s.goalDays) { value -> app.scope.launch { app.dao.updateSettings { it.copy(goalDays = value) } } }
                Spacer(Modifier.height(12.dp))
            }
            Group(Modifier.padding(top = 12.dp)) {
                ListRow(stringResource(R.string.settings_productive_apps), if (s.productivePackages.isEmpty()) stringResource(R.string.settings_productive_apps_empty) else appCount(s.productivePackages.size), onClick = {
                    navigator.push(Route.PickApps(resources.getString(R.string.settings_productive_apps), s.productivePackages, single = false) { picked ->
                        app.scope.launch { app.dao.updateSettings { it.copy(productivePackages = picked) } }
                    })
                }) { AppSelectionPreview(s.productivePackages) }
            }

            SectionTitle(stringResource(R.string.settings_appearance))
            Group {
                ListRow(stringResource(R.string.settings_app_theme), stringResource(R.string.settings_theme_help))
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    listOf("SYSTEM" to stringResource(R.string.settings_system), "LIGHT" to stringResource(R.string.settings_light), "DARK" to stringResource(R.string.settings_dark)).forEach { (mode, label) ->
                        ChoiceButton(label, s.themeMode == mode, Modifier.weight(1f)) {
                            app.scope.launch { app.dao.updateSettings { it.copy(themeMode = mode) } }
                        }
                    }
                }
                ListRow(stringResource(R.string.settings_clock), stringResource(R.string.settings_clock_help))
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    listOf("SYSTEM" to stringResource(R.string.settings_system), "H12" to stringResource(R.string.settings_clock_12), "H24" to stringResource(R.string.settings_clock_24)).forEach { (mode, label) ->
                        ChoiceButton(label, s.clockFormat == mode, Modifier.weight(1f)) {
                            app.scope.launch { app.dao.updateSettings { it.copy(clockFormat = mode) } }
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.settings_sound))
            Group {
                var sounds by remember { mutableStateOf(Sfx.enabled) }
                SwitchRow(stringResource(R.string.settings_sound_effects), stringResource(R.string.settings_sound_help), sounds) { on ->
                    Sfx.enabled = on
                    sounds = on
                }
            }

            SectionTitle(stringResource(R.string.settings_notifications))
            Group {
                SwitchRow(stringResource(R.string.settings_focus_updates), stringResource(R.string.settings_focus_events_help), s.notifyFocusEvents) { on ->
                    app.scope.launch { app.dao.updateSettings { it.copy(notifyFocusEvents = on) } }
                }
                if (Build.VERSION.SDK_INT >= 36) {
                    SwitchRow(stringResource(R.string.settings_live_focus_timer), stringResource(R.string.settings_live_timer_help), s.liveFocusTimer) { on ->
                        app.scope.launch { app.dao.updateSettings { it.copy(liveFocusTimer = on) } }
                    }
                    if (s.liveFocusTimer && !context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()) {
                        ListRow(
                            stringResource(R.string.settings_allow_live_updates),
                            stringResource(R.string.settings_live_timer_permission_help),
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            },
                            trailing = { Chevron() },
                        )
                    }
                }
                SwitchRow(stringResource(R.string.settings_planned_focus_reminders), stringResource(R.string.settings_plan_reminders_help), s.notifyPlanReminders) { on ->
                    app.scope.launch { app.dao.updateSettings { it.copy(notifyPlanReminders = on) } }
                }
                SwitchRow(stringResource(R.string.settings_inbox_summaries), stringResource(R.string.settings_inbox_summary_help), s.notifyInboxSummaries) { on ->
                    app.scope.launch { app.dao.updateSettings { it.copy(notifyInboxSummaries = on) } }
                }
                ListRow(stringResource(R.string.settings_android_notification_settings), stringResource(R.string.settings_system_notifications_help), onClick = {
                    context.openFirst(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Chevron() }
            }

            dev.agneswd.ronumi.Distribution.UpdateSettings(s)

            BackupSettings(restoreLocked = locked || focus != null)

            SectionTitle(stringResource(R.string.settings_about))
            PebbleSays(
                stringResource(R.string.distribution_privacy),
                Mood.WAVE,
                Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                pebbleSize = 80.dp,
            )
            Group(Modifier.padding(top = 12.dp)) {
                ListRow(stringResource(R.string.settings_source_code), stringResource(R.string.settings_source_description, stringResource(R.string.settings_repository_name)), onClick = {
                    context.openFirst(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/agneswd/Stillpoint")))
                }) { Chevron() }
                val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ListRow(stringResource(R.string.settings_version), version)
            }
            Hint(stringResource(R.string.settings_licenses_description))
            Spacer(Modifier.height(32.dp))
        }
    }
}
