package dev.agneswd.stillpoint.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

/** Screen time for one day. [perApp] is sorted with the most used app first. */
data class DayUsage(
    val date: LocalDate,
    val perApp: List<Pair<String, Long>>,
    val unlocks: Int,
) {
    val totalMillis: Long get() = perApp.sumOf { it.second }
}

/**
 * Reads foreground time and unlocks from the system usage events.
 * Events give exact times. The aggregated UsageStats buckets do not split well at midnight.
 */
class UsageReader(private val context: Context, private val catalog: AppCatalog) {
    private val manager = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground time of one app since local midnight. */
    fun todayMillis(packageName: String): Long {
        val from = startOfDay(LocalDate.now())
        return foregroundTimes(from, System.currentTimeMillis())[packageName] ?: 0L
    }

    fun day(date: LocalDate): DayUsage {
        val from = startOfDay(date)
        val to = minOf(startOfDay(date.plusDays(1)), System.currentTimeMillis())
        if (from >= to) return DayUsage(date, emptyList(), 0)
        val hidden = catalog.launchers() + SYSTEM_UI
        val perApp = foregroundTimes(from, to)
            .filterKeys { it !in hidden }
            .filterValues { it >= 1_000 }
            .toList()
            .sortedByDescending { it.second }
        return DayUsage(date, perApp, unlocks(from, to))
    }

    /** The last [count] days, oldest first, today last. */
    fun recentDays(count: Int): List<DayUsage> {
        val today = LocalDate.now()
        return (count - 1 downTo 0).map { day(today.minusDays(it.toLong())) }
    }

    private fun foregroundTimes(from: Long, to: Long): Map<String, Long> {
        if (!hasAccess()) return emptyMap()
        val timeline = ForegroundTimeline(from, to)
        // Find the app already open at midnight, even before its first event today.
        val events = manager.queryEvents((from - 86_400_000).coerceAtLeast(0), to)
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> event.packageName?.let { timeline.resume(it, event.timeStamp) }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    event.packageName?.let { timeline.pause(it, event.timeStamp) }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> timeline.screenOff(event.timeStamp)
            }
        }
        return timeline.result()
    }

    private fun unlocks(from: Long, to: Long): Int {
        if (!hasAccess()) return 0
        var count = 0
        val events = manager.queryEvents(from, to)
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) count++
        }
        return count
    }

    companion object {
        const val SYSTEM_UI = "com.android.systemui"

        fun startOfDay(date: LocalDate): Long =
            date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
