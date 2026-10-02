package dev.agneswd.stillpoint.notify

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.StillpointApp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.HeldNotification
import dev.agneswd.stillpoint.data.currentSettings
import dev.agneswd.stillpoint.ui.MainActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/** Delivers one private summary. The inbox retains messages until the user clears them. */
object Delivery {
    private val lock = Mutex()
    suspend fun release(context: Context) = lock.withLock {
        if (!context.app.dao.currentSettings().notifyInboxSummaries) return@withLock
        val held = context.app.dao.allHeld()
        if (held.isEmpty()) return@withLock
        val preferences = context.getSharedPreferences("delivery", Context.MODE_PRIVATE)
        val delivered = preferences.getStringSet("deliveredVersions", null) ?: held
            .filter { it.id <= preferences.getLong("lastDelivered", 0) }.map { it.deliveryVersion() }.toSet().also {
                // Preserve the old delivery state when upgrading from the row-id watermark.
                preferences.edit().putStringSet("deliveredVersions", it).remove("lastDelivered").apply()
            }
        val count = pendingNotifications(held, delivered).size
        if (count == 0) return@withLock
        val notification = NotificationCompat.Builder(context, StillpointApp.CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("$count held notifications")
            .setContentText("Tap to open your notification inbox.")
            .setContentIntent(MainActivity.pendingInbox(context)).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        if (androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            context.getSystemService(NotificationManager::class.java).notify(10, notification)
            preferences.edit().putStringSet("deliveredVersions", held.map { it.deliveryVersion() }.toSet()).apply()
        }
    }
}

/** Inbox updates keep their row id. Each new content version can produce one delivery summary. */
internal fun pendingNotifications(held: List<HeldNotification>, delivered: Set<String>): List<HeldNotification> =
    held.filter { it.deliveryVersion() !in delivered }

internal fun HeldNotification.deliveryVersion(): String {
    val content = listOf(packageName, notificationKey, title, text).joinToString("") { "${it.length}:$it" }
    val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
    return "$id:$postedAt:" + digest.joinToString("") { "%02x".format(it) }
}
