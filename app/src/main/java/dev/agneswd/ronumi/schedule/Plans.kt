package dev.agneswd.ronumi.schedule

import dev.agneswd.ronumi.guard.freeSchedules
import dev.agneswd.ronumi.plus.PlusFeature

import dev.agneswd.ronumi.ui.displayName
import dev.agneswd.ronumi.focus.remainingMillis
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.RonumiApp
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.currentSettings
import dev.agneswd.ronumi.focus.Focus
import dev.agneswd.ronumi.guard.dayBit
import dev.agneswd.ronumi.notify.Delivery
import dev.agneswd.ronumi.ui.MainActivity
import dev.agneswd.ronumi.widget.Widgets
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

/** Schedules persisted plans, notification batches, and focus phase boundaries. */
object Plans {
    private val lock = Mutex()
    private const val PLAN = "plan"
    private const val DELIVERY = "delivery"
    private const val PHASE = "phase"
    private val alarms = HashMap<String, ArmedAlarm>()
    private var invalidated = false

    /** The next refresh registers every alarm again. Use this after boot, clock, zone, or permission changes. */
    suspend fun invalidate() = lock.withLock { invalidated = true }

    private suspend fun forget(kind: String) = lock.withLock { alarms[kind] = forgetAlarm() }

    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Resolves local times before comparing instants. An overlapping time runs once, at its first occurrence. */
    fun nextTime(minutes: Set<Int>, days: Int = 127, now: ZonedDateTime = ZonedDateTime.now()): Long? {
        return (0..7).flatMap { offset ->
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (days and dayBit(date.dayOfWeek) == 0) emptyList() else minutes.map {
                date.atTime(it / 60, it % 60).atZone(now.zone).toInstant()
            }
        }.filter { it > now.toInstant() }.minOrNull()?.toEpochMilli()
    }

    suspend fun refresh(context: Context) = lock.withLock {
        // Backup and other data changes call this before they redraw widgets.
        Widgets.invalidateContent()
        val dao = context.app.dao
        val manager = context.getSystemService(AlarmManager::class.java)
        val preferences = context.getSharedPreferences("plans", Context.MODE_PRIVATE)
        val candidates = freeSchedules(dao.allSchedules(), context.app.plus.has(PlusFeature.UNLIMITED_SCHEDULES)).filter { it.enabled && it.startFocus }.mapNotNull { s ->
            val snoozed = preferences.getLong("snooze-${s.id}", 0)
            val at = if (snoozed > System.currentTimeMillis()) snoozed else nextTime(setOf(s.startMinute), s.days)
            at?.let { s to it }
        }
        val s = dao.currentSettings()
        val plan = candidates.minByOrNull { it.second }
        val delivery = nextTime(s.notificationDeliveryTimes.mapNotNull(String::toIntOrNull).toSet())
        val focus = dao.activeFocus()?.takeIf { it.running }
        val exact = exactAllowed(context)
        val dirty = invalidated
        applyAlarm(context, manager, PLAN, plan?.let { AlarmTarget(it.second, it.first.id, exact) }, dirty)
        applyAlarm(context, manager, DELIVERY, delivery?.let { AlarmTarget(it, 0, exact) }, dirty)
        applyAlarm(
            context,
            manager,
            PHASE,
            focus?.let { AlarmTarget(System.currentTimeMillis() + it.remainingMillis().coerceAtLeast(100), it.startedAt, exact) },
            dirty,
        )
        invalidated = false
    }

    private fun applyAlarm(context: Context, manager: AlarmManager, kind: String, desired: AlarmTarget?, dirty: Boolean) {
        val (step, next) = alarmStep(alarms[kind] ?: ArmedAlarm(), desired, dirty)
        alarms[kind] = next
        when (step) {
            AlarmStep.Keep -> Unit
            AlarmStep.Cancel -> manager.cancel(pending(context, kind))
            AlarmStep.Schedule -> schedule(context, kind, desired!!.atMillis, desired.id)
        }
    }

    private fun pending(context: Context, kind: String, at: Long = 0, id: Long = 0): PendingIntent {
        val intent = Intent(context, PlanReceiver::class.java).setAction(kind)
            .setData(Uri.parse("ronumi://alarm/$kind")).putExtra("at", at).putExtra("id", id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun schedule(context: Context, kind: String, at: Long, id: Long = 0) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val event = pending(context, kind, at, id)
        if (exactAllowed(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, event)
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, event)
    }

    suspend fun fire(context: Context, intent: Intent) {
        val dao = context.app.dao
        when (intent.action) {
            PHASE -> {
                val focus = dao.activeFocus()
                if (focus?.startedAt == intent.getLongExtra("id", -1)) Focus.advance(context)
                forget(PHASE)
            }
            PLAN -> {
                val plan = freeSchedules(dao.allSchedules(), context.app.plus.has(PlusFeature.UNLIMITED_SCHEDULES)).firstOrNull { it.id == intent.getLongExtra("id", -1) && it.enabled && it.startFocus }
                if (plan != null) {
                    // Only an exact alarm permits a background foreground-service start.
                    if (exactAllowed(context) && System.currentTimeMillis() - intent.getLongExtra("at", 0) < 60_000) {
                        runCatching { Focus.start(context, plan.name, plan = plan) }
                            .onFailure { remind(context, plan.name, plan.id) }
                    } else remind(context, plan.name, plan.id)
                }
                forget(PLAN)
            }
            DELIVERY -> {
                Delivery.release(context)
                forget(DELIVERY)
            }
            SNOOZE -> {
                val id = intent.getLongExtra("id", 0)
                context.getSharedPreferences("plans", Context.MODE_PRIVATE).edit().putLong("snooze-$id", System.currentTimeMillis() + 10 * 60_000).apply()
                context.getSystemService(NotificationManager::class.java).cancel(20)
            }
        }
        refresh(context)
    }

    private suspend fun remind(context: Context, title: String, id: Long) {
        if (!context.app.dao.currentSettings().notifyPlanReminders) return
        val snooze = PendingIntent.getBroadcast(context, id.toInt(), Intent(context, PlanReceiver::class.java)
            .setAction(SNOOZE).setData(Uri.parse("ronumi://snooze/$id")).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, RonumiApp.CHANNEL_EVENTS)
            .addAction(0, context.app.getString(R.string.plan_notification_snooze), snooze)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(title.displayName(context)).setContentText(context.app.getString(R.string.plan_notification_body))
            .setContentIntent(MainActivity.pendingPlan(context, id)).setAutoCancel(true).build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(20, notification) }
    }

    private const val SNOOZE = "snooze"
}

/** System broadcasts rebuild alarms after reboot, app update, permission grants, and clock changes. */
class PlanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.app.scope.launch {
            try {
                if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                        Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) {
                    Focus.checkpoint(context)
                    Plans.invalidate()
                    Plans.refresh(context)
                } else Plans.fire(context, intent)
            } finally { pending.finish() }
        }
    }
}
