package dev.agneswd.stillpoint.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.guard.GuardService
import dev.agneswd.stillpoint.notify.HoldListener

/** Which system permissions the user has granted. */
data class Access(
    val usage: Boolean,
    val guard: Boolean,
    val notifications: Boolean,
    val listener: Boolean,
) {
    val ready: Boolean get() = usage && guard

    companion object {
        fun read(context: Context) = Access(
            usage = context.app.usage.hasAccess(),
            guard = GuardService.isEnabled(context),
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            listener = HoldListener.isEnabled(context),
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
fun AccessRows(access: Access, includeOptional: Boolean) {
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    AccessRow(
        "Usage access",
        "Shows screen time and checks app limits.",
        access.usage,
    ) {
        context.openFirst(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
    }
    AccessRow(
        "Accessibility",
        "Sees which app is open so Stillpoint can block it. On Android 13 and later, if the switch is grey, " +
            "open App info, tap the menu and allow restricted settings first.",
        access.guard,
    ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    if (!includeOptional) return
    AccessRow(
        "Notifications",
        "Shows the focus timer and tells you when a round ends.",
        access.notifications,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    AccessRow(
        "Notification access",
        "Holds notifications from the apps you choose.",
        access.listener,
    ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
}

@Composable
private fun AccessRow(title: String, why: String, granted: Boolean, open: () -> Unit) {
    ListRow(title, why) {
        if (granted) {
            Icon(painterResource(R.drawable.ic_check), "Allowed", tint = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = open) { Text("Allow") }
        }
    }
}

/** Opens the first settings screen that this phone has. Some phones skip the app-specific pages. */
fun Context.openFirst(vararg intents: Intent) {
    intents.firstOrNull { runCatching { startActivity(it) }.isSuccess }
}
