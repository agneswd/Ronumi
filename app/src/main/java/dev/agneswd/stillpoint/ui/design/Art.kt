package dev.agneswd.stillpoint.ui.design

import dev.agneswd.stillpoint.R
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The streak flame. It flickers when [lit], and turns grey when the streak is not safe today. */
@Composable
fun Flame(modifier: Modifier = Modifier, size: Dp = 28.dp, lit: Boolean = true) {
    val c = Sp.colors
    val flicker by rememberInfiniteTransition(label = "flame").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "flicker",
    )
    val outer = if (lit) c.flame else c.border
    val inner = if (lit) c.gold else c.surfaceHigh
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val f = if (lit) flicker else 0f
        scale(1f + f * 0.03f, 1f - f * 0.04f, pivot = Offset(w / 2, h)) {
            drawPath(flamePath(w, h, 1f, f * 0.04f), outer)
            drawPath(flamePath(w, h, 0.5f, -f * 0.06f), inner)
        }
    }
}

/** A flame with a tip and a small lick on the left. [k] scales it down toward the bottom center. */
private fun flamePath(w: Float, h: Float, k: Float, lean: Float) = Path().apply {
    fun p(x: Float, y: Float) = Offset(w * (0.5f + (x - 0.5f) * k), h * (1f - (1f - y) * k))
    val tip = p(0.5f + lean, 0f)
    moveTo(tip.x, tip.y)
    p(0.64f, 0.2f).let { a -> p(0.9f, 0.4f).let { b -> p(0.88f, 0.66f).let { c -> cubicTo(a.x, a.y, b.x, b.y, c.x, c.y) } } }
    p(0.86f, 0.88f).let { a -> p(0.7f, 1f).let { b -> p(0.5f, 1f).let { c -> cubicTo(a.x, a.y, b.x, b.y, c.x, c.y) } } }
    p(0.28f, 1f).let { a -> p(0.12f, 0.88f).let { b -> p(0.12f, 0.64f).let { c -> cubicTo(a.x, a.y, b.x, b.y, c.x, c.y) } } }
    p(0.12f, 0.5f).let { a -> p(0.2f, 0.4f).let { b -> p(0.28f, 0.32f).let { c -> cubicTo(a.x, a.y, b.x, b.y, c.x, c.y) } } }
    p(0.29f, 0.44f).let { a -> p(0.35f, 0.52f).let { b -> p(0.42f, 0.54f).let { c -> cubicTo(a.x, a.y, b.x, b.y, c.x, c.y) } } }
    p(0.38f, 0.34f).let { a -> p(0.44f, 0.16f).let { b -> cubicTo(a.x, a.y, b.x, b.y, tip.x, tip.y) } }
    close()
}

/** A lightning bolt for XP. */
@Composable
fun XpBolt(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val c = Sp.colors
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bolt = Path().apply {
            moveTo(w * 0.58f, 0f); lineTo(w * 0.12f, h * 0.58f); lineTo(w * 0.46f, h * 0.58f)
            lineTo(w * 0.38f, h); lineTo(w * 0.88f, h * 0.38f); lineTo(w * 0.53f, h * 0.38f); close()
        }
        drawPath(bolt, c.gold)
        drawPath(bolt, c.goldLip, style = Stroke(w * 0.06f))
    }
}

enum class DayPart(@param:StringRes val labelRes: Int, val start: Int, val end: Int, val scheduleId: String, @param:StringRes val scheduleNameRes: Int) {
    MORNING(R.string.day_part_morning, 6 * 60, 8 * 60, "Morning focus", R.string.schedule_default_morning),
    AFTERNOON(R.string.day_part_afternoon, 14 * 60, 16 * 60, "Afternoon focus", R.string.schedule_default_afternoon),
    EVENING(R.string.day_part_evening, 18 * 60, 20 * 60, "Evening focus", R.string.schedule_default_evening),
    NIGHT(R.string.day_part_night, 20 * 60, 22 * 60, "Night focus", R.string.schedule_default_night),
}

/** A small round picture for a part of the day: sunrise, sun, sunset or moon. */
@Composable
fun DayPartIcon(part: DayPart, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val sun = Color(0xFFFFC53D)
    val sunLip = Color(0xFFFF9F1C)
    Canvas(modifier.size(size)) {
        val s = this.size.width
        val sky = when (part) {
            DayPart.MORNING -> listOf(Color(0xFFFFD9C2), Color(0xFFFFB3C6))
            DayPart.AFTERNOON -> listOf(Color(0xFFBEE3FF), Color(0xFF8CC8FF))
            DayPart.EVENING -> listOf(Color(0xFFFFB38A), Color(0xFFB57BFF))
            DayPart.NIGHT -> listOf(Color(0xFF3B3F8F), Color(0xFF1B1D45))
        }
        drawRoundRect(Brush.verticalGradient(sky), cornerRadius = CornerRadius(s * 0.3f))
        when (part) {
            DayPart.MORNING, DayPart.EVENING -> {
                drawCircle(sun, s * 0.2f, Offset(s / 2, s * 0.66f))
                drawRect(sky.last(), Offset(0f, s * 0.68f), Size(s, s * 0.32f))
                drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(s * 0.18f, s * 0.72f), Size(s * 0.64f, s * 0.06f), CornerRadius(s * 0.03f))
            }
            DayPart.AFTERNOON -> {
                repeat(8) { i ->
                    val a = i * PI / 4
                    val c0 = Offset(s / 2 + cos(a).toFloat() * s * 0.26f, s / 2 + sin(a).toFloat() * s * 0.26f)
                    val c1 = Offset(s / 2 + cos(a).toFloat() * s * 0.34f, s / 2 + sin(a).toFloat() * s * 0.34f)
                    drawLine(sunLip, c0, c1, s * 0.05f, StrokeCap.Round)
                }
                drawCircle(sun, s * 0.19f, Offset(s / 2, s / 2))
            }
            DayPart.NIGHT -> {
                drawCircle(Color(0xFFFFF1C2), s * 0.2f, Offset(s * 0.5f, s * 0.5f))
                drawCircle(sky.first(), s * 0.17f, Offset(s * 0.6f, s * 0.42f))
                sparkle(Offset(s * 0.25f, s * 0.28f), s * 0.06f, Color.White)
                sparkle(Offset(s * 0.78f, s * 0.72f), s * 0.045f, Color.White)
            }
        }
    }
}

/** Onboarding scene: a phone with a short video feed and a stop badge. Pebble guards it. */
@Composable
fun ShortsScene(modifier: Modifier = Modifier) {
    val c = Sp.colors
    // One loop swipes through all three colors, so the end matches the start.
    val t = loop(3 * 1600, "shorts")
    Box(modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(170.dp, 300.dp).offset(x = (-40).dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(c.text, cornerRadius = CornerRadius(w * 0.16f))
            drawRoundRect(c.surfaceHigh, Offset(w * 0.06f, w * 0.06f), Size(w * 0.88f, h - w * 0.12f), CornerRadius(w * 0.12f))
            val screen = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.06f, w * 0.06f, w * 0.94f, h - w * 0.06f, CornerRadius(w * 0.12f)))
            }
            // Video cards swipe up one at a time, like a short-video feed: hold, then swipe.
            val cardH = h * 0.42f
            val pitch = cardH + w * 0.05f
            val step = (t * 3).toInt().coerceAtMost(2)
            val local = t * 3 - step
            val swipe = FastOutSlowInEasing.transform(((local - 0.45f) / 0.55f).coerceIn(0f, 1f))
            val shift = (step + swipe) * pitch
            val colors = listOf(c.rose, c.brand, c.flame)
            clipPath(screen) {
                for (i in 0 until 6) {
                    val y = w * 0.1f + i * pitch - shift
                    if (y > h || y + cardH < 0f) continue
                    val color = colors[i % 3]
                    drawRoundRect(
                        Brush.verticalGradient(listOf(color.copy(alpha = 0.55f), color), startY = y, endY = y + cardH),
                        Offset(w * 0.1f, y), Size(w * 0.8f, cardH), CornerRadius(w * 0.08f),
                    )
                    val play = Path().apply {
                        moveTo(w * 0.44f, y + cardH * 0.4f); lineTo(w * 0.6f, y + cardH * 0.5f); lineTo(w * 0.44f, y + cardH * 0.6f); close()
                    }
                    drawPath(play, Color.White.copy(alpha = 0.9f))
                }
            }
            // The stop badge.
            val badge = Offset(w * 0.86f, h * 0.18f)
            drawCircle(c.dangerLip, w * 0.2f, badge + Offset(0f, w * 0.025f))
            drawCircle(c.danger, w * 0.2f, badge)
            drawLine(Color.White, badge + Offset(-w * 0.09f, 0f), badge + Offset(w * 0.09f, 0f), w * 0.06f, StrokeCap.Round)
        }
        Pebble(Mood.GUARD, Modifier.offset(x = 80.dp, y = 70.dp), size = 120.dp)
    }
}

/** Onboarding scene: notifications drop into a closed box while Pebble rests. */
@Composable
fun NotificationScene(modifier: Modifier = Modifier) {
    val c = Sp.colors
    val t = loop(2800, "notes")
    Box(modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(300.dp, 300.dp)) {
            val w = size.width
            // Three bubbles fall into the box one after another.
            repeat(3) { i ->
                val p = (t + i / 3f) % 1f
                val y = w * 0.05f + p * w * 0.5f
                val x = w * (0.22f + i * 0.2f)
                // Fade in at the top and out into the box, so the restart never pops.
                val alpha = when {
                    p < 0.12f -> p / 0.12f
                    p > 0.85f -> (1f - p) / 0.15f
                    else -> 1f
                }
                drawRoundRect(c.surfaceHigh.copy(alpha = alpha), Offset(x - w * 0.12f, y), Size(w * 0.3f, w * 0.1f), CornerRadius(w * 0.04f))
                drawCircle(listOf(c.rose, c.brand, c.mint)[i].copy(alpha = alpha), w * 0.025f, Offset(x - w * 0.06f, y + w * 0.05f))
                drawRoundRect(c.textDim.copy(alpha = alpha * 0.6f), Offset(x - w * 0.02f, y + w * 0.035f), Size(w * 0.12f, w * 0.03f), CornerRadius(w * 0.015f))
            }
            // The box.
            drawRoundRect(c.brandLip, Offset(w * 0.12f, w * 0.62f), Size(w * 0.76f, w * 0.3f), CornerRadius(w * 0.06f))
            drawRoundRect(c.brand, Offset(w * 0.12f, w * 0.6f), Size(w * 0.76f, w * 0.28f), CornerRadius(w * 0.06f))
            drawRoundRect(c.brandLip, Offset(w * 0.08f, w * 0.56f), Size(w * 0.84f, w * 0.08f), CornerRadius(w * 0.04f))
        }
        Pebble(Mood.CALM, Modifier.offset(x = 0.dp, y = 40.dp), size = 96.dp)
    }
}

/** Onboarding scene: a padlock with a timer ring. Pebble is proud of the strict session. */
@Composable
fun StrictScene(modifier: Modifier = Modifier) {
    val c = Sp.colors
    val t = loop(6000, "strict")
    Box(modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp, 260.dp).offset(x = (-30).dp)) {
            val w = size.width
            val h = size.height
            // Shackle.
            drawArc(c.textDim, 180f, 180f, false, Offset(w * 0.24f, h * 0.04f), Size(w * 0.52f, w * 0.52f), style = Stroke(w * 0.1f, cap = StrokeCap.Round))
            drawLine(c.textDim, Offset(w * 0.24f, h * 0.04f + w * 0.26f), Offset(w * 0.24f, h * 0.36f), w * 0.1f)
            drawLine(c.textDim, Offset(w * 0.76f, h * 0.04f + w * 0.26f), Offset(w * 0.76f, h * 0.36f), w * 0.1f)
            // Body with a lip.
            drawRoundRect(c.flameLip, Offset(w * 0.08f, h * 0.36f), Size(w * 0.84f, h * 0.6f), CornerRadius(w * 0.14f))
            drawRoundRect(c.flame, Offset(w * 0.08f, h * 0.33f), Size(w * 0.84f, h * 0.6f), CornerRadius(w * 0.14f))
            val center = Offset(w / 2, h * 0.63f)
            drawCircle(Color.White, w * 0.24f, center)
            drawArc(c.surfaceHigh, 0f, 360f, false, center - Offset(w * 0.19f, w * 0.19f), Size(w * 0.38f, w * 0.38f), style = Stroke(w * 0.05f))
            // The timer runs down, then refills quickly, so the loop has no jump.
            val left = if (t < 0.85f) 1f - t / 0.85f else FastOutSlowInEasing.transform((t - 0.85f) / 0.15f)
            drawArc(c.brand, -90f, 360f * left, false, center - Offset(w * 0.19f, w * 0.19f), Size(w * 0.38f, w * 0.38f), style = Stroke(w * 0.05f, cap = StrokeCap.Round))
            drawCircle(c.text, w * 0.03f, center)
        }
        Pebble(Mood.STRICT, Modifier.offset(x = 95.dp, y = 80.dp), size = 100.dp)
    }
}

/** Onboarding scene: a big flame with a day count. Pebble celebrates. */
@Composable
fun StreakScene(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        Flame(Modifier.offset(x = (-50).dp, y = (-20).dp), size = 190.dp)
        Pebble(Mood.CELEBRATE, Modifier.offset(x = 85.dp, y = 70.dp), size = 110.dp)
    }
}

/** Soft floating dots in the brand colors, behind welcome and reward screens. */
@Composable
fun FloatingDots(modifier: Modifier = Modifier) {
    val c = Sp.colors
    val t = loop(9000, "dots")
    val colors = listOf(c.brand, c.rose, c.mint, c.gold, c.flame)
    Canvas(modifier) {
        repeat(14) { i ->
            val seed = i * 37.17f
            val x = (seed * 13 % 100) / 100f * size.width
            val baseY = (seed * 7 % 100) / 100f * size.height
            val y = baseY + sin((t + i / 14f) * 2 * PI).toFloat() * 18.dp.toPx()
            val r = (6 + (i * 5) % 12).dp.toPx()
            drawCircle(colors[i % colors.size].copy(alpha = 0.18f), r, Offset(x, y))
        }
    }
}

/** A round badge medal for achievements. Locked badges are grey. */
@Composable
fun Medal(color: Color, lip: Color, locked: Boolean, modifier: Modifier = Modifier, size: Dp = 64.dp, content: DrawScope.(Float) -> Unit = {}) {
    val c = Sp.colors
    Canvas(modifier.size(size).graphicsLayer { alpha = if (locked) 0.55f else 1f }) {
        val s = this.size.width
        val fill = if (locked) c.surfaceHigh else color
        val edge = if (locked) c.border else lip
        val hex = Path().apply {
            repeat(6) { i ->
                val a = PI / 3 * i - PI / 2
                val p = Offset(s / 2 + cos(a).toFloat() * s * 0.46f, s / 2 + sin(a).toFloat() * s * 0.46f)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(hex, edge)
        rotate(0f) { scale(0.86f, Offset(s / 2, s / 2 - s * 0.03f)) { drawPath(hex, fill) } }
        content(s)
    }
}
