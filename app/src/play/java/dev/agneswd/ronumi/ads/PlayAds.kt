package dev.agneswd.ronumi.ads

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.BONUS_XP_LIMIT
import dev.agneswd.ronumi.data.updateSettings
import dev.agneswd.ronumi.plus.Entitlement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Google ads for Play users without Plus. [AdRules] decides what may show. This object asks for consent, loads one ad
 * of each kind ahead of time, shows it, and counts it. Call it on the main thread.
 */
object PlayAds {
    // Google drops a loaded ad after about an hour.
    private const val EXPIRY_MILLIS = 55 * 60_000L

    private var consentAsked = false
    private var sdkStarted = false
    private var interstitial: Pair<InterstitialAd, Long>? = null
    private var rewarded: Pair<RewardedAd, Long>? = null
    private val loading = mutableSetOf<String>()

    private val ready = MutableStateFlow(false)
    /** True while a rewarded ad waits to be shown. */
    val rewardReady = ready.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences("ads", Context.MODE_PRIVATE)

    fun history(context: Context): AdHistory = prefs(context).run {
        AdHistory(getString("day", "").orEmpty(), getInt("interstitials", 0), getInt("rewards", 0), getLong("lastInterstitialAt", 0))
    }

    private fun save(context: Context, history: AdHistory) = prefs(context).edit {
        putString("day", history.day)
        putInt("interstitials", history.interstitials)
        putInt("rewards", history.rewards)
        putLong("lastInterstitialAt", history.lastInterstitialAt)
    }

    /** The install time. Debug checks can move it back to skip the new-user wait. */
    private fun installedAt(context: Context): Long = prefs(context).getLong("installedAt", 0).takeIf { it > 0 }
        ?: context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime

    suspend fun moment(context: Context): AdMoment {
        val app = context.app
        return withContext(Dispatchers.IO) {
            AdMoment(
                now = System.currentTimeMillis(),
                today = LocalDate.now().toString(),
                installedAt = installedAt(app),
                locked = app.plus.state.value.entitlement == Entitlement.LOCKED,
                focusActive = app.dao.activeFocus() != null,
                onboarded = app.dao.settingsOrNull()?.onboarded == true,
            )
        }
    }

    suspend fun rewardsLeft(context: Context): Int = AdRules.rewardsLeft(moment(context), history(context))

    /**
     * Asks for consent where the law needs it, then starts the SDK. Nothing loads without permission to request ads.
     * Without it, the load stays marked as running, so this process does not ask again.
     */
    private fun start(activity: Activity, then: () -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val go = {
            if (info.canRequestAds()) {
                if (!sdkStarted) MobileAds.initialize(activity.applicationContext)
                sdkStarted = true
                then()
            }
        }
        if (consentAsked) return go()
        consentAsked = true
        info.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { go() } },
            { go() },
        )
    }

    private fun fresh(loadedAt: Long) = System.currentTimeMillis() - loadedAt < EXPIRY_MILLIS

    /** Loads the session ad while the summary shows, so leaving it can show the ad at once. */
    fun prepareSessionAd(activity: Activity) = activity.app.scope.launch(Dispatchers.Main) {
        if (!AdRules.mayShowInterstitial(moment(activity), history(activity))) return@launch
        if (interstitial?.second?.let(::fresh) == true || !loading.add("interstitial")) return@launch
        start(activity) {
            InterstitialAd.load(activity, activity.getString(R.string.ads_unit_interstitial), AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading -= "interstitial"
                    interstitial = ad to System.currentTimeMillis()
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading -= "interstitial"
                }
            })
        }
    }

    /** Shows the loaded session ad if the caps allow it. Otherwise nothing happens. */
    fun afterSessionSummary(activity: Activity) {
        val (ad, loadedAt) = interstitial ?: return
        activity.app.scope.launch(Dispatchers.Main) {
            val moment = moment(activity)
            if (!fresh(loadedAt)) interstitial = null
            if (interstitial == null || !AdRules.mayShowInterstitial(moment, history(activity))) return@launch
            interstitial = null
            save(activity, AdRules.afterInterstitial(moment, history(activity)))
            ad.show(activity)
        }
    }

    /** Loads a rewarded ad when a reward is still open today. [rewardReady] turns true when it can show. */
    fun prepareReward(activity: Activity) = activity.app.scope.launch(Dispatchers.Main) {
        if (rewarded?.second?.let(::fresh) == true) {
            ready.value = true
            return@launch
        }
        rewarded = null
        ready.value = false
        if (AdRules.rewardsLeft(moment(activity), history(activity)) == 0 || !loading.add("rewarded")) return@launch
        start(activity) {
            RewardedAd.load(activity, activity.getString(R.string.ads_unit_rewarded), AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading -= "rewarded"
                    rewarded = ad to System.currentTimeMillis()
                    ready.value = true
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading -= "rewarded"
                }
            })
        }
    }

    /** Shows the rewarded ad. XP is added only when Google reports the reward. [onClosed] gets the rewards left. */
    fun showReward(activity: Activity, onClosed: (Int) -> Unit) {
        val (ad, _) = rewarded ?: return
        rewarded = null
        ready.value = false
        val app = activity.app
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = afterReward()
            override fun onAdFailedToShowFullScreenContent(error: AdError) = afterReward()
            fun afterReward() {
                app.scope.launch(Dispatchers.Main) {
                    val left = rewardsLeft(activity)
                    onClosed(left)
                    if (left > 0) prepareReward(activity)
                }
            }
        }
        ad.show(activity) {
            app.scope.launch {
                save(app, AdRules.afterReward(moment(app), history(app)))
                app.dao.updateSettings { it.copy(bonusXp = (it.bonusXp + AdRules.REWARD_XP).coerceAtMost(BONUS_XP_LIMIT)) }
            }
        }
    }

    /** True when the law needs a way to change the ad consent, such as in the EU. */
    fun privacyOptionsRequired(context: Context): Boolean =
        UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) = UserMessagingPlatform.showPrivacyOptionsForm(activity) { }

    /** Debug checks only: pretend the install is older, and forget today's counts. */
    internal fun resetForCheck(context: Context, installedAt: Long) {
        prefs(context).edit { clear(); putLong("installedAt", installedAt) }
        interstitial = null
        rewarded = null
        ready.value = false
    }
}
