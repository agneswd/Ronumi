package dev.agneswd.stillpoint.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.data.FocusPhase
import dev.agneswd.stillpoint.data.UsageDay
import dev.agneswd.stillpoint.data.currentSettings
import dev.agneswd.stillpoint.game.rewardDate
import dev.agneswd.stillpoint.focus.remainingMillis
import dev.agneswd.stillpoint.game.gameState
import dev.agneswd.stillpoint.guard.formatMinutes
import dev.agneswd.stillpoint.ui.design.Mood
import dev.agneswd.stillpoint.focus.Focus
import dev.agneswd.stillpoint.guard.formatDuration
import dev.agneswd.stillpoint.guard.time
import dev.agneswd.stillpoint.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Redraws the home screen widgets. Call it when usage or focus state changes. */
object Widgets {
    fun refresh(context: Context) {
        val app = context.app
        app.scope.launch {
            val today = app.usage.day(LocalDate.now())
            if (app.usage.hasAccess()) app.dao.recordUsage(UsageDay(today.date.toString(), today.perApp.toMap(), today.unlocks))
            val manager = AppWidgetManager.getInstance(context)
            fun ids(type: Class<*>) = manager.getAppWidgetIds(ComponentName(context, type))
            val settings = app.dao.currentSettings()
            val goalIds = ids(GoalWidget::class.java)
            val calendarIds = ids(CalendarWidget::class.java)
            // Session history also determines which Pebble items are unlocked.
            val sessions = app.dao.allSessions()
            val game = gameState(sessions, settings)
            val style = dev.agneswd.stillpoint.game.PebbleStyles.resolve(settings.pebbleItems, game.level.number, settings.petTapCount)

            ids(UsageWidget::class.java).forEach { id ->
                val history = app.dao.allUsageDays().associate { it.day to it.perApp.values.sum() }
                val week = (6 downTo 1).map { history[LocalDate.now().minusDays(it.toLong()).toString()] ?: 0L } + today.totalMillis
                val (w, h) = artSize(context, manager, id, widthShare = 1f, heightShare = 0.4f)
                manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_usage).apply {
                    setTextViewText(R.id.widget_value, formatDuration(today.totalMillis))
                    setTextViewText(R.id.widget_detail, if (today.unlocks == 1) "1 unlock today" else "${today.unlocks} unlocks today")
                    setImageViewBitmap(R.id.widget_art, barsArt(context, week, w, h))
                    setOnClickPendingIntent(R.id.widget_root, MainActivity.pendingHome(context))
                })
            }

            goalIds.forEach { id ->
                val g = game
                val fraction = g.todayMinutes / g.goalMinutes.coerceAtLeast(1).toFloat()
                val mood = when {
                    fraction >= 1f -> Mood.CELEBRATE
                    g.todayMinutes > 0 -> Mood.HAPPY
                    LocalTime.now().hour >= 21 -> Mood.SLEEPY
                    else -> Mood.WAVE
                }
                manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_goal).apply {
                    setImageViewBitmap(R.id.widget_art, goalArt(context, fraction, mood, style = style))
                    setTextViewText(R.id.widget_value, "${formatMinutes(g.todayMinutes)} of ${formatMinutes(g.goalMinutes)}")
                    setTextViewText(R.id.widget_detail, streakText(g.streak, g.streakSafeToday))
                    setOnClickPendingIntent(R.id.widget_root, MainActivity.pendingHome(context))
                })
            }

            calendarIds.forEach { id ->
                val g = game
                val minutes = sessions.groupBy { it.rewardDate() }.mapValues { (_, l) -> (l.sumOf { it.focusedMillis } / 60_000).toInt() }
                val frozen = settings.frozenDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
                val (w, h) = artSize(context, manager, id, widthShare = 1f, heightShare = 0.72f)
                manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_calendar).apply {
                    setImageViewBitmap(R.id.widget_art, calendarArt(context, minutes, settings.focusGoalMinutes, frozen, w, h))
                    setTextViewText(R.id.widget_detail, streakText(g.streak, g.streakSafeToday))
                    setOnClickPendingIntent(R.id.widget_root, MainActivity.pendingHome(context))
                })
            }

            val focusIds = ids(FocusWidget::class.java)
            if (focusIds.isNotEmpty()) {
                val focus = app.dao.activeFocus()
                val views = RemoteViews(context.packageName, R.layout.widget_focus)
                if (focus == null) {
                    views.setImageViewBitmap(R.id.widget_art, pebbleArt(context, Mood.IDLE, style = style))
                    views.setTextViewText(R.id.widget_value, "Focus")
                    views.setTextViewText(R.id.widget_detail, "${formatMinutes(settings.focusMinutes)} session")
                    views.setTextViewText(R.id.widget_action, "START")
                    val start = Intent(context, FocusWidget::class.java).setAction(FocusWidget.ACTION_START)
                    views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getBroadcast(context, 0, start, PendingIntent.FLAG_IMMUTABLE))
                } else {
                    val mood = if (!focus.running) Mood.SLEEPY else if (focus.phase == FocusPhase.FOCUS) Mood.CALM else Mood.HAPPY
                    views.setImageViewBitmap(R.id.widget_art, pebbleArt(context, mood, style = style))
                    views.setTextViewText(R.id.widget_value, if (!focus.running) "Paused" else if (focus.phase == FocusPhase.FOCUS) "Focusing" else "On a break")
                    views.setTextViewText(R.id.widget_detail, if (!focus.running) "Tap to resume" else "Until ${time(System.currentTimeMillis() + focus.remainingMillis())}")
                    views.setTextViewText(R.id.widget_action, "OPEN")
                    views.setOnClickPendingIntent(R.id.widget_root, MainActivity.pendingFocus(context))
                }
                manager.updateAppWidget(focusIds, views)
            }
        }
    }

    private fun streakText(streak: Int, safe: Boolean) = when {
        streak == 0 -> "Start a streak today"
        safe -> "$streak day streak"
        else -> "$streak day streak, keep it today"
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

/** Pebble and today's progress toward the focus goal. */
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
        const val ACTION_START = "dev.agneswd.stillpoint.START_FOCUS"
    }
}
