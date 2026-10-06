package dev.agneswd.ronumi.usage

import android.content.Context
import android.os.SystemClock
import dev.agneswd.ronumi.StillpointApp
import dev.agneswd.ronumi.data.UsageDay
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One usage scan shared by the screens that show recent days. Call [refresh] off the main thread. */
class UsageRefresher(private val app: StillpointApp) {
    private val core = UsageRefreshCore()
    private val gate = Mutex()
    private val cache = HashMap<LocalDate, DayUsage>()

    /** A clock, zone, or date broadcast. The next refresh treats stored days as stale once. */
    fun bumpClock() = core.bumpClock()

    /**
     * Returns [days] of usage, oldest first.
     * A day that is already being scanned is not scanned again.
     * Stored past days that were fully scanned stay stored until the clock changes.
     */
    suspend fun refresh(days: Int): List<DayUsage> = withContext(Dispatchers.IO) {
        gate.withLock { refreshLocked(days) }
    }

    private suspend fun refreshLocked(days: Int): List<DayUsage> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val clock = UsageClock(
            today = today,
            zone = zone,
            startOfTodayMillis = start,
            nowMillis = System.currentTimeMillis(),
            monotonicMillis = SystemClock.elapsedRealtime(),
            generation = core.generationNow(),
        )
        if (!app.usage.hasAccess()) {
            return (days - 1 downTo 0).map { DayUsage(today.minusDays(it.toLong()), emptyList(), 0) }
        }
        val stored = completeDays().filter { it < today }.toSet()
        val scan = core.plan(stored, days, clock)
        for (date in scan) {
            val usage = try {
                app.usage.day(date)
            } catch (error: Exception) {
                null
            }
            if (usage == null) {
                core.fail(date)
                continue
            }
            try {
                app.dao.recordUsage(usage.toUsageDay())
                if (scannedFullDay(date, zone, clock.nowMillis)) markComplete(date)
            } catch (error: Exception) {
                core.fail(date)
                continue
            }
            cache[date] = usage
            core.succeed(date, clock)
        }
        return (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            cache[date] ?: app.dao.usageDay(date.toString())?.toDayUsage() ?: DayUsage(date, emptyList(), 0)
        }
    }

    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun completeDays(): Set<LocalDate> {
        val saved = prefs().getStringSet(COMPLETE, emptySet()) ?: emptySet()
        return saved.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
    }

    private fun markComplete(date: LocalDate) {
        val next = completeDays().map { it.toString() }.toMutableSet()
        next.add(date.toString())
        prefs().edit().putStringSet(COMPLETE, next).apply()
    }

    private companion object {
        const val PREFS = "usage-refresh"
        const val COMPLETE = "complete-days"
    }
}

private fun DayUsage.toUsageDay() = UsageDay(date.toString(), perApp.toMap(), unlocks)

private fun UsageDay.toDayUsage() = DayUsage(
    LocalDate.parse(day),
    perApp.entries.sortedByDescending { it.value }.map { it.key to it.value },
    unlocks,
)
