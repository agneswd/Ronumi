package dev.agneswd.stillpoint

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.agneswd.stillpoint.data.StillpointDatabase
import dev.agneswd.stillpoint.usage.AppCatalog
import dev.agneswd.stillpoint.usage.UsageReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the process-wide objects. Get it with [Context.app]. */
class StillpointApp : Application() {
    val database by lazy { StillpointDatabase.open(this) }
    val dao get() = database.dao()
    val usage by lazy { UsageReader(this, catalog) }
    val catalog by lazy { AppCatalog(this) }

    /** For work that must finish even when the screen that started it closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_FOCUS, "Focus timer", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_EVENTS, "Focus events", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    companion object {
        const val CHANNEL_FOCUS = "focus"
        const val CHANNEL_EVENTS = "events"
    }
}

val Context.app: StillpointApp get() = applicationContext as StillpointApp
