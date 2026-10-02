package dev.agneswd.stillpoint.guard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.ui.MainActivity
import dev.agneswd.stillpoint.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import android.os.SystemClock
import android.os.PowerManager
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.StillpointApp
import java.time.LocalDate

/**
 * Enforces every block. Window changes tell it which app is in front.
 * A 20 second tick catches limits that run out and schedules that start while an app is open.
 * Window content tells it about Shorts feeds, browser addresses and the settings pages of this app.
 */
class GuardService : AccessibilityService() {
    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var rules = Rules()
    private var foreground: String? = null
    private var essentials = emptySet<String>()
    private var launchers = emptySet<String>()
    private val activityCache = HashMap<ComponentName, Boolean>()
    private var lastContentCheck = 0L
    private var lastBlockAt = 0L
    private var visitStarted = SystemClock.elapsedRealtime()
    private var lastReminder = 0L
    private val firstVideos = HashMap<String, String>()

    private val tick = object : Runnable {
        override fun run() {
            if (getSystemService(PowerManager::class.java).isInteractive) {
                appInFront()?.let(::setForeground)
                evaluate()
                foreground?.let { checkContent(it, force = true) }
            }
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) { foreground = null; firstVideos.clear() }
            Widgets.refresh(context)
        }
    }

    override fun onServiceConnected() {
        refreshSystemApps()
        val dao = app.dao
        scope.launch {
            combine(dao.settings(), dao.limits(), dao.schedules(), dao.sites(), dao.activeFocusFlow()) { settings, limits, schedules, sites, focus ->
                Rules(settings, limits.associateBy { it.packageName }, schedules, sites.map { it.domain }.toSet(), focus)
            }.collect {
                rules = it
                evaluate()
            }
        }
        handler.postDelayed(tick, TICK_MILLIS)
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val front = appInFront() ?: pkg.takeIf { isActivity(it, event.className?.toString()) }
                if (front != null) {
                    if (front != foreground) refreshSystemApps()
                    setForeground(front)
                    evaluate()
                }
                checkContent(pkg, force = true)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_VIEW_SCROLLED -> checkContent(pkg, force = false)
        }
    }

    /**
     * The package of the focused app window. Keyboards, the status bar and overlays are other
     * window types, so they never count. Some apps report a view class in their events, so the
     * window list is more reliable than the event.
     */
    private fun appInFront(): String? {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val window = apps.firstOrNull { it.isFocused } ?: apps.firstOrNull() ?: return null
        return window.root?.packageName?.toString()
    }

    /** Dialogs, keyboards and toasts also change windows. Only real activities change the app in front. */
    private fun isActivity(pkg: String, className: String?): Boolean {
        className ?: return false
        val component = ComponentName(pkg, className)
        return activityCache.getOrPut(component) {
            runCatching { packageManager.getActivityInfo(component, 0) }.isSuccess
        }
    }

    private fun setForeground(pkg: String) {
        if (pkg == packageName) { foreground = pkg; return }
        if (foreground != pkg) {
            if (foreground != packageName) firstVideos.clear()
            visitStarted = SystemClock.elapsedRealtime()
            lastReminder = 0
        }
        foreground = pkg
    }

    private fun refreshSystemApps() {
        val catalog = app.catalog
        essentials = catalog.essentials()
        launchers = catalog.launchers()
    }

    private fun evaluate() {
        val pkg = foreground ?: return
        if (pkg == packageName) return
        val current = rules
        scope.launch {
            val verdict = withContext(Dispatchers.Default) {
                current.decide(
                    pkg = pkg,
                    label = app.catalog.label(pkg),
                    now = LocalDateTime.now(),
                    essentials = essentials,
                    launchers = launchers,
                    allowedUntil = app.dao.pass(LocalDate.now().toString(), pkg)?.expiresAt ?: 0,
                    usedToday = { app.usage.todayMillis(pkg) },
                )
            }
            if (foreground != pkg || rules !== current) return@launch
            when (verdict) {
                Verdict.Allow -> remind(pkg, current)
                Verdict.ReturnToFocus -> startActivity(MainActivity.focusIntent(this@GuardService))
                is Verdict.Block -> block(pkg, verdict.reason)
            }
        }
    }

    /** Reminders count one continuous visit and stop when the app leaves the foreground. */
    private fun remind(pkg: String, current: Rules) {
        val minutes = current.limits[pkg]?.takeIf { it.enabled }?.reminderMinutes ?: 0
        if (minutes <= 0) return
        val now = SystemClock.elapsedRealtime()
        val interval = minutes * 60_000L
        if (now - visitStarted < interval || lastReminder > 0 && now - lastReminder < interval) return
        lastReminder = now
        val notification = NotificationCompat.Builder(this, StillpointApp.CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("Time to take a break?")
            .setContentText("You have used ${app.catalog.label(pkg)} for ${(now - visitStarted) / 60_000} minutes.")
            .setContentIntent(MainActivity.pendingHome(this)).setAutoCancel(true).build()
        runCatching { getSystemService(NotificationManager::class.java).notify(30, notification) }
    }

    private fun checkContent(pkg: String, force: Boolean) {
        if (pkg !in watchedPackages && !rules.settings.blockMultiWindow) return
        val now = System.currentTimeMillis()
        if (!force && now - lastContentCheck < CONTENT_THROTTLE_MILLIS) return
        lastContentCheck = now
        val current = rules
        val roots = windowRoots(pkg)
        if (roots.isEmpty()) return

        val protected = current.settings.protection && current.locked(LocalDateTime.now())
        if (!protected && current.settings.pauseBlocksUntil > now) return
        if (current.settings.blockMultiWindow && current.locked(LocalDateTime.now()) && pkg !in essentials &&
            (windows.any { it.isInPictureInPictureMode } || windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root?.packageName?.toString() }.filter { it !in essentials }.distinct().size > 1)) {
            block(pkg, BlockReason(BlockKind.MULTI_WINDOW, "Multiple app windows are blocked", "Use one app during this block."))
            return
        }
        val contentEnabled = !current.settings.contentOnlyDuringFocus || current.focusing
        if (contentEnabled) shortsFeeds.firstOrNull { it.packageName == pkg && it.enabled(current.settings) }?.let { feed ->
            val root = roots.firstOrNull(feed::isShowing)
            if (root != null) {
                val identity = feed.videoIdentity(root)
                val first = firstVideos[pkg]
                if (current.settings.allowFirstShort && identity != null && (first == null || first == identity)) {
                    firstVideos[pkg] = identity
                    return
                }
                performGlobalAction(GLOBAL_ACTION_BACK)
                block(pkg, BlockReason(BlockKind.SHORTS, "${feed.name} is blocked", "The rest of the app still works."))
                return
            }
        }
        if (contentEnabled && pkg == "com.google.android.youtube") {
            if (current.settings.blockYoutubeHome && roots.any { it.youtubeHome() }) {
                roots.any { it.openYoutubeSearch() }
                block(pkg, BlockReason(BlockKind.STUDY, "YouTube home is blocked", "Search for a video or use your subscriptions."))
                return
            }
            if (current.settings.youtubeStudyMode && roots.any { it.youtubePlayer() }) {
                val channel = roots.firstNotNullOfOrNull { it.youtubeChannel() }
                if (channel == null || current.settings.allowedYoutubeChannels.none { channelKey(it) == channelKey(channel) }) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    block(pkg, BlockReason(BlockKind.STUDY, "This YouTube channel is blocked", "Only your chosen channels work in study mode."))
                    return
                }
            }
        }

        roots.firstNotNullOfOrNull { it.browserHost(pkg) }?.let { host ->
            if (!contentEnabled) return@let
            val site = if (current.settings.siteAllowList && !allowedSite(host, current.sites)) host
                else blockedSiteFor(host, if (current.settings.siteAllowList) emptySet() else current.sites, current.settings.blockAdultSites)
                    ?: return@let
            performGlobalAction(GLOBAL_ACTION_BACK)
            block(pkg, BlockReason(BlockKind.SITE, "$site is blocked", "You put this site on your block list."))
            return
        }

        if (current.settings.protection && pkg in protectedScreens && current.locked(LocalDateTime.now()) &&
            roots.any { it.containsText(app.catalog.label(packageName)) }
        ) {
            block(pkg, BlockReason(BlockKind.PROTECTION, "Stillpoint settings are locked", "You can change them when the focus round or schedule ends."))
        }
    }

    /**
     * Every window of [pkg] on screen. A dialog can be the active window,
     * and the address bar or video feed is then in the window under it.
     */
    private fun windowRoots(pkg: String): List<AccessibilityNodeInfo> {
        val roots = windows.mapNotNull { it.root }.filter { it.packageName?.toString() == pkg }
        return roots.ifEmpty { listOfNotNull(rootInActiveWindow?.takeIf { it.packageName?.toString() == pkg }) }
    }

    /**
     * Covers the app with the block screen. It does not press home first: the launcher
     * could then come up over the block screen. The block screen goes home when it closes.
     */
    private fun block(pkg: String, reason: BlockReason) {
        val now = System.currentTimeMillis()
        if (now - lastBlockAt < BLOCK_DEBOUNCE_MILLIS) return
        lastBlockAt = now
        startActivity(BlockActivity.intent(this, pkg, reason))
    }

    companion object {
        private const val TICK_MILLIS = 20_000L
        private const val CONTENT_THROTTLE_MILLIS = 400L
        private const val BLOCK_DEBOUNCE_MILLIS = 1_500L

        /** Screens where someone can turn off or remove this app. */
        private val protectedScreens = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )

        private val watchedPackages = shortsFeeds.map { it.packageName }.toSet() + browserUrlBars.keys + protectedScreens

        fun isEnabled(context: Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val name = ComponentName(context, GuardService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == name }
        }
    }
}
