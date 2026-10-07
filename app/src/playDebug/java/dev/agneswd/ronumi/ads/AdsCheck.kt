package dev.agneswd.ronumi.ads

import android.content.Context
import dev.agneswd.ronumi.app

/** Debug checks: "ads-ready" makes the install look 4 days old and clears today's counts. Both report the state. */
suspend fun adsCheck(context: Context, reset: Boolean): String {
    if (reset) PlayAds.resetForCheck(context, System.currentTimeMillis() - 4 * 24 * 60 * 60_000L)
    val history = PlayAds.history(context)
    val bonus = context.app.dao.settingsOrNull()?.bonusXp ?: 0
    return "rewards=${history.rewards}\ninterstitials=${history.interstitials}\nbonusXp=$bonus\nrewardsLeft=${PlayAds.rewardsLeft(context)}"
}
