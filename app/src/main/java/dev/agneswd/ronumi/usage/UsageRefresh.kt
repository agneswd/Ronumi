package dev.agneswd.ronumi.usage

import java.time.LocalDate
import java.time.ZoneId

/** The clock values the day planner needs. Monotonic time is not the civil clock. */
data class UsageClock(
    val today: LocalDate,
    val zone: ZoneId,
    val startOfTodayMillis: Long,
    val nowMillis: Long,
    val monotonicMillis: Long,
    val generation: Long,
)

/** In-memory scan state. Stored past days live in the database, not here. */
data class UsageRefreshState(
    val running: Set<LocalDate> = emptySet(),
    val todayScannedAt: Long? = null,
    val todayDate: LocalDate? = null,
    val zone: ZoneId? = null,
    val today: LocalDate? = null,
    val startOfTodayMillis: Long? = null,
    val generation: Long = 0,
)

data class UsagePlan(
    val scan: List<LocalDate>,
    val state: UsageRefreshState,
)

/** Start and end of a local date. A DST day is 23 or 25 hours, not 24. */
fun dayWindow(date: LocalDate, zone: ZoneId): Pair<Long, Long> {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
}

/** True when a scan that ended at [nowMillis] covered the whole local day. */
fun scannedFullDay(date: LocalDate, zone: ZoneId, nowMillis: Long): Boolean =
    nowMillis >= dayWindow(date, zone).second

/**
 * Chooses which days to scan.
 * A past day that is already stored is skipped until the date, zone, or clock generation changes.
 * Today is never treated as a completed past day, and it is scanned at most once per minute.
 */
fun planUsageRefresh(
    state: UsageRefreshState,
    storedDays: Set<LocalDate>,
    dayCount: Int,
    clock: UsageClock,
    throttleMillis: Long = 60_000,
): UsagePlan {
    val first = state.zone == null
    val boundaryChanged = !first && (
        state.zone != clock.zone ||
            state.today != clock.today ||
            state.startOfTodayMillis != clock.startOfTodayMillis ||
            state.generation != clock.generation
        )
    val trusted = if (boundaryChanged) emptySet() else storedDays.filter { it < clock.today }.toSet()
    val todayThrottled = state.todayDate == clock.today &&
        state.todayScannedAt != null &&
        clock.monotonicMillis - state.todayScannedAt < throttleMillis
    val scan = (dayCount - 1 downTo 0).map { clock.today.minusDays(it.toLong()) }.filter { date ->
        when {
            date in state.running -> false
            date > clock.today -> false
            date < clock.today && date in trusted -> false
            date == clock.today && todayThrottled -> false
            else -> true
        }
    }
    return UsagePlan(
        scan = scan,
        state = state.copy(
            running = state.running + scan,
            zone = clock.zone,
            today = clock.today,
            startOfTodayMillis = clock.startOfTodayMillis,
            generation = clock.generation,
        ),
    )
}

/** A failed scan or a missing permission must not replace stored history. */
fun persistUsageScan(hasAccess: Boolean, failed: Boolean): Boolean = hasAccess && !failed

/**
 * Remembers which days are in flight and when today was last scanned.
 * A new instance is a process start: it trusts stored past days.
 */
class UsageRefreshCore {
    private var state = UsageRefreshState()
    var generation: Long = 0
        private set

    @Synchronized
    fun bumpClock() {
        generation++
    }

    @Synchronized
    fun generationNow(): Long = generation

    @Synchronized
    fun plan(storedDays: Set<LocalDate>, dayCount: Int, clock: UsageClock): List<LocalDate> {
        val plan = planUsageRefresh(state, storedDays, dayCount, clock)
        state = plan.state
        return plan.scan
    }

    @Synchronized
    fun succeed(date: LocalDate, clock: UsageClock) {
        state = state.copy(
            running = state.running - date,
            todayScannedAt = if (date == clock.today) clock.monotonicMillis else state.todayScannedAt,
            todayDate = if (date == clock.today) clock.today else state.todayDate,
        )
    }

    /** Drops the in-flight mark and does not start the today throttle. */
    @Synchronized
    fun fail(date: LocalDate) {
        state = state.copy(running = state.running - date)
    }
}
