package dev.agneswd.ronumi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.widget.RemoteViews
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.FocusPhase
import dev.agneswd.ronumi.data.UsageDay
import dev.agneswd.ronumi.data.currentSettings
import dev.agneswd.ronumi.game.rewardDate
import dev.agneswd.ronumi.focus.remainingMillis
import dev.agneswd.ronumi.game.gameState
import dev.agneswd.ronumi.guard.formatMinutes
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.focus.Focus
import dev.agneswd.ronumi.guard.formatDuration
import dev.agneswd.ronumi.guard.time
import dev.agneswd.ronumi.guard.uses24HourClock
import dev.agneswd.ronumi.ui.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime

/** Redraws the home screen widgets. Call it when usage or focus state changes. */
object Widgets {
    /** Usage and session history are read at most this often, even when widgets are installed. */
    private const val RECORD_INTERVAL_MILLIS = 10 * 60_000L
    private const val ART_CACHE_LIMIT = 8

    private val lock = Mutex()
    private val bitmaps = object : LinkedHashMap<ArtKey, Bitmap>(ART_CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ArtKey, Bitmap>): Boolean {
            val drop = size > ART_CACHE_LIMIT
            if (drop) eldest.value.recycle()
            return drop
        }
    }

    @Volatile
    private var recordedAt = 0L

    @Volatile
    private var forceContent = false

    private var styleReady = false
    private var lastStyle = emptySet<String>()
    private var lastStamp = ""
    private var lastNight = false
    private var lastFocusKey: FocusViewKey? = null
    private var knownIds = emptySet<Int>()
    private val lastSizes = HashMap<Int, Pair<Int, Int>>()

    /** The next refresh reads usage and session history. Plans calls this after stored data changes. */
    fun invalidateContent() {
        forceContent = true
    }

    fun refresh(context: Context) {
        val app = context.app
        app.scope.launch {
            lock.withLock { redraw(context) }
        }
    }

    private suspend fun redraw(context: Context) {
        val app = context.app
        val manager = AppWidgetManager.getInstance(context)
        fun ids(type: Class<*>) = manager.getAppWidgetIds(ComponentName(context, type))
        val usageIds = ids(UsageWidget::class.java)
        val goalIds = ids(GoalWidget::class.java)
        val calendarIds = ids(CalendarWidget::class.java)
        val focusIds = ids(FocusWidget::class.java)
        val allIds = (usageIds.toList() + goalIds.toList() + calendarIds.toList() + focusIds.toList()).toSet()
        val now = android.os.SystemClock.elapsedRealtime()
        val forced = forceContent
        forceContent = false
        if (allIds.isEmpty()) {
            clearArt()
            if (!forced && recordedAt > 0 && now - recordedAt < RECORD_INTERVAL_MILLIS) return
            recordedAt = now
            recordToday(app)
            return
        }
        val focus = app.dao.activeFocus()
        val settings = app.dao.currentSettings()
        val focusKey = focus?.let { FocusViewKey(it.startedAt, it.phase, paused = !it.running) }
        val stamp = listOf(
            settings.pebbleItems.sorted().joinToString(","),
            settings.petTapCount.toString(),
            settings.focusGoalMinutes.toString(),
            settings.frozenDays.sorted().joinToString(","),
        ).joinToString("|")
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val resized = allIds.any { id ->
            val size = optionSize(context, manager, id)
            val previous = lastSizes[id]
            previous != null && previous != size
        }
        val due = forced || recordedAt == 0L || now - recordedAt >= RECORD_INTERVAL_MILLIS ||
            !styleReady || focusKey != lastFocusKey || stamp != lastStamp || night != lastNight ||
            resized || allIds.any { it !in knownIds }
        lastFocusKey = focusKey
        if (!due) {
            if (focusIds.isNotEmpty()) updateFocus(context, manager, focusIds, focus, settings, lastStyle, night)
            return
        }
        recordedAt = now
        lastStamp = stamp
        lastNight = night
        knownIds = allIds
        allIds.forEach { lastSizes[it] = optionSize(context, manager, it) }
        val today = recordToday(app)
        val sessions = app.dao.allSessions()
        val game = gameState(sessions, settings)
        val style = dev.agneswd.ronumi.game.RonumiStyles.resolve(settings.pebbleItems, game.level.number, settings.petTapCount, app.plus.has(dev.agneswd.ronumi.plus.PlusFeature.PLUS_WARDROBE))
        lastStyle = style
        styleReady = true
        val history = if (usageIds.isEmpty()) emptyMap() else app.dao.allUsageDays().associate { it.day to it.perApp.values.sum() }
        usageIds.forEach { id ->
            val week = (6 downTo 1).map { history[LocalDate.now().minusDays(it.toLong()).toString()] ?: 0L } + today.totalMillis
            val (w, h) = artSize(context, manager, id, widthShare = 1f, heightShare = 0.4f)
            manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_usage).apply {
                setTextViewText(R.id.widget_value, formatDuration(context, today.totalMillis))
                setTextViewText(R.id.widget_detail, context.app.resources.getQuantityString(R.plurals.widget_unlock_count, today.unlocks, today.unlocks))
                setImageViewBitmap(R.id.widget_art, artwork(ArtKey("bars", "", emptySet(), 0, w, h, night, week.joinToString(","))) {
                    barsArt(context, week, w, h)
                })
                setOnClickPendingIntent(R.id.widget_root, MainActivity.pendingHome(context))
            })
        }
        goalIds.forEach { id ->
            val fraction = game.todayMinutes / game.goalMinutes.coerceAtLeast(1).toFloat()
            val mood = goalMood(fraction, game.todayMinutes)
            manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_goal).apply {
                setImageViewBitmap(R.id.widget_art, artwork(ArtKey("goal", mood.name, style, (fraction.coerceIn(0f, 1f) * 100).toInt(), 360, 360, night, "")) {
                    goalArt(context, fraction, mood, style = style)
                })
                setTextViewText(R.id.widget_value, context.app.getString(R.string.widget_goal_progress, formatMinutes(context, game.todayMinutes), formatMinutes(context, game.goalMinutes)))
                setTextViewText(R.id.widget_detail, streakText(context, game.streak, game.streakSafeToday))
                setOnClickPendingIntent(R.id.widget_root, MainActivity.pendingHome(context))
            })
        }
        calendarIds.forEach { id ->
            val minutes = sessions.groupBy { it.rewardDate() }.mapValues { (_, listed) -> (listed.sumOf { it.focusedMillis } / 60_000).toInt() }
            val frozen = settings.frozenDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
            val (w, h) = artSize(context, manager, id, widthShare = 1f, heightShare = 0.72f)
            val data = minutes.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" } +
                "|${settings.focusGoalMinutes}|${frozen.sorted().joinToString(",")}"
            manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_calendar).apply {
                setImageViewBitmap(R.id.widget_art, artwork(ArtKey("calendar", "", emptySet(), 0, w, h, night, data)) {
                    calendarArt(context, minutes, settings.focusGoalMinutes, frozen, w, h)
                })
                setTextViewText(R.id.widget_detail, streakText(context, game.streak, game.streakSafeToday))
                setOnClickPendingIntent(R.id.widget_root, MainActivity.pendingHome(context))
            })
        }
        if (focusIds.isNotEmpty()) updateFocus(context, manager, focusIds, focus, settings, style, night)
        lastSizes.keys.retainAll(allIds)
    }

    private suspend fun recordToday(app: dev.agneswd.ronumi.RonumiApp): dev.agneswd.ronumi.usage.DayUsage {
        val today = app.usage.day(LocalDate.now())
        if (app.usage.canQuery()) app.dao.recordUsage(UsageDay(today.date.toString(), today.perApp.toMap(), today.unlocks))
        return today
    }

    private fun updateFocus(
        context: Context,
        manager: AppWidgetManager,
        focusIds: IntArray,
        focus: dev.agneswd.ronumi.data.ActiveFocus?,
        settings: dev.agneswd.ronumi.data.Settings,
        style: Set<String>,
        night: Boolean,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_focus)
        if (focus == null) {
            views.setImageViewBitmap(R.id.widget_art, artwork(ArtKey("ronumi", Mood.IDLE.name, style, 0, 220, 220, night, "")) {
                ronumiArt(context, Mood.IDLE, style = style)
            })
            views.setTextViewText(R.id.widget_value, context.app.getString(R.string.widget_focus_title))
            views.setTextViewText(R.id.widget_detail, context.app.getString(R.string.widget_session_length, formatMinutes(context, settings.focusMinutes)))
            views.setTextViewText(R.id.widget_action, context.app.getString(R.string.widget_start_button))
            val start = Intent(context, FocusWidget::class.java).setAction(FocusWidget.ACTION_START)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getBroadcast(context, 0, start, PendingIntent.FLAG_IMMUTABLE))
        } else {
            val mood = if (!focus.running) Mood.SLEEPY else if (focus.phase == FocusPhase.FOCUS) Mood.CALM else Mood.HAPPY
            views.setImageViewBitmap(R.id.widget_art, artwork(ArtKey("ronumi", mood.name, style, 0, 220, 220, night, "")) {
                ronumiArt(context, mood, style = style)
            })
            views.setTextViewText(R.id.widget_value, if (!focus.running) context.app.getString(R.string.widget_paused_title) else if (focus.phase == FocusPhase.FOCUS) context.app.getString(R.string.widget_running_title) else context.app.getString(R.string.widget_break_title))
            views.setTextViewText(R.id.widget_detail, if (!focus.running) context.app.getString(R.string.widget_resume_hint) else context.app.getString(R.string.widget_end_time, time(System.currentTimeMillis() + focus.remainingMillis(), context.uses24HourClock(settings.clockFormat))))
            views.setTextViewText(R.id.widget_action, context.app.getString(R.string.widget_open_button))
            views.setOnClickPendingIntent(R.id.widget_root, MainActivity.pendingFocus(context))
        }
        manager.updateAppWidget(focusIds, views)
    }

    private fun goalMood(fraction: Float, todayMinutes: Int) = when {
        fraction >= 1f -> Mood.CELEBRATE
        todayMinutes > 0 -> Mood.HAPPY
        LocalTime.now().hour >= 21 -> Mood.SLEEPY
        else -> Mood.WAVE
    }

    private fun artwork(key: ArtKey, create: () -> Bitmap): Bitmap {
        bitmaps[key]?.let { return it }
        val created = create()
        bitmaps[key] = created
        return created
    }

    private fun clearArt() {
        bitmaps.values.forEach { it.recycle() }
        bitmaps.clear()
        lastSizes.clear()
        knownIds = emptySet()
        styleReady = false
    }

    /** Launcher-reported size. A change means the picture must be drawn again at the new size. */
    private fun optionSize(context: Context, manager: AppWidgetManager, id: Int): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(id)
        return options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) to
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
    }

    private data class FocusViewKey(val startedAt: Long, val phase: dev.agneswd.ronumi.data.FocusPhase, val paused: Boolean)

    private data class ArtKey(
        val kind: String,
        val mood: String,
        val style: Set<String>,
        val bucket: Int,
        val width: Int,
        val height: Int,
        val night: Boolean,
        val data: String,
    )

    private fun streakText(context: Context, streak: Int, safe: Boolean) = when {
        streak == 0 -> context.app.getString(R.string.widget_streak_empty)
        safe -> context.app.resources.getQuantityString(R.plurals.widget_streak_safe, streak, streak)
        else -> context.app.resources.getQuantityString(R.plurals.widget_streak_pending, streak, streak)
    }

    /** The picture size in pixels: a share of the widget's current size, from the launcher's size options. */
    private fun artSize(context: Context, manager: AppWidgetManager, id: Int, widthShare: Float, heightShare: Float): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(id)
        val density = context.resources.displayMetrics.density
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0).takeIf { it > 0 } ?: 250
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: 110
        // The layout padding takes about 32 dp on each axis.
        return ((w - 32) * widthShare * density).toInt() to ((h - 32) * heightShare * density).toInt()
    }
}

/** Ronumi and today's progress toward the focus goal. */
class GoalWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) = Widgets.refresh(context)
}

/** The focus days of recent weeks, and the streak. */
class CalendarWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) = Widgets.refresh(context)
}

/** Today's screen time and unlocks. */
class UsageWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) = Widgets.refresh(context)
}

/** Starts a focus session with one tap, or shows the running one. */
class FocusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_START) return
        val pending = goAsync()
        context.app.scope.launch {
            try {
                Focus.start(context, "")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_START = "dev.agneswd.ronumi.START_FOCUS"
    }
}
