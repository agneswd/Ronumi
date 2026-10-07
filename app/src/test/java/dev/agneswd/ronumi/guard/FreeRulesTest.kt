package dev.agneswd.ronumi.guard

import dev.agneswd.ronumi.data.ActiveFocus
import dev.agneswd.ronumi.data.AppLimit
import dev.agneswd.ronumi.data.BlockMode
import dev.agneswd.ronumi.data.FocusPhase
import dev.agneswd.ronumi.data.FocusSound
import dev.agneswd.ronumi.data.LimitMode
import dev.agneswd.ronumi.data.Schedule
import dev.agneswd.ronumi.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the guard applies the free limits when Plus is locked. Written before the code.
 * Failure cases: a newer rule wins over an older one; a disabled rule uses up the free slot;
 * more than five apps stay blocked; an allow-list rule is cut; a Plus-only block keeps working;
 * a strict limit stays strict; a running strict session is weakened; data is changed with Plus.
 */
class FreeRulesTest {
    private val apps = (1..8).map { "app.$it" }.toSet()

    private fun schedule(id: Long, enabled: Boolean = true, packages: Set<String> = setOf("a"), mode: BlockMode = BlockMode.LISTED) =
        Schedule(id = id, name = "s$id", startMinute = 0, endMinute = 60, packages = packages, mode = mode, enabled = enabled)

    private fun focus(strict: Boolean, packages: Set<String> = apps) = ActiveFocus(
        startedAt = 0, phase = FocusPhase.FOCUS, phaseStartedAt = 0, phaseEndsAt = 1, round = 1, rounds = 1,
        focusMinutes = 25, breakMinutes = 5, packages = packages, mode = BlockMode.LISTED, strict = strict,
        lockHome = false, sound = FocusSound.OFF,
    )

    private val plusSettings = Settings(
        focusPackages = apps, focusStrict = true, blockYoutubeShorts = true, blockInstagramReels = true,
        blockSnapchatSpotlight = true, blockFacebookReels = true, blockAdultSites = true, protection = true,
        heldPackages = setOf("chat"), youtubeStudyMode = true, blockYoutubeHome = true,
    )

    @Test fun withPlusTheRulesStayTheSame() {
        val rules = Rules(plusSettings, schedules = listOf(schedule(2), schedule(1)), sites = setOf("x.com"))
        assertSame(rules, rules.forPlus(hasPlus = true))
    }

    @Test fun theOldestEnabledScheduleStaysActive() {
        val free = Rules(schedules = listOf(schedule(3), schedule(1, enabled = false), schedule(2))).forPlus(hasPlus = false)
        assertEquals(listOf(2L), free.schedules.filter { it.enabled }.map { it.id })
    }

    @Test fun theFirstSavedLimitStaysActiveAndStrictBecomesGentle() {
        val limits = linkedMapOf(
            "first" to AppLimit("first", 30, LimitMode.STRICT),
            "second" to AppLimit("second", 30),
        )
        val free = Rules(limits = limits).forPlus(hasPlus = false)
        assertEquals(setOf("first"), free.limits.keys)
        assertEquals(LimitMode.GENTLE, free.limits.getValue("first").mode)
    }

    @Test fun blockListsKeepTheFirstFiveAppsInSavedOrder() {
        val free = Rules(Settings(focusPackages = apps), schedules = listOf(schedule(1, packages = apps))).forPlus(hasPlus = false)
        val firstFive = apps.take(5).toSet()
        assertEquals(firstFive, free.settings.focusPackages)
        assertEquals(firstFive, free.schedules.single().packages)
    }

    @Test fun anAllowListScheduleIsNotCut() {
        val free = Rules(schedules = listOf(schedule(1, packages = apps, mode = BlockMode.ALL_EXCEPT))).forPlus(hasPlus = false)
        assertEquals(apps, free.schedules.single().packages)
    }

    @Test fun plusOnlyBlocksStop() {
        val free = Rules(plusSettings, sites = setOf("x.com")).forPlus(hasPlus = false)
        with(free.settings) {
            assertFalse(blockYoutubeShorts || blockInstagramReels || blockSnapchatSpotlight || blockFacebookReels)
            assertFalse(blockAdultSites || protection || youtubeStudyMode || blockYoutubeHome || focusStrict)
            assertTrue(heldPackages.isEmpty())
        }
        assertTrue(free.sites.isEmpty())
    }

    @Test fun aRunningStrictSessionRunsToItsEnd() {
        val free = Rules(plusSettings, focus = focus(strict = true)).forPlus(hasPlus = false)
        assertTrue(free.focus!!.strict)
        assertEquals(apps, free.focus!!.packages)
        // Protection guards that session until it ends.
        assertTrue(free.settings.protection)
    }

    @Test fun aRelaxedSessionKeepsFiveApps() {
        val free = Rules(focus = focus(strict = false)).forPlus(hasPlus = false)
        assertEquals(apps.take(5).toSet(), free.focus!!.packages)
    }
}
