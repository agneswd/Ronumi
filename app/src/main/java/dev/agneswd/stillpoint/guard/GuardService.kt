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
import java.util.concurrent.ConcurrentHashMap

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

    private val tick = object : Runnable {
        override fun run() {
            appInFront()?.let { foreground = it }
            evaluate()
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = Widgets.refresh(context)
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
                    foreground = front
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
                    allowedUntil = Allowances.until(pkg),
                    usedToday = { app.usage.todayMillis(pkg) },
                )
            }
            if (foreground != pkg) return@launch
            when (verdict) {
                Verdict.Allow -> Unit
                Verdict.ReturnToFocus -> startActivity(MainActivity.focusIntent(this@GuardService))
                is Verdict.Block -> block(pkg, verdict.reason)
            }
        }
    }

    private fun checkContent(pkg: String, force: Boolean) {
        if (pkg !in watchedPackages) return
        val now = System.currentTimeMillis()
        if (!force && now - lastContentCheck < CONTENT_THROTTLE_MILLIS) return
        lastContentCheck = now
        val current = rules
        val roots = windowRoots(pkg)
        if (roots.isEmpty()) return

        shortsFeeds.firstOrNull { it.packageName == pkg && it.enabled(current.settings) }?.let { feed ->
            if (roots.any(feed::isShowing)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                block(pkg, BlockReason(BlockKind.SHORTS, "${feed.name} is blocked", "The rest of the app still works."))
            }
            return
        }

        roots.firstNotNullOfOrNull { it.browserHost(pkg) }?.let { host ->
            val site = blockedSiteFor(host, current.sites, current.settings.blockAdultSites) ?: return@let
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

/** Temporary passes from gentle limits. They live in memory and end when the process dies. */
object Allowances {
    private val passes = ConcurrentHashMap<String, Long>()

    fun until(pkg: String): Long = passes[pkg] ?: 0L

    fun grant(pkg: String, millis: Long) {
        passes[pkg] = System.currentTimeMillis() + millis
    }
}
