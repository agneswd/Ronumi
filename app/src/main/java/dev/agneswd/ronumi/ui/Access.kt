package dev.agneswd.ronumi.ui

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.ChunkyCard
import dev.agneswd.ronumi.ui.design.Sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.consent.ConsentKind
import dev.agneswd.ronumi.consent.Consents
import dev.agneswd.ronumi.guard.GuardService
import dev.agneswd.ronumi.notify.HoldListener

/** Which system permissions the user has granted. */
data class Access(
    val usage: Boolean,
    val guard: Boolean,
    val notifications: Boolean,
    val listener: Boolean,
    val exactAlarms: Boolean = true,
) {
    val ready: Boolean get() = usage && guard
    val allAllowed: Boolean get() = ready && notifications && listener && exactAlarms

    companion object {
        fun read(context: Context) = Access(
            usage = context.app.usage.hasAccess(),
            guard = GuardService.isEnabled(context),
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            listener = HoldListener.isEnabled(context),
            exactAlarms = dev.agneswd.ronumi.schedule.Plans.exactAllowed(context),
        )
    }
}

/** The permission state. It reads it again each time the user comes back from the settings. */
@Composable
fun rememberAccess(): Access {
    val context = LocalContext.current
    var access by remember { mutableStateOf(Access.read(context)) }
    LifecycleResumeEffect(Unit) {
        access = Access.read(context)
        onPauseOrDispose { }
    }
    return access
}

@Composable
fun AccessRows(access: Access, includeOptional: Boolean, onlyMissing: Boolean = false) {
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    if (!onlyMissing || !access.guard) AccessRow(
        stringResource(R.string.permissions_accessibility),
        stringResource(R.string.permissions_accessibility_description),
        access.guard,
    ) { context.withConsent(ConsentKind.ACCESSIBILITY) { context.openAccessibilitySettings() } }
    if (!onlyMissing || !access.usage) AccessRow(
        stringResource(R.string.permissions_usage_access),
        stringResource(R.string.permissions_usage_description),
        access.usage,
    ) { context.withConsent(ConsentKind.USAGE) { context.openUsageSettings() } }
    if (!includeOptional) return
    if (Build.VERSION.SDK_INT >= 31 && (!onlyMissing || !access.exactAlarms)) AccessRow(
        stringResource(R.string.permissions_alarms_and_reminders),
        stringResource(R.string.permissions_alarms_description),
        access.exactAlarms,
    ) { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }
    if (!onlyMissing || !access.notifications) AccessRow(
        stringResource(R.string.permissions_notifications),
        stringResource(R.string.permissions_notifications_description),
        access.notifications,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    if (!onlyMissing || !access.listener) AccessRow(
        stringResource(R.string.permissions_notification_access),
        stringResource(R.string.permissions_listener_description),
        access.listener,
    ) { context.withConsent(ConsentKind.NOTIFICATION_ACCESS) { context.openNotificationListenerSettings() } }
}

/** Shown when Android already enabled the guard and Ronumi has no accessibility agreement. */
@Composable
fun BlockingConsentNudge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val revision by Consents.revision.collectAsState()
    var resume by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resume++
        onPauseOrDispose { }
    }
    val show = remember(revision, resume) {
        GuardService.isEnabled(context) && !Consents.granted(context, ConsentKind.ACCESSIBILITY)
    }
    if (!show) return
    ChunkyCard(
        modifier.fillMaxWidth().padding(bottom = 8.dp),
        fill = Sp.colors.danger.copy(alpha = 0.1f),
        onClick = { context.withConsent(ConsentKind.ACCESSIBILITY) { context.openAccessibilitySettings() } },
    ) {
        Column(Modifier.padding(4.dp)) {
            Text(stringResource(R.string.consent_finish_blocking), style = MaterialTheme.typography.titleMedium, color = Sp.colors.danger)
            Text(stringResource(R.string.consent_agree_before_blocks), style = MaterialTheme.typography.bodySmall, color = Sp.colors.text)
        }
    }
}

/** A line that opens usage consent. It draws nothing while usage can already be read. */
@Composable
fun UsageConsentLine(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val revision by Consents.revision.collectAsState()
    var resume by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resume++
        onPauseOrDispose { }
    }
    val show = remember(revision, resume) { !context.app.usage.canQuery() }
    if (!show) return
    Text(
        stringResource(R.string.consent_usage_needed),
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
            context.withConsent(ConsentKind.USAGE) { context.openUsageSettings() }
        }.padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = Sp.colors.brand,
    )
}

/** Opens in-app Settings when accessibility is already agreed. Otherwise it asks first. */
fun Context.openGuardSetup(fallback: () -> Unit) {
    if (Consents.granted(this, ConsentKind.ACCESSIBILITY)) fallback()
    else withConsent(ConsentKind.ACCESSIBILITY) { openAccessibilitySettings() }
}

fun Context.openAccessibilitySettings() {
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

fun Context.openUsageSettings() {
    openFirst(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
    )
}

fun Context.openNotificationListenerSettings() {
    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

@Composable
private fun AccessRow(title: String, why: String, granted: Boolean, open: () -> Unit) {
    val icon = when (title) {
        stringResource(R.string.permissions_usage_access) -> R.drawable.ic_timer
        stringResource(R.string.permissions_accessibility) -> R.drawable.ic_tab_blocks
        stringResource(R.string.permissions_notifications) -> R.drawable.ic_bell
        else -> R.drawable.ic_tag
    }
    ChunkyCard(Modifier.fillMaxWidth().padding(vertical = 5.dp), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, if (granted) Sp.colors.mint else Sp.colors.brand)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
                Text(why, style = MaterialTheme.typography.bodySmall, color = Sp.colors.textDim)
            }
            Spacer(Modifier.width(10.dp))
            if (granted) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Sp.colors.mint), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check), stringResource(R.string.permissions_allowed), tint = Sp.colors.onFill, modifier = Modifier.size(20.dp))
                }
            } else {
                ChunkyButton(stringResource(R.string.permissions_allow), open, Modifier.width(96.dp), height = 40.dp)
            }
        }
    }
}

/** Opens the first settings screen that this phone has. Some phones skip the app-specific pages. */
fun Context.openFirst(vararg intents: Intent) {
    intents.firstOrNull { runCatching { startActivity(it) }.isSuccess }
}
