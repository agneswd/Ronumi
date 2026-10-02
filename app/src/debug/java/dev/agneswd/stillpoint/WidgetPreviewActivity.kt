package dev.agneswd.stillpoint

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import dev.agneswd.stillpoint.widget.CalendarWidget
import dev.agneswd.stillpoint.widget.FocusWidget
import dev.agneswd.stillpoint.widget.GoalWidget
import dev.agneswd.stillpoint.widget.UsageWidget
import dev.agneswd.stillpoint.widget.Widgets

/**
 * Debug builds only: hosts every Stillpoint widget like a launcher does, so a screenshot shows them.
 * First run: adb shell appwidget grantbind --package dev.agneswd.stillpoint
 * Then: adb shell am start -S -n dev.agneswd.stillpoint/.WidgetPreviewActivity
 */
class WidgetPreviewActivity : Activity() {
    private lateinit var host: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, HOST_ID).apply { deleteHost() }
        val manager = AppWidgetManager.getInstance(this)
        fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(48), dp(16), dp(16))
            setBackgroundColor(0xFF5B6B8C.toInt())
        }
        // Width and height in dp, about the size of the cells a launcher gives each widget.
        listOf(
            Triple(GoalWidget::class.java, 170, 170),
            Triple(CalendarWidget::class.java, 340, 170),
            Triple(FocusWidget::class.java, 340, 80),
            Triple(UsageWidget::class.java, 170, 170),
        ).forEach { (type, w, h) ->
            val id = host.allocateAppWidgetId()
            check(manager.bindAppWidgetIdIfAllowed(id, ComponentName(this, type))) { "Run: adb shell appwidget grantbind --package $packageName" }
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
            })
            val view = host.createView(this, id, manager.getAppWidgetInfo(id))
            column.addView(view, LinearLayout.LayoutParams(dp(w), dp(h)).apply { bottomMargin = dp(12) })
        }
        setContentView(column, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        Widgets.refresh(this)
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        super.onStop()
        host.stopListening()
    }

    private companion object {
        const val HOST_ID = 4711
    }
}
