package dev.agneswd.stillpoint

import dev.agneswd.stillpoint.ui.design.Sfx

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dev.agneswd.stillpoint.data.FocusPhase
import dev.agneswd.stillpoint.data.StillpointDatabase
import dev.agneswd.stillpoint.usage.AppCatalog
import dev.agneswd.stillpoint.usage.UsageReader
import dev.agneswd.stillpoint.usage.UsageRefresher
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
    val usageRefresh by lazy { UsageRefresher(this) }
    val catalog by lazy { AppCatalog(this) }
    val plus: dev.agneswd.stillpoint.plus.Plus by lazy { Distribution.createPlus(this) }

    /** For work that must finish even when the screen that started it closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Sfx.init(this)
        watchClock()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_FOCUS, getString(R.string.notification_channel_focus), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_EVENTS, getString(R.string.notification_channel_events), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
        plus
        scope.launch {
            // A checkpoint rewrites elapsed time only. Plans care about the session, phase, deadline, and pause.
            val focusKey = dao.activeFocusFlow().map { focus ->
                // Whole seconds. A checkpoint rewrites the deadline by a few milliseconds.
                focus?.let { FocusScheduleKey(it.startedAt, it.phase, it.phaseEndsAt / 1000, paused = !it.running) }
            }.distinctUntilChanged()
            combine(dao.settings().map { it.notificationDeliveryTimes }.distinctUntilChanged(), dao.schedules(), focusKey) { _, _, _ -> Unit }.collect {
                Plans.refresh(this@StillpointApp)
            }
        }
    }

    private fun watchClock() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                usageRefresh.bumpClock()
            }
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    /** Identity of the running session for alarm scheduling. Elapsed time is not part of it. */
    private data class FocusScheduleKey(
        val startedAt: Long,
        val phase: FocusPhase,
        val phaseEndsAtSeconds: Long,
        val paused: Boolean,
    )

    companion object {
        const val CHANNEL_FOCUS = "focus"
        const val CHANNEL_EVENTS = "events"
    }
}

val Context.app: StillpointApp get() = applicationContext as StillpointApp
