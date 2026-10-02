package dev.agneswd.stillpoint.notify

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.StillpointApp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.ui.MainActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Delivers one private summary. The inbox retains messages until the user clears them. */
object Delivery {
    private val lock = Mutex()
    suspend fun release(context: Context) = lock.withLock {
        val held = context.app.dao.allHeld()
        if (held.isEmpty()) return@withLock
        val preferences = context.getSharedPreferences("delivery", Context.MODE_PRIVATE)
        val latest = held.maxOf { it.id }
        if (latest <= preferences.getLong("lastDelivered", 0)) return@withLock
        val count = held.count { it.id > preferences.getLong("lastDelivered", 0) }
        val notification = NotificationCompat.Builder(context, StillpointApp.CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("$count held notifications")
            .setContentText("Tap to open your notification inbox.")
            .setContentIntent(MainActivity.pendingInbox(context)).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        if (androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            context.getSystemService(NotificationManager::class.java).notify(10, notification)
            preferences.edit().putLong("lastDelivered", latest).apply()
        }
    }
}
