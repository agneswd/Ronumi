package dev.agneswd.ronumi.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ways ads could reach someone who must not see them, or show too often. */
class AdRulesTest {
    private val day = 24 * 60 * 60_000L
    private val now = 100 * day
    private val ready = AdMoment(now = now, today = "2026-10-07", installedAt = now - 4 * day, locked = true, focusActive = false, onboarded = true)

    @Test fun plusSeesNoAds() {
        val plus = ready.copy(locked = false)
        assertFalse(AdRules.mayShowInterstitial(plus, AdHistory()))
        assertEquals(0, AdRules.rewardsLeft(plus, AdHistory()))
    }

    @Test fun noAdsDuringFocus() {
        assertFalse(AdRules.mayShowInterstitial(ready.copy(focusActive = true), AdHistory()))
        assertEquals(0, AdRules.rewardsLeft(ready.copy(focusActive = true), AdHistory()))
    }

    @Test fun noAdsBeforeSetupEnds() {
        assertFalse(AdRules.mayShowInterstitial(ready.copy(onboarded = false), AdHistory()))
        assertEquals(0, AdRules.rewardsLeft(ready.copy(onboarded = false), AdHistory()))
    }

    @Test fun noAdsInTheFirstThreeDays() {
        val young = ready.copy(installedAt = now - 3 * day + 1)
        assertFalse(AdRules.mayShowInterstitial(young, AdHistory()))
        assertEquals(0, AdRules.rewardsLeft(young, AdHistory()))
        assertTrue(AdRules.mayShowInterstitial(ready.copy(installedAt = now - 3 * day), AdHistory()))
    }

    @Test fun atMostThreeInterstitialsADay() {
        var history = AdHistory()
        repeat(3) {
            val moment = ready.copy(now = now + it * AdRules.INTERSTITIAL_GAP_MILLIS)
            assertTrue(AdRules.mayShowInterstitial(moment, history))
            history = AdRules.afterInterstitial(moment, history)
        }
        assertFalse(AdRules.mayShowInterstitial(ready.copy(now = now + 3 * AdRules.INTERSTITIAL_GAP_MILLIS), history))
    }

    @Test fun interstitialsKeepAGap() {
        val history = AdRules.afterInterstitial(ready, AdHistory())
        assertFalse(AdRules.mayShowInterstitial(ready.copy(now = now + AdRules.INTERSTITIAL_GAP_MILLIS - 1), history))
        assertTrue(AdRules.mayShowInterstitial(ready.copy(now = now + AdRules.INTERSTITIAL_GAP_MILLIS), history))
    }

    @Test fun aClockSetBackDoesNotAllowAnotherAd() {
        val history = AdRules.afterInterstitial(ready, AdHistory())
        assertFalse(AdRules.mayShowInterstitial(ready.copy(now = now - day), history))
    }

    @Test fun atMostThreeRewardsADay() {
        var history = AdHistory()
        for (left in 3 downTo 1) {
            assertEquals(left, AdRules.rewardsLeft(ready, history))
            history = AdRules.afterReward(ready, history)
        }
        assertEquals(0, AdRules.rewardsLeft(ready, history))
    }

    @Test fun aNewDayResetsTheCounts() {
        var history = AdHistory()
        repeat(3) { history = AdRules.afterReward(ready, AdRules.afterInterstitial(ready.copy(now = now + it * day / 10), history)) }
        val tomorrow = ready.copy(now = now + day, today = "2026-10-08")
        assertEquals(3, AdRules.rewardsLeft(tomorrow, history))
        assertTrue(AdRules.mayShowInterstitial(tomorrow, history))
    }
}
