package dev.agneswd.ronumi.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.HeldNotification
import dev.agneswd.ronumi.data.currentSettings
import dev.agneswd.ronumi.guard.Rules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Removes notifications from the apps the user chose and keeps them in the app.
 * It holds them always, or only during focus rounds and schedules.
 */
class HoldListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.isOngoing || sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        // Group summaries repeat their children. Keep only the real messages.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val key = sbn.key
        app.scope.launch {
            val dao = app.dao
            val settings = dao.currentSettings()
            if (sbn.packageName !in settings.heldPackages) return@launch
            if (!settings.holdAlways) {
                val rules = Rules(schedules = dao.schedules().first(), focus = dao.activeFocus())
                if (!rules.locked(LocalDateTime.now())) return@launch
            }
            dao.recordHeld(
                HeldNotification(
                    packageName = sbn.packageName,
                    notificationKey = key,
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                    postedAt = sbn.postTime,
                ),
                java.time.LocalDate.now().toString(),
            )
            cancelNotification(key)
        }
    }

    companion object {
        fun isEnabled(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        fun component(context: Context) = ComponentName(context, HoldListener::class.java)
    }
}
