package dev.agneswd.ronumi.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.ui.design.drawRonumi
import java.time.DayOfWeek
import java.time.LocalDate

/** The few colors the widget pictures need. They match the widget_* color resources. */
private class ArtColors(dark: Boolean) {
    val track = if (dark) Color(0xFF2E3146) else Color(0xFFEDEEF7)
    val brand = if (dark) Color(0xFF8391FF) else Color(0xFF6C7BFF)
    val mint = if (dark) Color(0xFF3FDDA2) else Color(0xFF34D399)
    val flame = if (dark) Color(0xFFFFA62E) else Color(0xFFFF9F1C)
    val frozen = Color(0xFF7CC8FF)
}

private fun Context.dark() =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/** Draws into a new bitmap with the Compose drawing API, so widgets reuse the app's art code. */
private fun render(width: Int, height: Int, draw: DrawScope.() -> Unit): Bitmap {
    val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(width.toFloat(), height.toFloat()), draw)
    return bitmap
}

/** Ronumi in a square, with no ring. */
fun pebbleArt(context: Context, mood: Mood, sizePx: Int = 220, style: Set<String> = emptySet()): Bitmap = render(sizePx, sizePx) {
    val w = size.width * 0.86f
    inset((size.width - w) / 2, size.height - w * 1.1f, (size.width - w) / 2, 0f) { drawRonumi(mood, style = style) }
}

/** The goal ring: progress toward today's goal, with Ronumi inside. */
fun goalArt(context: Context, fraction: Float, mood: Mood, sizePx: Int = 360, style: Set<String> = emptySet()): Bitmap {
    val c = ArtColors(context.dark())
    return render(sizePx, sizePx) {
        val stroke = size.width * 0.085f
        val box = Size(size.width - stroke, size.height - stroke)
        val corner = Offset(stroke / 2, stroke / 2)
        drawArc(c.track, 0f, 360f, false, corner, box, style = Stroke(stroke))
        val sweep = 360f * fraction.coerceIn(0f, 1f)
        if (sweep > 0f) drawArc(if (fraction >= 1f) c.mint else c.brand, -90f, sweep, false, corner, box, style = Stroke(stroke, cap = StrokeCap.Round))
        val w = size.width * 0.5f
        val top = (size.height - w * 1.1f) / 2 + size.height * 0.02f
        inset((size.width - w) / 2, top, (size.width - w) / 2, size.height - top - w * 1.1f) { drawRonumi(mood, style = style) }
    }
}

/** Seven rounded bars, oldest first. The last bar is today and gets the brand color. */
fun barsArt(context: Context, values: List<Long>, widthPx: Int, heightPx: Int): Bitmap {
    val c = ArtColors(context.dark())
    return render(widthPx, heightPx) {
        if (values.isEmpty()) return@render
        val max = values.max().coerceAtLeast(1)
        val gap = size.width * 0.04f
        val barW = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val x = i * (barW + gap)
            val radius = CornerRadius(barW * 0.3f)
            drawRoundRect(c.track, Offset(x, 0f), Size(barW, size.height), radius)
            val h = (size.height * v / max).coerceAtLeast(if (v > 0) barW * 0.6f else 0f)
            if (h > 0f) drawRoundRect(if (i == values.lastIndex) c.brand else c.brand.copy(alpha = 0.45f), Offset(x, size.height - h), Size(barW, h), radius)
        }
    }
}

/**
 * A focus calendar: one column per week, Monday at the top, today in the last column.
 * Days that met the goal are full brand color; partial days are lighter; frozen days are blue.
 */
fun calendarArt(
    context: Context,
    minutes: Map<LocalDate, Int>,
    goal: Int,
    frozen: Set<LocalDate>,
    widthPx: Int,
    heightPx: Int,
    today: LocalDate = LocalDate.now(),
): Bitmap {
    val c = ArtColors(context.dark())
    return render(widthPx, heightPx) {
        val gap = size.height * 0.035f
        val cell = (size.height - gap * 6) / 7
        val weeks = ((size.width + gap) / (cell + gap)).toInt().coerceAtLeast(1)
        val startX = size.width - weeks * (cell + gap) + gap
        val lastMonday = today.with(DayOfWeek.MONDAY)
        for (col in 0 until weeks) {
            val monday = lastMonday.minusWeeks((weeks - 1 - col).toLong())
            for (row in 0 until 7) {
                val date = monday.plusDays(row.toLong())
                if (date.isAfter(today)) continue
                val m = minutes[date] ?: 0
                val color = when {
                    m >= goal -> c.brand
                    date in frozen -> c.frozen
                    m > 0 -> c.brand.copy(alpha = 0.25f + 0.5f * m / goal.coerceAtLeast(1))
                    else -> c.track
                }
                val at = Offset(startX + col * (cell + gap), row * (cell + gap))
                drawRoundRect(color, at, Size(cell, cell), CornerRadius(cell * 0.28f))
                if (date == today) {
                    val ring = cell * 0.12f
                    drawRoundRect(c.flame, at + Offset(ring / 2, ring / 2), Size(cell - ring, cell - ring), CornerRadius(cell * 0.24f), style = Stroke(ring))
                }
            }
        }
    }
}
