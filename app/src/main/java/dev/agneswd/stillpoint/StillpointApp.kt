package dev.agneswd.stillpoint

import dev.agneswd.stillpoint.ui.design.Sfx

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.schedule.Plans

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
        Sfx.init(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_FOCUS, "Focus timer", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_EVENTS, "Focus events", NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
        scope.launch {
            dao.settings().map { it.autoUpdateChecks }.distinctUntilChanged().collect { enabled ->
                dev.agneswd.stillpoint.update.UpdateScheduler.schedule(this@StillpointApp, enabled)
            }
        }
        scope.launch {
            combine(dao.settings(), dao.schedules(), dao.activeFocusFlow()) { _, _, _ -> Unit }.collect {
                Plans.refresh(this@StillpointApp)
            }
        }
    }

    companion object {
        const val CHANNEL_FOCUS = "focus"
        const val CHANNEL_EVENTS = "events"
    }
}

val Context.app: StillpointApp get() = applicationContext as StillpointApp
