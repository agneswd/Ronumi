package dev.agneswd.ronumi.ads

/** What the app knows when it decides about an ad. [today] is the local date as ISO text. */
data class AdMoment(
    val now: Long,
    val today: String,
    val installedAt: Long,
    /** True when Plus is locked. Unlocked and pending Plus see no ads. */
    val locked: Boolean,
    val focusActive: Boolean,
    val onboarded: Boolean,
)

/** Ads shown on [day]. Older days count as zero. */
data class AdHistory(val day: String = "", val interstitials: Int = 0, val rewards: Int = 0, val lastInterstitialAt: Long = 0)

/** The caps the owner set for the Play version. See DESIGN-ADS in the launch notes. */
object AdRules {
    const val MAX_INTERSTITIALS = 3
    const val MAX_REWARDS = 3
    const val REWARD_XP = 10
    const val INTERSTITIAL_GAP_MILLIS = 20 * 60_000L
    const val NEW_USER_MILLIS = 3 * 24 * 60 * 60_000L

    private fun eligible(m: AdMoment) = m.locked && !m.focusActive && m.onboarded && m.now - m.installedAt >= NEW_USER_MILLIS

    private fun on(m: AdMoment, h: AdHistory) = if (h.day == m.today) h else AdHistory(m.today, lastInterstitialAt = h.lastInterstitialAt)

    // A clock set back gives a negative gap, so it never allows an extra ad.
    fun mayShowInterstitial(m: AdMoment, h: AdHistory): Boolean = eligible(m) &&
        on(m, h).let { it.interstitials < MAX_INTERSTITIALS && m.now - it.lastInterstitialAt >= INTERSTITIAL_GAP_MILLIS }

    fun rewardsLeft(m: AdMoment, h: AdHistory): Int = if (!eligible(m)) 0 else (MAX_REWARDS - on(m, h).rewards).coerceAtLeast(0)

    fun afterInterstitial(m: AdMoment, h: AdHistory): AdHistory = on(m, h).let { it.copy(interstitials = it.interstitials + 1, lastInterstitialAt = m.now) }

    fun afterReward(m: AdMoment, h: AdHistory): AdHistory = on(m, h).let { it.copy(rewards = it.rewards + 1) }
}
