package dev.agneswd.stillpoint.guard

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
    private val activityCache = HashMap<ComponentName, Boolean>()
    private var lastContentCheck = 0L
    private var lastBlockAt = 0L
    private var visitStarted = SystemClock.elapsedRealtime()
    private var lastReminder = 0L
    private val firstVideos = HashMap<String, String>()
    private var returnInProgress = false
    private var blockVisible = false
    private var bypassDebounce = false
    private var blockedThisCheck = false
    private var coverView: View? = null
    private var returnPackage: String? = null
    private var ytPlace = YoutubePlace.OTHER
    private var ytQuery: String? = null
    private var returnByBack = false
    private var searchRestoreTries = 0

    private val returnSettle: Runnable = object : Runnable {
        override fun run() {
            val pkg = returnPackage
            val back = returnByBack
            returnByBack = false
            returnInProgress = false
            bypassDebounce = true
            blockedThisCheck = false
            val restoreSearch = back && pkg == "com.google.android.youtube" && !landedOnSearch(pkg)
            if (!restoreSearch && pkg != null && (foreground == pkg || appInFront() == pkg)) checkContent(pkg, force = true)
            if (restoreSearch && searchRestoreTries < 1) {
                searchRestoreTries++
                returnInProgress = true
                launchYoutubeSearch()
                bypassDebounce = false
                handler.postDelayed(this, RETURN_SETTLE_MILLIS)
            } else {
                searchRestoreTries = 0
                bypassDebounce = false
                if (!blockedThisCheck) hideCover()
            }
        }
    }

    private val returnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_COVER_READY -> if (!returnInProgress) hideCover()
                ACTION_BLOCK_CLOSED -> blockVisible = false
                ACTION_RETURN -> returnToApp(intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: foreground ?: return)
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
        val actions = IntentFilter().apply {
            addAction(ACTION_RETURN)
            addAction(ACTION_COVER_READY)
            addAction(ACTION_BLOCK_CLOSED)
        }
        ContextCompat.registerReceiver(this, returnReceiver, actions, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(returnSettle)
        handler.removeCallbacks(coverTimeout)
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
                if (pkg == "com.google.android.youtube" && rules.settings.blockYoutubeShorts && eventSaysShorts(event)) {
                    showCover()
                    handler.removeCallbacks(coverTimeout)
                    handler.postDelayed(coverTimeout, 700)
                }
                checkContent(pkg, force = true)
            }
        }
    }

    private val coverTimeout = Runnable {
        if (!blockVisible && !returnInProgress) hideCover()
    }

    /** True when the event is the YouTube Shorts tab, not a video title that merely contains the word. */
    private fun eventSaysShorts(event: AccessibilityEvent): Boolean {
        val values = ArrayList<String>(4)
        event.contentDescription?.toString()?.let(values::add)
        event.text.forEach { it?.toString()?.let(values::add) }
        event.source?.contentDescription?.toString()?.let(values::add)
        event.source?.text?.toString()?.let(values::add)
        return values.any { value ->
            val text = value.trim()
            text.equals("Shorts", true) || text.startsWith("Shorts ", true) || text.startsWith("Shorts,", true)
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
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("Time to take a break?")
            .setContentText("You have used ${app.catalog.label(pkg)} for ${(now - visitStarted) / 60_000} minutes.")
            .setContentIntent(MainActivity.pendingHome(this)).setAutoCancel(true).build()
        runCatching { getSystemService(NotificationManager::class.java).notify(30, notification) }
    }

    private fun checkContent(pkg: String, force: Boolean) {
        // Remember search before the rules finish loading. Otherwise the first blocked Short has no page to return to.
        if (pkg == "com.google.android.youtube") {
            val early = windowRoots(pkg)
            if (early.isNotEmpty()) noteYoutubePlace(early)
        }
        if (pkg !in watchedPackages && !rules.settings.blockMultiWindow) return
        val elapsed = SystemClock.elapsedRealtime()
        if (!force && elapsed - lastContentCheck < CONTENT_THROTTLE_MILLIS) return
        lastContentCheck = elapsed
        val now = System.currentTimeMillis()
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
        if (returnInProgress) return
        // The blocked feed stays in the window under the block screen and keeps sending events.
        // Starting the block again would cover the screen and never take the cover down.
        if (blockVisible && !bypassDebounce && appInFront() == packageName) return
        val contentEnabled = !current.settings.contentOnlyDuringFocus || current.focusing
        if (contentEnabled) shortsFeeds.firstOrNull { it.packageName == pkg && it.enabled(current.settings) }?.let { feed ->
            val root = roots.firstOrNull(feed::isShowing)
            if (root != null) {
                val identity = feed.videoIdentity(root)
                val first = firstVideos[pkg]
                // Wait for a title before allowing one video. A missing title must not open the feed.
                if (current.settings.allowFirstShort) {
                    if (identity == null) return
                    if (first == null || first == identity) {
                        firstVideos[pkg] = identity
                        return
                    }
                }
                silence(roots)
                // Cover first, then leave the player. A new activity on a playing video moves
                // YouTube into a picture-in-picture window, and the video keeps going.
                // A Short opened from search keeps its back stack. Home or Subscriptions would replace that search.
                showCover()
                val fromSearch = ytPlace == YoutubePlace.SEARCH
                val left = if (fromSearch) false else clickSafeNav(pkg, roots)
                block(pkg, BlockReason(BlockKind.SHORTS, "${feed.name} is blocked", "The rest of the app still works."), reveal = left)
                return
            }
        }
        if (contentEnabled && pkg == "com.google.android.youtube") {
            if (current.settings.blockYoutubeHome && roots.any { it.youtubeHome() }) {
                // Open search under the cover. Closing the block then shows search, not the feed.
                roots.any { it.openYoutubeSearch() }
                block(pkg, BlockReason(BlockKind.STUDY, "YouTube home is blocked", "Search for a video or use your subscriptions."), reveal = true)
                return
            }
            if (current.settings.youtubeStudyMode && roots.any { it.youtubePlayer() }) {
                val channel = roots.firstNotNullOfOrNull { it.youtubeChannel() }
                if (channel == null || current.settings.allowedYoutubeChannels.none { channelKey(it) == channelKey(channel) }) {
                    silence(roots)
                    showCover()
                    val left = clickSafeNav(pkg, roots)
                    block(pkg, BlockReason(BlockKind.STUDY, "This YouTube channel is blocked", "Only your chosen channels work in study mode."), reveal = left)
                    return
                }
            }
        }

        roots.firstNotNullOfOrNull { it.browserHost(pkg) }?.let { host ->
            if (!contentEnabled) return@let
            val site = if (current.settings.siteAllowList && !allowedSite(host, current.sites)) host
                else blockedSiteFor(host, if (current.settings.siteAllowList) emptySet() else current.sites, current.settings.blockAdultSites)
                    ?: return@let
            // Leave the page when the block closes. A back press now would also close the block screen.
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
     * Covers the screen before the block activity draws, then opens that activity.
     * A global back press is not used here. It can dismiss the block and play the video again.
     */
    private fun block(pkg: String, reason: BlockReason, reveal: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!bypassDebounce && now - lastBlockAt < BLOCK_DEBOUNCE_MILLIS) return
        lastBlockAt = now
        blockedThisCheck = true
        if (blockVisible && appInFront() == packageName) return
        blockVisible = true
        showCover()
        startActivity(BlockActivity.intent(this, pkg, reason, reveal))
        handler.postDelayed({ dismissPip() }, 400)
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

    /** Remembers the YouTube page under the player. A Short must not erase a search that is still open. */
    private fun noteYoutubePlace(roots: List<AccessibilityNodeInfo>) {
        val feed = shortsFeeds.first { it.packageName == "com.google.android.youtube" }
        val shorts = roots.any(feed::isShowing)
        if (!shorts) {
            val place = roots.firstNotNullOfOrNull { root -> root.youtubePlace().takeIf { it != YoutubePlace.OTHER } }
            if (place != null) ytPlace = place
        } else if (roots.any { it.youtubeSearchOpen() }) {
            ytPlace = YoutubePlace.SEARCH
        }
        roots.firstNotNullOfOrNull { it.youtubeSearchQuery() }?.let { ytQuery = it }
    }

    private fun landedOnSearch(pkg: String): Boolean =
        appInFront() == pkg && windowRoots(pkg).any { it.youtubeSearchOpen() }

    /** Moves the open app off Shorts, Reels, or a blocked player. The cover stays up during the move. */
    private fun returnToApp(pkg: String) {
        returnInProgress = true
        blockVisible = false
        showCover()
        returnPackage = pkg
        handler.removeCallbacks(returnSettle)
        if (pkg == "com.google.android.youtube" && ytPlace == YoutubePlace.SEARCH) {
            returnByBack = true
            searchRestoreTries = 0
            // The block screen closes 250ms after the broadcast. Back must reach YouTube, not the block.
            handler.postDelayed({
                if (appInFront() == pkg) performGlobalAction(GLOBAL_ACTION_BACK)
            }, 320)
            handler.postDelayed(returnSettle, 1000)
            return
        }
        returnByBack = false
        val roots = windowRoots(pkg)
        val clicked = clickSafeNav(pkg, roots)
        if (!clicked) launchSafeScreen(pkg)
        handler.postDelayed(returnSettle, RETURN_SETTLE_MILLIS)
    }

    private fun launchYoutubeSearch() {
        val query = ytQuery?.takeIf { it.isNotBlank() }
        val uri = if (query == null) "https://www.youtube.com/results?search_query="
            else "https://www.youtube.com/results?search_query=${android.net.Uri.encode(query)}"
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))
            .setPackage("com.google.android.youtube")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        runCatching { startActivity(intent) }
    }

    private fun launchSafeScreen(pkg: String) {
        val uri = when (pkg) {
            "com.google.android.youtube" -> if (rules.settings.blockYoutubeHome) "https://www.youtube.com/feed/subscriptions" else "https://www.youtube.com/"
            "com.instagram.android" -> "https://www.instagram.com/"
            "com.facebook.katana" -> "https://www.facebook.com/"
            else -> null
        }
        val intent = if (uri != null) {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).setPackage(pkg)
        } else {
            packageManager.getLaunchIntentForPackage(pkg)
        } ?: return
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun showCover() {
        if (coverView != null) return
        val view = View(this).apply { setBackgroundColor(0xFF0E1018.toInt()) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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

        private const val TICK_MILLIS = 20_000L
        private const val CONTENT_THROTTLE_MILLIS = 80L
        private const val BLOCK_DEBOUNCE_MILLIS = 300L
        private const val RETURN_SETTLE_MILLIS = 700L

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
