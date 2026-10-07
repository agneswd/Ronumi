package dev.agneswd.ronumi

import dev.agneswd.ronumi.ui.design.Sfx

import android.app.Application
import android.app.LocaleManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import android.os.Build
import dev.agneswd.ronumi.data.FocusPhase
import dev.agneswd.ronumi.data.RonumiDatabase
import dev.agneswd.ronumi.usage.AppCatalog
import dev.agneswd.ronumi.usage.UsageReader
import dev.agneswd.ronumi.usage.UsageRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.schedule.Plans
import dev.agneswd.ronumi.widget.Widgets

/** Holds the process-wide objects. Get it with [Context.app]. */
class RonumiApp : Application() {
    val database by lazy { RonumiDatabase.open(this) }
    val dao get() = database.dao()
    val usage by lazy { UsageReader(this, catalog) }
    val usageRefresh by lazy { UsageRefresher(this) }
    val catalog by lazy { AppCatalog(this) }
    val plus: dev.agneswd.ronumi.plus.Plus by lazy { Distribution.createPlus(this) }

    /** For work that must finish even when the screen that started it closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Sfx.init(this)
        watchClock()
        readAppLanguage()
        language = resources.configuration.locales
        createChannels()
        watchLanguage()
        plus
        scope.launch {
            // A checkpoint rewrites elapsed time only. Plans care about the session, phase, deadline, and pause.
            val focusKey = dao.activeFocusFlow().map { focus ->
                // Whole seconds. A checkpoint rewrites the deadline by a few milliseconds.
                focus?.let { FocusScheduleKey(it.startedAt, it.phase, it.phaseEndsAt / 1000, paused = !it.running) }
            }.distinctUntilChanged()
            // Plus decides which schedules run, so a purchase or a refund also refreshes the alarms.
            val plusKey = plus.state.map { it.entitlement }.distinctUntilChanged()
            combine(dao.settings().map { it.notificationDeliveryTimes }.distinctUntilChanged(), dao.schedules(), focusKey, plusKey) { _, _, _, _ -> Unit }.collect {
                Plans.refresh(this@RonumiApp)
            }
        }
    }

    // The language the app text uses now. The app language in Android settings can change it while the process runs.
    private var language: LocaleList = LocaleList.getEmptyLocaleList()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        languageChanged()
    }

    /** Puts a new phone or app language into the text that Android or a launcher keeps outside the app. */
    private fun languageChanged() {
        readAppLanguage()
        if (resources.configuration.locales == language) return
        language = resources.configuration.locales
        createChannels()
        catalog.forgetLabels()
        Widgets.invalidateContent()
        Widgets.refresh(this)
    }

    // Android tells the app about a new app language with this broadcast, also when no screen is open.
    private fun watchLanguage() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = languageChanged()
        }
        val filter = IntentFilter(Intent.ACTION_LOCALE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    /**
     * Android 13 and later let the user pick a language for this app only. Activities always get it. A process that
     * started before the choice keeps the phone language for everything else, and Android resets shared resources
     * when a screen opens. So the app reads the choice itself and serves its text from resources in that language.
     * Services, receivers, widgets, and notifications get their text through the app for this reason.
     */
    private fun readAppLanguage() {
        appLocales = if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(LocaleManager::class.java)?.applicationLocales?.takeUnless { it.isEmpty }
        } else null
    }

    @Volatile private var appLocales: LocaleList? = null
    // The phone configuration with the app language, and resources made from it.
    @Volatile private var localized: Pair<Configuration, Resources>? = null

    override fun getResources(): Resources {
        val base = super.getResources()
        val wanted = appLocales ?: return base
        if (base.configuration.locales == wanted) return base
        val config = Configuration(base.configuration).apply { setLocales(wanted) }
        localized?.let { (made, resources) -> if (made == config) return resources }
        return createConfigurationContext(config).resources.also { localized = config to it }
    }

    /** Creates the channels, or renames them in the current language. */
    private fun createChannels() {
        getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_FOCUS, getString(R.string.notification_channel_focus), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_EVENTS, getString(R.string.notification_channel_events), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    private fun watchClock() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                usageRefresh.bumpClock()
            }
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    /** Identity of the running session for alarm scheduling. Elapsed time is not part of it. */
    private data class FocusScheduleKey(
        val startedAt: Long,
        val phase: FocusPhase,
        val phaseEndsAtSeconds: Long,
        val paused: Boolean,
    )

    companion object {
        const val CHANNEL_FOCUS = "focus"
        const val CHANNEL_EVENTS = "events"
    }
}

val Context.app: RonumiApp get() = applicationContext as RonumiApp
