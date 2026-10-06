package dev.agneswd.stillpoint.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class UsageRefreshTest {
    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 6, 15)

    private fun clock(
        day: LocalDate = today,
        monotonic: Long = 10_000,
        generation: Long = 0,
        zone: ZoneId = this.zone,
        start: Long = dayWindow(day, zone).first,
    ) = UsageClock(day, zone, start, start + 3_600_000, monotonic, generation)

    @Test
    fun matchingRangesDoNotScanTwice() {
        val core = UsageRefreshCore()
        val first = core.plan(emptySet(), 7, clock())
        val second = core.plan(emptySet(), 7, clock(monotonic = 10_100))
        assertEquals(7, first.size)
        assertTrue(second.isEmpty())
    }

    @Test
    fun widerRequestScansDaysTheNarrowOneLeft() {
        val core = UsageRefreshCore()
        val todayOnly = core.plan(emptySet(), 1, clock())
        val week = core.plan(emptySet(), 7, clock(monotonic = 10_100))
        assertEquals(listOf(today), todayOnly)
        assertEquals(6, week.size)
        assertFalse(today in week)
    }

    @Test
    fun storedPastDaysStayIdleAcrossNavigationAndRestart() {
        val stored = (1..6).map { today.minusDays(it.toLong()) }.toSet()
        val core = UsageRefreshCore()
        val first = core.plan(stored, 7, clock())
        core.succeed(today, clock())
        val again = core.plan(stored, 7, clock(monotonic = 20_000))
        assertEquals(listOf(today), first)
        assertTrue(again.isEmpty())

        val restarted = UsageRefreshCore()
        assertEquals(listOf(today), restarted.plan(stored, 7, clock()))
    }

    @Test
    fun todayScansImmediatelyThenWaitsOneMinute() {
        val core = UsageRefreshCore()
        val now = clock()
        assertEquals(listOf(today), core.plan(emptySet(), 1, now))
        core.succeed(today, now)
        assertTrue(core.plan(emptySet(), 1, clock(monotonic = now.monotonicMillis + 59_000)).isEmpty())
        assertEquals(listOf(today), core.plan(emptySet(), 1, clock(monotonic = now.monotonicMillis + 60_000)))
    }

    @Test
    fun midnightScansYesterdayAndTheNewToday() {
        val core = UsageRefreshCore()
        core.plan(emptySet(), 1, clock())
        core.succeed(today, clock())
        val next = today.plusDays(1)
        val scan = core.plan(setOf(today), 2, clock(day = next, monotonic = 90_000))
        assertEquals(listOf(today, next), scan)
    }

    @Test
    fun zoneChangeRescansOnceWhenTheDateStays() {
        val stored = setOf(today.minusDays(1))
        val core = UsageRefreshCore()
        core.plan(stored, 2, clock())
        core.succeed(today, clock())
        val london = ZoneId.of("Europe/London")
        val moved = core.plan(stored, 2, clock(zone = london, generation = 1, monotonic = 20_000))
        assertTrue(today.minusDays(1) in moved)
        core.succeed(today.minusDays(1), clock(zone = london, generation = 1))
        val settled = core.plan(stored + today.minusDays(1), 2, clock(zone = london, generation = 1, monotonic = 30_000))
        assertFalse(today.minusDays(1) in settled)
    }

    @Test
    fun manualClockChangeDoesNotResetTheTodayThrottle() {
        val core = UsageRefreshCore()
        val now = clock()
        core.plan(emptySet(), 2, now)
        core.succeed(today, now)
        core.succeed(today.minusDays(1), now)
        val jumped = core.plan(
            setOf(today.minusDays(1)),
            2,
            clock(monotonic = now.monotonicMillis + 1_000, generation = 1, start = now.startOfTodayMillis + 60_000),
        )
        assertEquals(listOf(today.minusDays(1)), jumped)
        assertFalse(today in jumped)
    }

    @Test
    fun dateRollbackDoesNotKeepTodayAsAFinishedDay() {
        val future = today.plusDays(1)
        val core = UsageRefreshCore()
        core.plan(emptySet(), 1, clock(day = future))
        core.succeed(future, clock(day = future))
        val rolled = core.plan(setOf(future), 1, clock(monotonic = 80_000, generation = 1))
        assertEquals(listOf(today), rolled)
    }

    @Test
    fun missingPermissionAndFailedScansDoNotCountAsSaved() {
        assertFalse(persistUsageScan(hasAccess = false, failed = false))
        assertFalse(persistUsageScan(hasAccess = true, failed = true))
        assertTrue(persistUsageScan(hasAccess = true, failed = false))
        val core = UsageRefreshCore()
        core.plan(emptySet(), 1, clock())
        core.fail(today)
        assertEquals(listOf(today), core.plan(emptySet(), 1, clock(monotonic = 10_500)))
    }

    @Test
    fun dstDaysUseLocalBoundaries() {
        val spring = LocalDate.of(2026, 3, 8)
        val fall = LocalDate.of(2026, 11, 1)
        val springWindow = dayWindow(spring, zone)
        val fallWindow = dayWindow(fall, zone)
        assertEquals(23 * 3_600_000L, springWindow.second - springWindow.first)
        assertEquals(25 * 3_600_000L, fallWindow.second - fallWindow.first)
        assertTrue(scannedFullDay(spring, zone, springWindow.second))
        assertFalse(scannedFullDay(spring, zone, springWindow.second - 1))
    }
}
