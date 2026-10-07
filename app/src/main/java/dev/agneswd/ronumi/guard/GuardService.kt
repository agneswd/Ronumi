package dev.agneswd.ronumi.guard

import dev.agneswd.ronumi.plus.Entitlement

import dev.agneswd.ronumi.ui.textResource
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
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.ui.MainActivity
import dev.agneswd.ronumi.widget.Widgets
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
import dev.agneswd.ronumi.Distribution
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.RonumiApp
import dev.agneswd.ronumi.consent.ConsentKind
import dev.agneswd.ronumi.consent.Consents
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
    private var appListReady = false
    private val guardsSystemScreens = Distribution.guardsSystemScreens
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
    private var returnClockStarted = false
    private var returnStep = 0
    private var stepStarted = 0L
    private var lastTapAt = 0L
    /** Packages that showed a screen other than their feed during this visit. Back then stays in the app. */
    private val feedFreeScreen = HashSet<String>()
    /** When each feed started to show a video without a readable title. */
    private val untitledSince = HashMap<String, Long>()
    /** One content check per window. A newer event for a queued window replaces the hint. */
    private val contentWindows = HashMap<Int, ContentWindow>()
    private val contentOrder = ArrayDeque<Int>()
    private val queuedHints = HashMap<Int, ContentHint>()
    private val queuedPackages = HashMap<Int, String>()
    private var contentPumpPosted = false
    private var titlePackage: String? = null
    private var titleWindow = -1
    private val titleCheck = Runnable {
        titlePackage?.let { checkContent(it, force = true, windowId = titleWindow) }
    }
    private val contentPump = Runnable {
        contentPumpPosted = false
        val windowId = if (contentOrder.isEmpty()) return@Runnable else contentOrder.removeFirst()
        val state = contentWindows[windowId]
        if (state != null) state.queued = false
        val pkg = queuedPackages.remove(windowId)
        val hint = queuedHints.remove(windowId)
        if (pkg != null) checkContent(pkg, force = false, windowId = windowId, hint = hint)
        postContentPump()
    }

    /**
     * Recovers in the blocked app under the cover, one step per run.
     * A website loads a blank page in the same tab.
     * A feed goes Back, or to a safe tab when Back would leave the app.
     * Each step waits for the app to react.
     */
    private val returnSettle = object : Runnable {
        override fun run() {
            val pkg = blockedPackage ?: return finishReturn()
            val kind = blockedKind ?: return finishReturn()
            // Windows can omit the app for a moment after the block activity finishes.
            // rootInActiveWindow still names it. Do not send actions to a different app.
            var front = appInFront()
            if (front == null && rootInActiveWindow?.packageName?.toString() == pkg) front = pkg
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - returnStarted
            if (front != pkg) {
                if ((front == null || front == packageName) && elapsed < FRONT_WAIT_MILLIS) handler.postDelayed(this, 50)
                else finishReturn()
                return
            }
            if (!returnClockStarted) {
                returnClockStarted = true
                returnStarted = now
                stepStarted = now
            }
            val roots = windowRoots(pkg)
            if (roots.isEmpty()) {
                val limit = if (kind == BlockKind.SITE) SITE_RECOVERY_MILLIS else FRONT_WAIT_MILLIS
                if (now - returnStarted < limit) handler.postDelayed(this, 80)
                else if (kind == BlockKind.SITE) leaveBlockedPage()
                else finishReturn()
                return
            }
            val settleElapsed = now - returnStarted
            if (kind == BlockKind.SITE) settleSite(roots, settleElapsed) else settleFeed(pkg, kind, roots, now, settleElapsed)
        }
    }

    /**
     * Replaces the blocked page with about:blank in the same tab.
     * A missing host is not success. The bar must show about:blank, or a host the rules allow.
     * On timeout, go Home. Do not read the page again and show the block screen.
     */
    private fun settleSite(roots: List<AccessibilityNodeInfo>, elapsed: Long) {
        val now = SystemClock.elapsedRealtime()
        val bar = roots.firstNotNullOfOrNull { it.browserAddressBar() }
        val editing = bar?.isFocused == true
        if (siteCleared(bar, editing)) {
            finishReturn()
            return
        }
        if (returnStep == 0 && (editing || now - lastTapAt >= EDITOR_WAIT_MILLIS)) {
            if (!editing) lastTapAt = now
            if (roots.any { it.clearBrowserPage(::tapBrowserControl) }) returnStep = 1
        } else if (returnStep > 0 && !editing && blockedAddress(bar)) {
            if (now - lastTapAt >= EDITOR_WAIT_MILLIS) {
                lastTapAt = now
                roots.any { it.clearBrowserPage(::tapBrowserControl) }
            }
        }
        if (elapsed < SITE_RECOVERY_MILLIS) {
            handler.postDelayed(returnSettle, 100)
            return
        }
        leaveBlockedPage()
    }

    /** The bar shows about:blank, or a host that the current rules allow. */
    private fun siteCleared(bar: AccessibilityNodeInfo?, editing: Boolean): Boolean {
        if (editing || bar == null) return false
        val address = addressText(bar)
        if (isBlankPage(address)) return true
        val host = hostOf(address) ?: return false
        return blockedSite(host, rules) == null
    }

    /** The bar shows a host that the current rules still block. An unreadable bar is not a block. */
    private fun blockedAddress(bar: AccessibilityNodeInfo?): Boolean {
        if (bar == null) return false
        val host = hostOf(addressText(bar)) ?: return false
        return blockedSite(host, rules) != null
    }

    private fun addressText(bar: AccessibilityNodeInfo): String {
        val text = bar.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty() || bar.viewIdResourceName?.substringAfterLast('/') != "ADDRESSBAR_URL_BOX") return text
        return bar.contentDescription?.toString()?.trim()?.substringBefore(' ')?.trimEnd('.').orEmpty()
    }

    private fun isBlankPage(address: String): Boolean {
        val value = address.trim()
        return value.equals("about:blank", true) ||
            value.startsWith("about:blank/", true) ||
            value.startsWith("about:blank?", true) ||
            value.startsWith("about:blank#", true)
    }

    /** Hides the cover and leaves the blocked page. The page must not stay in front. */
    private fun leaveBlockedPage() {
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
            returnStep == 2 && now - stepStarted >= FEED_SETTLE_MILLIS || elapsed >= FRONT_WAIT_MILLIS -> {
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
            if (accessibilityAllowed()) {
                if (Consents.granted(this@GuardService, ConsentKind.APP_LIST) != appListReady) refreshSystemApps()
                if (getSystemService(PowerManager::class.java).isInteractive) {
                    appInFront()?.let(::setForeground)
                    evaluate()
                    foreground?.let { checkContent(it, force = true) }
                }
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
        if (accessibilityAllowed()) {
            refreshSystemApps()
            // A reconnect gets no window event for the app already in front. Check it as soon as rules load.
            appInFront()?.let(::setForeground)
        }
        val dao = app.dao
        scope.launch {
            val saved = combine(dao.settings(), dao.limits(), dao.schedules(), dao.sites(), dao.activeFocusFlow()) { settings, limits, schedules, sites, focus ->
                Rules(settings, limits.associateBy { it.packageName }, schedules, sites.map { it.domain }.toSet(), focus)
            }
            // Without Plus, only the free part of the saved rules applies. A refund takes effect at once.
            combine(saved, app.plus.state) { rules, plus -> rules.forPlus(plus.entitlement == Entitlement.UNLOCKED) }.collect {
                rules = it
                if (!accessibilityAllowed()) return@collect
                evaluate()
                foreground?.let { pkg -> checkContent(pkg, force = true) }
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

    /**
     * Event paths. The service XML still requests every type below.
     * TYPE_WINDOW_STATE_CHANGED: immediate full check. This path needs the new window.
     * TYPE_VIEW_SCROLLED: one check per window, at least 80 ms apart. A repeat scroll
     * of a window that was already checked does not walk the protected-screen tree.
     * TYPE_WINDOW_CONTENT_CHANGED: same coalesce. An address-bar change reads that node.
     * A list change skips the protected-screen tree. Any other change walks the tree.
     * TYPE_VIEW_CLICKED and TYPE_VIEW_SELECTED: immediate full check. Shorts uses the click.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!accessibilityAllowed()) return
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val front = appInFront() ?: pkg.takeIf { isActivity(it, event.className?.toString()) }
                if (front != null) {
                    if (front != foreground) refreshSystemApps()
                    setForeground(front)
                    evaluate()
                }
                noteWindowState(event.windowId)
                checkContent(pkg, force = true, windowId = event.windowId)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_VIEW_SCROLLED ->
                scheduleContent(pkg, event)

            // The Shorts tab click arrives before the player draws. Cover it now.
            AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                val settings = rules.settings
                if (pkg == "com.google.android.youtube" && settings.blockYoutubeShorts && !settings.allowFirstShort &&
                    contentRulesActive() && eventSaysShorts(event)) {
                    showCover()
                    handler.removeCallbacks(coverTimeout)
                    handler.postDelayed(coverTimeout, 700)
                }
                checkContent(pkg, force = true, windowId = event.windowId)
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
        val ready = Consents.granted(this, ConsentKind.APP_LIST)
        val now = SystemClock.elapsedRealtime()
        if (ready == appListReady && essentials.isNotEmpty() && now - systemAppsUpdatedAt < 30_000) return
        appListReady = ready
        systemAppsUpdatedAt = now
        val catalog = app.catalog
        essentials = catalog.essentials()
        launchers = catalog.launchers()
        browsers = if (ready) browserPackages() else emptySet()
    }

    private fun accessibilityAllowed(): Boolean = Consents.granted(this, ConsentKind.ACCESSIBILITY)

    private fun evaluate() {
        if (!accessibilityAllowed()) return
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
        val notification = NotificationCompat.Builder(this, RonumiApp.CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(getString(R.string.limit_reminder_title))
            .setContentText(resources.getQuantityString(R.plurals.limit_reminder_body, ((now - visitStarted) / 60_000).toInt(), app.catalog.label(pkg), (now - visitStarted) / 60_000))
            .setContentIntent(MainActivity.pendingHome(this)).setAutoCancel(true).build()
        runCatching { getSystemService(NotificationManager::class.java).notify(30, notification) }
    }

    private fun checkContent(pkg: String, force: Boolean, windowId: Int = -1, hint: ContentHint? = null) {
        if (!accessibilityAllowed()) return
        if (returnInProgress || blockVisible && appInFront() == packageName) return
        val watched = if (guardsSystemScreens) watchedPackages else watchedPackages - protectedScreens
        if (pkg !in watched && pkg !in browsers && !rules.settings.blockMultiWindow) return
        val elapsed = SystemClock.elapsedRealtime()
        if (!force && elapsed - lastContentCheck < CONTENT_THROTTLE_MILLIS) {
            scheduleContent(pkg, windowId, hint ?: ContentHint(unscopedContent = true, addressInBar = false, addressHost = null))
            return
        }
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
        val shieldsSettings = guardsSystemScreens && protected && pkg in protectedScreens
        if (!watchesContent && !shieldsSettings && !current.settings.blockMultiWindow) return
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
                            titlePackage = pkg
                            titleWindow = windowId
                            handler.removeCallbacks(titleCheck)
                            handler.postDelayed(titleCheck, TITLE_WAIT_MILLIS - (elapsed - since))
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

        val cached = if (windowId >= 0) contentWindows.getOrPut(windowId) { ContentWindow() } else null
        val sameWindow = cached != null && cached.checkedEpoch == cached.epoch
        if (contentEnabled && pkg in browsers) {
            val host = browserHostFor(roots, cached, sameWindow, force, hint)
            if (host != null) {
                val site = blockedSite(host, current)
                if (site != null) {
                    markChecked(cached)
                    block(pkg, BlockReason(BlockKind.SITE, textResource(R.string.block_site_title, site), textResource(R.string.block_site_detail)))
                    return
                }
            }
        }

        // A scroll or a list change keeps the previous full check. A window change clears it.
        val skipProtected = !force && sameWindow && hint != null && !hint.unscopedContent
        if (!skipProtected && guardsSystemScreens && current.settings.protection && pkg in protectedScreens && current.locked(LocalDateTime.now()) &&
            roots.any { it.containsText(app.catalog.label(packageName)) }
        ) {
            markChecked(cached)
            block(pkg, BlockReason(BlockKind.PROTECTION, textResource(R.string.block_settings_title), textResource(R.string.block_settings_detail)))
            return
        }
        markChecked(cached)
    }

    private fun markChecked(cached: ContentWindow?) {
        if (cached != null) cached.checkedEpoch = cached.epoch
    }

    /**
     * Uses the changed address node when that node is enough.
     * A scroll of the same window reuses the host from the last full check.
     */
    private fun browserHostFor(
        roots: List<AccessibilityNodeInfo>,
        cached: ContentWindow?,
        sameWindow: Boolean,
        force: Boolean,
        hint: ContentHint?,
    ): String? {
        if (!force && hint?.addressInBar == true) {
            if (cached != null) {
                cached.host = hint.addressHost
                cached.hostKnown = true
            }
            return hint.addressHost
        }
        if (!force && sameWindow && cached != null && cached.hostKnown && hint?.unscopedContent != true) return cached.host
        val found = roots.firstNotNullOfOrNull { it.browserHost() }
        if (cached != null) {
            cached.host = found
            cached.hostKnown = true
        }
        return found
    }

    private fun noteWindowState(windowId: Int) {
        if (contentWindows.size > 40) contentWindows.clear()
        val state = contentWindows.getOrPut(windowId) { ContentWindow() }
        state.epoch++
        state.hostKnown = false
        state.checkedEpoch = -1
        dropQueued(windowId)
    }

    private fun scheduleContent(pkg: String, event: AccessibilityEvent) {
        scheduleContent(pkg, event.windowId, hintOf(event))
    }

    private fun scheduleContent(pkg: String, windowId: Int, hint: ContentHint) {
        if (contentWindows.size > 40) contentWindows.clear()
        val state = contentWindows.getOrPut(windowId) { ContentWindow() }
        queuedPackages[windowId] = pkg
        queuedHints[windowId] = mergeHint(queuedHints[windowId], hint)
        if (state.queued) return
        state.queued = true
        contentOrder.addLast(windowId)
        postContentPump()
    }

    private fun dropQueued(windowId: Int) {
        contentOrder.remove(windowId)
        contentWindows[windowId]?.let { it.queued = false }
        queuedHints.remove(windowId)
        queuedPackages.remove(windowId)
    }

    private fun postContentPump() {
        if (contentPumpPosted || contentOrder.isEmpty()) return
        val wait = (CONTENT_THROTTLE_MILLIS - (SystemClock.elapsedRealtime() - lastContentCheck)).coerceAtLeast(0)
        contentPumpPosted = true
        handler.postDelayed(contentPump, wait)
    }

    private fun hintOf(event: AccessibilityEvent): ContentHint {
        val scrolled = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        val source = event.source ?: return ContentHint(unscopedContent = !scrolled, addressInBar = false, addressHost = null)
        return try {
            val address = source.nearbyAddress()
            val inList = source.inAppList()
            ContentHint(
                unscopedContent = !scrolled && !inList && !address.inBar,
                addressInBar = address.inBar,
                addressHost = address.host,
            )
        } finally {
            source.recycle()
        }
    }

    private fun mergeHint(old: ContentHint?, new: ContentHint) = ContentHint(
        unscopedContent = old?.unscopedContent == true || new.unscopedContent,
        addressInBar = old?.addressInBar == true || new.addressInBar,
        addressHost = if (new.addressInBar) new.addressHost else old?.addressHost,
    )

    private class ContentWindow {
        var epoch = 0
        var checkedEpoch = -1
        var host: String? = null
        var hostKnown = false
        var queued = false
    }

    private data class ContentHint(val unscopedContent: Boolean, val addressInBar: Boolean, val addressHost: String?)

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

    /** The picture-in-picture window of [pkg], when that package is still the blocked one. */
    private fun pipWindow(pkg: String): AccessibilityWindowInfo? =
        windows.firstOrNull { it.isInPictureInPictureMode && it.root?.packageName?.toString() == pkg }

    private fun pipStill(pkg: String): Boolean =
        blockVisible && blockedPackage == pkg && pipWindow(pkg) != null

    /** Closes the picture-in-picture window of the blocked app. Another app's window stays. */
    private fun dismissPip() {
        val pkg = blockedPackage ?: return
        if (!pipStill(pkg)) return
        if (clickPipClose(pkg)) return
        val pip = pipWindow(pkg) ?: return
        val bounds = android.graphics.Rect()
        pip.getBoundsInScreen(bounds)
        if (bounds.width() < 2 || bounds.height() < 2 || !pipStill(pkg)) return
        val path = android.graphics.Path().apply {
            moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 40)
        dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build(),
            object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    handler.postDelayed({ if (pipStill(pkg)) clickPipClose(pkg) }, 280)
                }
            },
            null,
        )
    }

    /** The close control is drawn next to the video. Do not click every Close label on the phone. */
    private fun clickPipClose(pkg: String): Boolean {
        if (!pipStill(pkg)) return false
        val pip = pipWindow(pkg) ?: return false
        val area = android.graphics.Rect()
        pip.getBoundsInScreen(area)
        area.inset(-120, -160)
        if (area.isEmpty) return false
        val roots = ArrayList<AccessibilityNodeInfo>()
        for (window in windows) {
            val root = window.root ?: continue
            val owner = root.packageName?.toString()
            if (owner == pkg) {
                roots.add(root)
            } else if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION && owner != packageName && windowOverlaps(window, area)) {
                roots.add(root)
            }
        }
        return roots.any { clickCloseIn(it, area, pkg) }
    }

    private fun windowOverlaps(window: AccessibilityWindowInfo, area: android.graphics.Rect): Boolean {
        val bounds = android.graphics.Rect()
        window.getBoundsInScreen(bounds)
        return android.graphics.Rect.intersects(area, bounds)
    }

    private fun clickCloseIn(node: AccessibilityNodeInfo, area: android.graphics.Rect, pkg: String): Boolean {
        if (!pipStill(pkg)) return false
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        val label = (node.contentDescription ?: node.text)?.toString()?.trim().orEmpty()
        val close = label.equals("Close", true) || label.equals("Dismiss", true) || label.contains("picture-in-picture", true)
        if (close && android.graphics.Rect.intersects(area, bounds) && pipStill(pkg) &&
            (node.performAction(AccessibilityNodeInfo.ACTION_CLICK) || node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        ) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickCloseIn(child, area, pkg)) return true
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
        returnClockStarted = false
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
        const val ACTION_RETURN = "dev.agneswd.ronumi.action.RETURN_TO_APP"
        const val ACTION_COVER_READY = "dev.agneswd.ronumi.action.COVER_READY"
        const val ACTION_BLOCK_CLOSED = "dev.agneswd.ronumi.action.BLOCK_CLOSED"
        const val EXTRA_BLOCKED_PACKAGE = "package"
        const val EXTRA_BLOCK_KIND = "kind"

        private const val TICK_MILLIS = 20_000L
        private const val CONTENT_THROTTLE_MILLIS = 80L
        private const val BLOCK_DEBOUNCE_MILLIS = 300L
        private const val FEED_SETTLE_MILLIS = 900L
        private const val EDITOR_WAIT_MILLIS = 1200L
        private const val TITLE_WAIT_MILLIS = 1500L
        /** How long to wait for the blocked app to return to the front. */
        private const val FRONT_WAIT_MILLIS = 2_500L
        /** How long a website may take to load about:blank before the phone goes Home. */
        private const val SITE_RECOVERY_MILLIS = 4_000L

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
