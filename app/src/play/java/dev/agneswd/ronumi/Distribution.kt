package dev.agneswd.ronumi

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.agneswd.ronumi.ads.AdRules
import dev.agneswd.ronumi.ads.PlayAds
import dev.agneswd.ronumi.data.Settings
import dev.agneswd.ronumi.plus.Plus
import dev.agneswd.ronumi.plus.PlayPlus
import dev.agneswd.ronumi.plus.createStore
import dev.agneswd.ronumi.ui.Chevron
import dev.agneswd.ronumi.ui.ListRow
import dev.agneswd.ronumi.ui.design.Tag
import dev.agneswd.ronumi.ui.findActivity

object Distribution {
    /** Play builds do not block Android Settings, the installer, or uninstall. */
    const val guardsSystemScreens = false

    /** Play builds link the Google Play Billing Library. The legal notices list its terms. */
    const val usesBilling = true

    /** Play builds show Google ads to users without Plus. The legal notices list the SDK terms. */
    const val usesAds = true

    fun createPlus(app: RonumiApp): Plus = PlayPlus(app, createStore(app))

    @Composable
    fun UpdateSettings(settings: Settings) = Unit

    /** The Play build has no import screen. */
    @Composable
    fun ImportOffer(): Boolean? = false

    /** "Watch a short ad for XP". It shows only while a reward is left today and an ad is loaded. */
    @Composable
    fun RewardedXpOffer() {
        val context = LocalContext.current
        val activity = context.findActivity() ?: return
        val plus by context.app.plus.state.collectAsState()
        val ready by PlayAds.rewardReady.collectAsState()
        var left by remember { mutableIntStateOf(0) }
        LaunchedEffect(plus.entitlement) {
            left = PlayAds.rewardsLeft(context)
            if (left > 0) PlayAds.prepareReward(activity)
        }
        if (left == 0 || !ready) return
        ListRow(
            stringResource(R.string.ads_reward_title),
            pluralStringResource(R.plurals.ads_reward_line, left, left),
            onClick = { PlayAds.showReward(activity) { left = it } },
            trailing = { Tag(stringResource(R.string.home_quest_xp, AdRules.REWARD_XP)) },
        )
    }

    /** Loads the ad that may show when the user leaves the session summary. */
    @Composable
    fun PrepareSessionAd() {
        val activity = LocalContext.current.findActivity() ?: return
        LaunchedEffect(Unit) { PlayAds.prepareSessionAd(activity) }
    }

    fun afterSessionSummary(activity: Activity) = PlayAds.afterSessionSummary(activity)

    /** Where the law asks for it, a way to change the ad consent. */
    @Composable
    fun AdPrivacyRow() {
        val activity = LocalContext.current.findActivity() ?: return
        if (!PlayAds.privacyOptionsRequired(activity)) return
        ListRow(
            stringResource(R.string.ads_privacy_title),
            stringResource(R.string.ads_privacy_line),
            onClick = { PlayAds.showPrivacyOptions(activity) },
        ) { Chevron() }
    }
}
