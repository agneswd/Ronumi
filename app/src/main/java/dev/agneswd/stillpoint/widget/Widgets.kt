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
import dev.agneswd.stillpoint.focus.Focus
import dev.agneswd.stillpoint.guard.formatDuration
import dev.agneswd.stillpoint.guard.time
import dev.agneswd.stillpoint.ui.MainActivity
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Redraws the home screen widgets. Call it when usage or focus state changes. */
object Widgets {
    fun refresh(context: Context) {
        val app = context.app
        app.scope.launch {
            val manager = AppWidgetManager.getInstance(context)
            val usageIds = manager.getAppWidgetIds(ComponentName(context, UsageWidget::class.java))
            if (usageIds.isNotEmpty()) {
                val today = app.usage.day(LocalDate.now())
                val views = RemoteViews(context.packageName, R.layout.widget_usage).apply {
                    setTextViewText(R.id.widget_value, formatDuration(today.totalMillis))
                    setTextViewText(R.id.widget_detail, "${today.unlocks} unlocks today")
                    setOnClickPendingIntent(R.id.widget_root, MainActivity.pendingHome(context))
                }
                manager.updateAppWidget(usageIds, views)
            }

            val focusIds = manager.getAppWidgetIds(ComponentName(context, FocusWidget::class.java))
            if (focusIds.isNotEmpty()) {
                val focus = app.dao.activeFocus()
                val views = RemoteViews(context.packageName, R.layout.widget_focus)
                if (focus == null) {
                    views.setTextViewText(R.id.widget_value, "Focus")
                    views.setTextViewText(R.id.widget_detail, "Tap to start")
                    val start = Intent(context, FocusWidget::class.java).setAction(FocusWidget.ACTION_START)
                    views.setOnClickPendingIntent(
                        R.id.widget_root,
                        PendingIntent.getBroadcast(context, 0, start, PendingIntent.FLAG_IMMUTABLE),
                    )
                } else {
                    views.setTextViewText(R.id.widget_value, if (focus.phase == FocusPhase.FOCUS) "Focusing" else "On a break")
                    views.setTextViewText(R.id.widget_detail, "Until ${time(focus.phaseEndsAt)}")
                    views.setOnClickPendingIntent(R.id.widget_root, MainActivity.pendingFocus(context))
                }
                manager.updateAppWidget(focusIds, views)
            }
        }
    }
}

/** Today's screen time and unlocks. */
class UsageWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context)
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
