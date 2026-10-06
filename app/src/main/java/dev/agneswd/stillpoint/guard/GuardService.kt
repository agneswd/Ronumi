package dev.agneswd.stillpoint.guard

import dev.agneswd.stillpoint.ui.textResource
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import androidx.core.content.ContextCompat
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
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
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
    private var browsers = emptySet<String>()
    private var systemAppsUpdatedAt = 0L
    private var evaluation: kotlinx.coroutines.Job? = null
    private val activityCache = HashMap<ComponentName, Boolean>()
    private var lastContentCheck = 0L
    private var lastBlockAt = 0L
    private var visitStarted = SystemClock.elapsedRealtime()
    private var lastReminder = 0L
    private val firstVideos = HashMap<String, String>()
    private var returnInProgress = false
    private var blockVisible = false
    private var coverView: View? = null
    private var blockedPackage: String? = null
    private var blockedKind: BlockKind? = null
    private var returnStarted = 0L
    private var returnStep = 0
    private var stepStarted = 0L
    private var lastTapAt = 0L
    private var pendingContentPackage: String? = null
    /** Packages that showed a screen other than their feed during this visit. Back then stays in the app. */
    private val feedFreeScreen = HashSet<String>()
    /** When each feed started to show a video without a readable title. */
    private val untitledSince = HashMap<String, Long>()
    private val contentCheck = Runnable {
        pendingContentPackage?.let { checkContent(it, force = true) }
    }

    /**
     * Recovers in the blocked app under the cover, one step per run.
     * Websites load a blank page in the same tab. Feeds go Back to the page that opened them,
     * or to a safe tab when the feed is itself a tab or the first screen of this visit.
     * Every step waits for the app to react before it tries the next one.
     */
    private val returnSettle = object : Runnable {
        override fun run() {
            val pkg = blockedPackage ?: return finishReturn()
            val kind = blockedKind ?: return finishReturn()
            val front = appInFront()
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - returnStarted
            if (front != pkg) {
                // The block activity needs a frame to finish. Never send actions to another app.
                if ((front == null || front == packageName) && elapsed < 800) handler.postDelayed(this, 50)
                else finishReturn()
                return
            }
            val roots = windowRoots(pkg)
            if (roots.isEmpty()) {
                if (elapsed < 1200) handler.postDelayed(this, 80) else finishReturn()
                return
            }
            if (kind == BlockKind.SITE) settleSite(pkg, roots, elapsed) else settleFeed(pkg, kind, roots, now, elapsed)
        }
    }

    private fun settleSite(pkg: String, roots: List<AccessibilityNodeInfo>, elapsed: Long) {
        val now = SystemClock.elapsedRealtime()
        if (returnStep == 0) {
            // The rule can change while the block is open. Keep a page that is now allowed.
            val host = roots.firstNotNullOfOrNull { it.browserHost() }
            if (host != null && blockedSite(host, rules) == null) return finishReturn()
            // Some toolbars take a second to open their editor. Another tap meanwhile would close it again.
            val editing = roots.any { it.browserAddressBar()?.isFocused == true }
            if (editing || now - lastTapAt >= EDITOR_WAIT_MILLIS) {
                if (!editing) lastTapAt = now
                if (roots.any { it.clearBrowserPage(::tapBrowserControl) }) returnStep = 1
            }
        } else if (roots.none { it.browserAddressBar()?.isFocused == true }) {
            finishReturn()
            checkContent(pkg, force = true)
            return
        }
        if (elapsed < 2500) {
            handler.postDelayed(returnSettle, 100)
            return
        }
        // This browser did not load the blank page. Its blocked page must not stay usable.
        finishReturn()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun settleFeed(pkg: String, kind: BlockKind, roots: List<AccessibilityNodeInfo>, now: Long, elapsed: Long) {
        if (!contentStillBlocked(pkg, roots)) return finishReturn()
        val feed = shortsFeeds.firstOrNull { it.packageName == pkg && kind == BlockKind.SHORTS }
        when {
            returnStep == 0 -> {
                val asTab = feed != null && (roots.any(feed::tabSelected) || pkg !in feedFreeScreen)
                when {
                    kind == BlockKind.STUDY && rules.settings.blockYoutubeHome && roots.any { it.youtubeHome() } ->
                        if (!roots.any { it.openYoutubeSearch() }) clickSafeNav(pkg, roots)
                    // A feed tab has no page under it. Back could leave the app, so pick another tab.
                    asTab && clickSafeNav(pkg, roots) -> Unit
                    else -> performGlobalAction(GLOBAL_ACTION_BACK)
                }
                returnStep = 1
                stepStarted = now
            }
            // Search and profile pages need a moment to replace the player before the next try.
            returnStep == 1 && now - stepStarted >= FEED_SETTLE_MILLIS -> {
                clickSafeNav(pkg, roots)
                returnStep = 2
                stepStarted = now
            }
            returnStep == 2 && now - stepStarted >= FEED_SETTLE_MILLIS || elapsed >= 2500 -> {
                // Nothing left the feed. Show the block again instead of the feed.
                finishReturn()
                checkContent(pkg, force = true)
                return
            }
        }
        handler.postDelayed(returnSettle, 100)
    }

    private fun finishReturn() {
        returnInProgress = false
        handler.removeCallbacks(returnSettle)
        hideCover()
    }

    private fun contentRulesActive(): Boolean {
        val current = rules
        if (current.settings.contentOnlyDuringFocus && !current.focusing) return false
        return current.settings.pauseBlocksUntil <= System.currentTimeMillis() ||
            current.settings.protection && current.locked(LocalDateTime.now())
    }

    private fun contentStillBlocked(pkg: String, roots: List<AccessibilityNodeInfo>): Boolean {
        val current = rules
        if (!contentRulesActive()) return false
        if (shortsFeeds.any { it.packageName == pkg && it.enabled(current.settings) && roots.any(it::isShowing) }) return true
        if (pkg != "com.google.android.youtube") return false
        if (current.settings.blockYoutubeHome && roots.any { it.youtubeHome() }) return true
        if (!current.settings.youtubeStudyMode || roots.none { it.youtubePlayer() }) return false
        val channel = roots.firstNotNullOfOrNull { it.youtubeChannel() }
        return channel == null || current.settings.allowedYoutubeChannels.none { channelKey(it) == channelKey(channel) }
    }

    /** The blocked site for [host] under [current] rules, or null when the host is allowed. */
    private fun blockedSite(host: String, current: Rules): String? =
        if (current.settings.siteAllowList) host.takeIf { !allowedSite(it, current.sites) }
        else blockedSiteFor(host, current.sites, current.settings.blockAdultSites)

    private val returnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_COVER_READY -> if (!returnInProgress) hideCover()
                ACTION_BLOCK_CLOSED -> blockVisible = false
                ACTION_RETURN -> {
                    val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: return
                    val kind = BlockKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_BLOCK_KIND) } ?: return
                    if (kind !in setOf(BlockKind.SITE, BlockKind.SHORTS, BlockKind.STUDY)) return
                    // The accessibility service can reconnect while the block activity is still open.
                    blockedPackage = pkg
                    blockedKind = kind
                    returnToApp(pkg)
                }
            }
        }
    }

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
            if (intent.action == Intent.ACTION_SCREEN_OFF) { foreground = null; forgetVisit(); finishReturn() }
            Widgets.refresh(context)
        }
    }

    override fun onServiceConnected() {
        refreshSystemApps()
        // A reconnect gets no window event for the app already in front. Check it as soon as rules load.
        appInFront()?.let(::setForeground)
        val dao = app.dao
        scope.launch {
            combine(dao.settings(), dao.limits(), dao.schedules(), dao.sites(), dao.activeFocusFlow()) { settings, limits, schedules, sites, focus ->
                Rules(settings, limits.associateBy { it.packageName }, schedules, sites.map { it.domain }.toSet(), focus)
            }.collect {
                rules = it
                evaluate()
                foreground?.let { checkContent(it, force = true) }
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
        val actions = IntentFilter().apply {
            addAction(ACTION_RETURN)
            addAction(ACTION_COVER_READY)
            addAction(ACTION_BLOCK_CLOSED)
        }
        ContextCompat.registerReceiver(this, returnReceiver, actions, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideCover()
        runCatching { unregisterReceiver(returnReceiver) }
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

            // The Shorts tab click arrives before the player draws. Cover it now.
            AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                val settings = rules.settings
                if (pkg == "com.google.android.youtube" && settings.blockYoutubeShorts && !settings.allowFirstShort &&
                    contentRulesActive() && eventSaysShorts(event)) {
                    showCover()
                    handler.removeCallbacks(coverTimeout)
                    handler.postDelayed(coverTimeout, 700)
                }
                checkContent(pkg, force = true)
            }
        }
    }

    private val coverTimeout = Runnable {
        if (!returnInProgress) hideCover()
    }

    /** True when the event is the YouTube Shorts tab, not a video title that merely contains the word. */
    private fun eventSaysShorts(event: AccessibilityEvent): Boolean {
        val values = ArrayList<String>(4)
        event.contentDescription?.toString()?.let(values::add)
        event.text.forEach { it?.toString()?.let(values::add) }
        event.source?.contentDescription?.toString()?.let(values::add)
        event.source?.text?.toString()?.let(values::add)
        return values.any { labelMatches(it, "Shorts") }
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
            if (foreground != packageName) forgetVisit()
            visitStarted = SystemClock.elapsedRealtime()
            lastReminder = 0
        }
        foreground = pkg
    }

    private fun forgetVisit() {
        firstVideos.clear()
        feedFreeScreen.clear()
        untitledSince.clear()
    }

    private fun refreshSystemApps() {
        val now = SystemClock.elapsedRealtime()
        if (essentials.isNotEmpty() && now - systemAppsUpdatedAt < 30_000) return
        systemAppsUpdatedAt = now
        val catalog = app.catalog
        essentials = catalog.essentials()
        launchers = catalog.launchers()
        browsers = browserPackages()
    }

    private fun evaluate() {
        val pkg = foreground ?: return
        if (pkg == packageName) return
        val current = rules
        val essentials = essentials
        val launchers = launchers
        evaluation?.cancel()
        evaluation = scope.launch {
            val verdict = withContext(Dispatchers.Default) {
                current.decide(
                    pkg = pkg,
                    label = app.catalog.label(pkg),
                    now = LocalDateTime.now(),
                    essentials = essentials,
                    launchers = launchers,
                    allowedUntil = if (current.limits[pkg]?.enabled == true) app.dao.pass(LocalDate.now().toString(), pkg)?.expiresAt ?: 0 else 0,
                    use24Hour = uses24HourClock(current.settings.clockFormat),
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
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(getString(R.string.limit_reminder_title))
            .setContentText(resources.getQuantityString(R.plurals.limit_reminder_body, ((now - visitStarted) / 60_000).toInt(), app.catalog.label(pkg), (now - visitStarted) / 60_000))
            .setContentIntent(MainActivity.pendingHome(this)).setAutoCancel(true).build()
        runCatching { getSystemService(NotificationManager::class.java).notify(30, notification) }
    }

    private fun checkContent(pkg: String, force: Boolean) {
        if (returnInProgress || blockVisible && appInFront() == packageName) return
        if (pkg !in watchedPackages && pkg !in browsers && !rules.settings.blockMultiWindow) return
        val elapsed = SystemClock.elapsedRealtime()
        if (!force && elapsed - lastContentCheck < CONTENT_THROTTLE_MILLIS) {
            pendingContentPackage = pkg
            handler.removeCallbacks(contentCheck)
            handler.postDelayed(contentCheck, CONTENT_THROTTLE_MILLIS - (elapsed - lastContentCheck))
            return
        }
        handler.removeCallbacks(contentCheck)
        lastContentCheck = elapsed
        val now = System.currentTimeMillis()
        val current = rules
        val protected = current.settings.protection && current.locked(LocalDateTime.now())
        val contentEnabled = !current.settings.contentOnlyDuringFocus || current.focusing
        val watchesContent = contentEnabled && (
            shortsFeeds.any { it.packageName == pkg && it.enabled(current.settings) } ||
                pkg == "com.google.android.youtube" && (current.settings.blockYoutubeHome || current.settings.youtubeStudyMode) ||
                pkg in browsers && (current.sites.isNotEmpty() || current.settings.siteAllowList || current.settings.blockAdultSites)
            )
        if (!watchesContent && !(protected && pkg in protectedScreens) && !current.settings.blockMultiWindow) return
        val roots = windowRoots(pkg)
        if (roots.isEmpty()) return

        if (!protected && current.settings.pauseBlocksUntil > now) return
        if (current.settings.blockMultiWindow && current.locked(LocalDateTime.now()) && pkg !in essentials &&
            (windows.any { it.isInPictureInPictureMode } || windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root?.packageName?.toString() }.filter { it !in essentials }.distinct().size > 1)) {
            block(pkg, BlockReason(BlockKind.MULTI_WINDOW, textResource(R.string.block_windows_title), textResource(R.string.block_windows_detail)))
            return
        }
        if (contentEnabled) shortsFeeds.firstOrNull { it.packageName == pkg && it.enabled(current.settings) }?.let { feed ->
            val root = roots.firstOrNull(feed::isShowing)
            if (root == null) {
                feedFreeScreen += pkg
                untitledSince.remove(pkg)
            } else {
                if (current.settings.allowFirstShort && feed.titleIds.isNotEmpty()) {
                    val identity = feed.videoIdentity(root)
                    val first = firstVideos[pkg]
                    if (identity == null) {
                        // A title can draw after the player. Wait a moment, then block a video without one.
                        val since = untitledSince.getOrPut(pkg) { elapsed }
                        if (elapsed - since < TITLE_WAIT_MILLIS) {
                            pendingContentPackage = pkg
                            handler.postDelayed(contentCheck, TITLE_WAIT_MILLIS - (elapsed - since))
                            return
                        }
                    } else {
                        untitledSince.remove(pkg)
                        if (first == null || first == identity) {
                            firstVideos[pkg] = identity
                            return
                        }
                    }
                }
                silence(roots)
                block(pkg, BlockReason(BlockKind.SHORTS, textResource(R.string.block_short_title, textResource(feed.nameRes)), textResource(R.string.block_short_detail)))
                return
            }
        }
        if (contentEnabled && pkg == "com.google.android.youtube") {
            if (current.settings.blockYoutubeHome && roots.any { it.youtubeHome() }) {
                block(pkg, BlockReason(BlockKind.STUDY, textResource(R.string.block_youtube_home_title), textResource(R.string.block_youtube_home_detail)))
                return
            }
            if (current.settings.youtubeStudyMode && roots.any { it.youtubePlayer() }) {
                val channel = roots.firstNotNullOfOrNull { it.youtubeChannel() }
                if (channel == null || current.settings.allowedYoutubeChannels.none { channelKey(it) == channelKey(channel) }) {
                    silence(roots)
                    block(pkg, BlockReason(BlockKind.STUDY, textResource(R.string.block_channel_title), textResource(R.string.block_channel_detail)))
                    return
                }
            }
        }

        roots.takeIf { pkg in browsers }?.firstNotNullOfOrNull { it.browserHost() }?.let { host ->
            if (!contentEnabled) return@let
            val site = blockedSite(host, current) ?: return@let
            block(pkg, BlockReason(BlockKind.SITE, textResource(R.string.block_site_title, site), textResource(R.string.block_site_detail)))
            return
        }

        if (current.settings.protection && pkg in protectedScreens && current.locked(LocalDateTime.now()) &&
            roots.any { it.containsText(app.catalog.label(packageName)) }
        ) {
            block(pkg, BlockReason(BlockKind.PROTECTION, textResource(R.string.block_settings_title), textResource(R.string.block_settings_detail)))
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
     * Covers the screen before the block activity draws, then opens that activity.
     * A global back press is not used here. It can dismiss the block and play the video again.
     */
    private fun block(pkg: String, reason: BlockReason) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < BLOCK_DEBOUNCE_MILLIS) return
        lastBlockAt = now
        if (blockVisible && appInFront() == packageName) return
        blockVisible = true
        blockedPackage = pkg
        blockedKind = reason.kind
        showCover()
        startActivity(BlockActivity.intent(this, pkg, reason))
        handler.removeCallbacks(coverTimeout)
        handler.postDelayed(coverTimeout, 2000)
        if (reason.kind == BlockKind.SHORTS || reason.kind == BlockKind.STUDY) {
            handler.postDelayed({ if (blockVisible && blockedPackage == pkg) dismissPip() }, 400)
        }
    }

    /**
     * Clicks a safe tab. Home comes before Subscriptions when the home feed is allowed.
     * Each label is tried on every window before the next label, so a small window cannot win.
     */
    private fun clickSafeNav(pkg: String, roots: List<AccessibilityNodeInfo>): Boolean {
        val labels = safeNavLabels(pkg)
        return labels.any { label -> roots.any { it.clickNav(label) } }
    }

    private fun safeNavLabels(pkg: String): List<String> = when (pkg) {
        "com.google.android.youtube" -> if (rules.settings.blockYoutubeHome) listOf("Subscriptions", "You", "Library") else listOf("Home", "Subscriptions")
        "com.instagram.android" -> listOf("Home", "Feed")
        "com.snapchat.android" -> listOf("Chat", "Friends")
        "com.facebook.katana" -> listOf("Home", "News Feed", "Feeds")
        else -> emptyList()
    }

    /** Closes a picture-in-picture window. The close control sits in the system window, not in the video. */
    private fun dismissPip() {
        if (clickPipClose()) return
        val pip = windows.firstOrNull { it.isInPictureInPictureMode } ?: return
        val bounds = android.graphics.Rect()
        pip.getBoundsInScreen(bounds)
        if (bounds.width() < 2 || bounds.height() < 2) return
        val path = android.graphics.Path().apply {
            moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 40)
        dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(),
            object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    handler.postDelayed({ clickPipClose() }, 280)
                }
            },
            null,
        )
    }

    /** The close control is drawn next to the video. Do not click every Close label on the phone. */
    private fun clickPipClose(): Boolean {
        val pip = windows.firstOrNull { it.isInPictureInPictureMode } ?: return false
        val area = android.graphics.Rect()
        pip.getBoundsInScreen(area)
        area.inset(-120, -160)
        if (area.isEmpty) return false
        return windows.mapNotNull { it.root }.any { clickCloseIn(it, area) }
    }

    private fun clickCloseIn(node: AccessibilityNodeInfo, area: android.graphics.Rect): Boolean {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        val label = (node.contentDescription ?: node.text)?.toString()?.trim().orEmpty()
        val close = label.equals("Close", true) || label.equals("Dismiss", true) || label.contains("picture-in-picture", true)
        if (close && android.graphics.Rect.intersects(area, bounds) &&
            (node.performAction(AccessibilityNodeInfo.ACTION_CLICK) || node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        ) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickCloseIn(child, area)) return true
        }
        return false
    }

    /** Stops the video that is about to be covered. */
    private fun silence(roots: List<AccessibilityNodeInfo>) {
        roots.forEach { root ->
            listOf("Pause video", "Pause", "Pause Short").forEach { root.clickNav(it) }
        }
        val clock = SystemClock.uptimeMillis()
        val audio = getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(clock, clock, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(clock, clock, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
    }

    /** Finish the block activity first, then recover in the existing app task under the cover. */
    private fun returnToApp(pkg: String) {
        if (returnInProgress || pkg != blockedPackage) return
        returnInProgress = true
        blockVisible = false
        returnStarted = SystemClock.elapsedRealtime()
        returnStep = 0
        stepStarted = returnStarted
        lastTapAt = 0L
        showCover()
        handler.removeCallbacks(returnSettle)
        handler.post(returnSettle)
    }

    /** Tap only a known native address control when its accessibility click action is missing. */
    private fun tapBrowserControl(node: AccessibilityNodeInfo) {
        if (!returnInProgress || appInFront() != blockedPackage) return
        val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
        val display = resources.displayMetrics
        if (!bounds.intersect(0, 0, display.widthPixels, display.heightPixels) || bounds.isEmpty) return
        val path = android.graphics.Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        dispatchGesture(android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 40)).build(), null, null)
    }

    private fun showCover() {
        if (coverView != null) return
        val view = View(this).apply { setBackgroundColor(0xFF0E1018.toInt()) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                (if (returnInProgress) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0) or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 30) {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        runCatching { getSystemService(WindowManager::class.java).addView(view, params) }.onSuccess { coverView = view }
    }

    private fun hideCover() {
        val view = coverView ?: return
        coverView = null
        runCatching { getSystemService(WindowManager::class.java).removeView(view) }
    }

    companion object {
        const val ACTION_RETURN = "dev.agneswd.stillpoint.action.RETURN_TO_APP"
        const val ACTION_COVER_READY = "dev.agneswd.stillpoint.action.COVER_READY"
        const val ACTION_BLOCK_CLOSED = "dev.agneswd.stillpoint.action.BLOCK_CLOSED"
        const val EXTRA_BLOCKED_PACKAGE = "package"
        const val EXTRA_BLOCK_KIND = "kind"

        private const val TICK_MILLIS = 20_000L
        private const val CONTENT_THROTTLE_MILLIS = 80L
        private const val BLOCK_DEBOUNCE_MILLIS = 300L
        private const val FEED_SETTLE_MILLIS = 900L
        private const val EDITOR_WAIT_MILLIS = 1200L
        private const val TITLE_WAIT_MILLIS = 1500L

        /** Screens where someone can turn off or remove this app. */
        private val protectedScreens = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )

        private val watchedPackages = shortsFeeds.map { it.packageName }.toSet() + protectedScreens

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
