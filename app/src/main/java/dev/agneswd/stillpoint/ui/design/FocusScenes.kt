package dev.agneswd.stillpoint.ui.design

import dev.agneswd.stillpoint.R
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import kotlin.math.PI
import kotlin.math.sin

/** Animated backgrounds for the focus screen. All are drawn on the device; none are photos. */
enum class FocusTheme(@param:StringRes val labelRes: Int) {
    LAKE(R.string.focus_scene_lake),
    DAWN(R.string.focus_scene_dawn),
    FOREST(R.string.focus_scene_forest),
    SPACE(R.string.focus_scene_space),
    RAIN(R.string.focus_scene_rain),
}

/**
 * Draws a [FocusTheme]. [center] is where the timer sits, as a fraction of the size.
 * The lake ripples spread from there.
 */
@Composable
fun FocusBackdrop(theme: FocusTheme, modifier: Modifier, center: Offset = Offset(0.5f, 0.38f), animated: Boolean = true) {
    val slow = if (animated) loop(14000, "slow") else 0.35f
    val mid = if (animated) loop(5000, "mid") else 0.35f
    val fast = if (animated) loop(1200, "fast") else 0.35f
    // Space stars drift on a long loop of their own, so whole-number speeds stay calm.
    val drift = if (animated) loop(36000, "drift") else 0.35f
    Canvas(modifier) {
        when (theme) {
            FocusTheme.LAKE -> lake(slow, mid, center)
            FocusTheme.DAWN -> dawn(slow, mid)
            FocusTheme.FOREST -> forest(slow, mid)
            FocusTheme.SPACE -> space(drift, mid)
            FocusTheme.RAIN -> rain(fast, mid)
        }
    }
}

/** A cheap repeatable random number in 0..1 for star and firefly positions. */
private fun hash(i: Int, salt: Int = 0): Float {
    val x = sin((i * 127.1f + salt * 311.7f)) * 43758.547f
    return x - kotlin.math.floor(x)
}

private fun DrawScope.stars(count: Int, twinkle: Float, top: Float = 0f, bottom: Float = 1f, alpha: Float = 1f) {
    repeat(count) { i ->
        val x = hash(i, 1) * size.width
        val y = (top + hash(i, 2) * (bottom - top)) * size.height
        val tw = 0.4f + 0.6f * ((sin((twinkle + hash(i, 3)) * 2 * PI) + 1) / 2).toFloat()
        drawCircle(Color.White.copy(alpha = tw * alpha * (0.5f + hash(i, 4) * 0.5f)), 1f + hash(i, 5) * 2.2f, Offset(x, y))
    }
}

private fun DrawScope.lake(slow: Float, mid: Float, center: Offset) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0E1033), Color(0xFF242C7A), Color(0xFF3D4BB8))))
    stars(60, slow * 3, 0f, 0.55f)
    val c = Offset(center.x * size.width, center.y * size.height)
    // Rings spread out from the timer, like a pebble dropped in still water.
    repeat(5) { i ->
        val p = (mid + i / 5f) % 1f
        val r = size.width * (0.3f + p * 0.9f)
        drawCircle(Color.White.copy(alpha = sin(p * PI).toFloat() * 0.16f), r, c, style = Stroke(2.5f))
    }
    // The far shore.
    val shore = Path().apply {
        moveTo(0f, size.height * 0.8f)
        repeat(9) { i ->
            val x = size.width * (i + 1) / 8f
            quadraticTo(x - size.width / 16f, size.height * (0.77f - hash(i, 9) * 0.04f), x, size.height * 0.8f)
        }
        lineTo(size.width, size.height); lineTo(0f, size.height); close()
    }
    drawPath(shore, Color(0xFF0B0D29).copy(alpha = 0.85f))
}

private fun DrawScope.dawn(slow: Float, mid: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3A2C6E), Color(0xFFB45A8C), Color(0xFFFFA77A), Color(0xFFFFD3A1))))
    val sunY = size.height * (0.66f - 0.015f * sin(slow * 2 * PI).toFloat())
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFE7B3), Color.Transparent), Offset(size.width / 2, sunY), size.width * 0.6f), size.width * 0.6f, Offset(size.width / 2, sunY))
    drawCircle(Color(0xFFFFE4A8), size.width * 0.16f, Offset(size.width / 2, sunY))
    stars(14, mid, 0f, 0.22f, 0.45f)
    // Solid silhouettes drift in place. Their edges never jump at the loop boundary.
    val drift = sin(slow * 2 * PI).toFloat() * size.width * 0.035f
    cloud(Offset(-size.width * 0.08f + drift, size.height * 0.19f), size.width * 0.39f, Color(0xFFE9B6C6))
    cloud(Offset(size.width * 0.72f - drift, size.height * 0.29f), size.width * 0.35f, Color(0xFFF5C3CA))
    cloud(Offset(-size.width * 0.12f - drift, size.height * 0.53f), size.width * 0.32f, Color(0xFFFFD5C1))
    hills(0.76f, Color(0xFFAD6A8B), 3)
    hills(0.83f, Color(0xFF805276), 4)
    hills(0.91f, Color(0xFF4A2F5E), 3)
}

/** One filled outline keeps the cloud lobes solid where they meet. */
private fun DrawScope.cloud(at: Offset, w: Float, color: Color) {
    val outline = Path().apply {
        moveTo(at.x + w * 0.14f, at.y + w * 0.25f)
        cubicTo(at.x - w * 0.03f, at.y + w * 0.25f, at.x - w * 0.04f, at.y + w * 0.04f, at.x + w * 0.16f, at.y + w * 0.03f)
        cubicTo(at.x + w * 0.18f, at.y - w * 0.17f, at.x + w * 0.47f, at.y - w * 0.20f, at.x + w * 0.54f, at.y - w * 0.02f)
        cubicTo(at.x + w * 0.67f, at.y - w * 0.13f, at.x + w * 0.84f, at.y - w * 0.03f, at.x + w * 0.84f, at.y + w * 0.06f)
        cubicTo(at.x + w * 1.05f, at.y + w * 0.04f, at.x + w * 1.09f, at.y + w * 0.25f, at.x + w * 0.90f, at.y + w * 0.25f)
        close()
    }
    drawPath(outline, color)
}

private fun DrawScope.forest(slow: Float, mid: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1F22), Color(0xFF123C35), Color(0xFF1C5A45))))
    stars(30, slow * 2, 0f, 0.35f, 0.7f)
    drawCircle(Color(0xFFEFF7D9).copy(alpha = 0.85f), size.width * 0.07f, Offset(size.width * 0.78f, size.height * 0.16f))
    hills(0.62f, Color(0xFF0F3A30), 3)
    hills(0.72f, Color(0xFF0B2B24), 5)
    hills(0.82f, Color(0xFF071C18), 7)
    repeat(24) { i ->
        val p = (mid + hash(i, 11)) % 1f
        val x = hash(i, 12) * size.width + sin((p + i) * 2 * PI).toFloat() * 20f
        val y = size.height * (0.45f + hash(i, 13) * 0.5f) - p * 40f
        val glow = ((sin((p * 3 + hash(i, 14)) * 2 * PI) + 1) / 2).toFloat()
        // Each firefly fades in and out over its path, so the restart at the bottom is invisible.
        val life = sin(p * PI).toFloat()
        drawCircle(Color(0xFFE4FF8A).copy(alpha = 0.15f * glow * life), 10f, Offset(x, y))
        drawCircle(Color(0xFFF2FFB8).copy(alpha = 0.9f * glow * life), 2.6f, Offset(x, y))
    }
}

private fun DrawScope.hills(level: Float, color: Color, bumps: Int) {
    val path = Path().apply {
        moveTo(0f, size.height * level)
        repeat(bumps) { i ->
            val x0 = size.width * i / bumps
            val x1 = size.width * (i + 1) / bumps
            quadraticTo((x0 + x1) / 2, size.height * (level - 0.06f - hash(i, bumps) * 0.05f), x1, size.height * level)
        }
        lineTo(size.width, size.height); lineTo(0f, size.height); close()
    }
    drawPath(path, color)
}

private fun DrawScope.space(slow: Float, mid: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF05060F), Color(0xFF141238), Color(0xFF2A1B4F))))
    drawCircle(Brush.radialGradient(listOf(Color(0x447C5CFF), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.3f), size.width * 0.7f), size.width * 0.7f, Offset(size.width * 0.2f, size.height * 0.3f))
    // Two star layers drift at different speeds.
    repeat(70) { i ->
        // Whole-number speeds put every star back at its start when the loop restarts.
        val near = i % 3 == 0
        val y = ((hash(i, 21) + slow * if (near) 2f else 1f) % 1f) * size.height
        val tw = 0.5f + 0.5f * sin((mid + hash(i, 23)) * 2 * PI).toFloat()
        drawCircle(Color.White.copy(alpha = 0.3f + 0.7f * tw * if (near) 1f else 0.4f), if (near) 2.2f else 1.2f, Offset(hash(i, 22) * size.width, y))
    }
    // Keep the planet beside the heading, clear of the timer and bottom controls.
    val planet = Offset(size.width * 0.82f, size.height * 0.17f)
    val r = size.width * 0.115f
    val ringAt = planet - Offset(r * 1.65f, r * 0.34f)
    val ringSize = Size(r * 3.3f, r * 0.68f)
    val ringStroke = Stroke((r * 0.12f).coerceAtLeast(2f))
    rotate(-16f, planet) {
        drawOval(Color(0xFFB68CAA), ringAt, ringSize, style = ringStroke)
        drawCircle(Color(0xFFB6A4ED), r, planet)
        val disc = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(planet - Offset(r, r), Size(r * 2, r * 2)))
        }
        clipPath(disc) {
            drawOval(Color(0xFFD8B6ED), planet - Offset(r * 1.3f, r * 0.8f), Size(r * 2.6f, r * 0.65f))
            drawOval(Color(0xFF9589CE), planet - Offset(r * 1.3f, -r * 0.25f), Size(r * 2.6f, r * 0.7f))
        }
        drawArc(Color(0xFFFFD7AF), 0f, 180f, false, ringAt, ringSize, style = ringStroke)
    }
}

private fun DrawScope.rain(fast: Float, mid: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B293C), Color(0xFF354D65), Color(0xFF607E8E))))
    val drift = sin(mid * 2 * PI).toFloat() * size.width * 0.008f
    cloud(Offset(-size.width * 0.12f + drift, size.height * 0.12f), size.width * 0.48f, Color(0xFF35485E))
    cloud(Offset(size.width * 0.71f - drift, size.height * 0.23f), size.width * 0.41f, Color(0xFF405970))
    hills(0.69f, Color(0xFF354F5E), 4)
    hills(0.73f, Color(0xFF293F4D), 3)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF4D7284), Color(0xFF233C50)), startY = size.height * 0.74f),
        Offset(0f, size.height * 0.74f), Size(size.width, size.height * 0.26f))
    // Wide, quiet ripples make the lower scene read as a pond.
    repeat(12) { i ->
        val p = (mid + hash(i, 36)) % 1f
        val at = Offset(hash(i, 37) * size.width, size.height * (0.76f + hash(i, 38) * 0.22f))
        val radius = size.width * (0.018f + p * 0.055f)
        drawOval(Color(0xFFB6D4DC).copy(alpha = sin(p * PI).toFloat() * 0.28f),
            at - Offset(radius, radius * 0.18f), Size(radius * 2, radius * 0.36f), style = Stroke(size.width * 0.002f))
    }
    repeat(64) { i ->
        val near = i % 3 == 0
        val x = hash(i, 31) * size.width
        val len = size.width * if (near) 0.034f else 0.022f
        // Whole-number speeds keep the fall continuous when the loop restarts.
        val speed = if (near) 1f else 2f
        val y = ((hash(i, 33) + fast * speed) % 1f) * (size.height + len) - len
        drawLine(Color(0xFFCEE2EC).copy(alpha = if (near) 0.32f else 0.15f),
            Offset(x, y), Offset(x - len * 0.16f, y + len),
            size.width * if (near) 0.003f else 0.0018f, StrokeCap.Round)
    }
    // Small reeds frame the pond without covering the central controls.
    repeat(2) { side ->
        repeat(4) { i ->
            val x = size.width * if (side == 0) (0.01f + i * 0.018f) else (0.99f - i * 0.018f)
            val y = size.height * (0.92f + hash(i, 42) * 0.045f)
            val lean = size.width * if (side == 0) 0.018f else -0.018f
            val reed = Path().apply {
                moveTo(x, size.height)
                quadraticTo(x + lean, y + size.height * 0.025f, x + lean, y)
            }
            drawPath(reed, Color(0xFF172E3D), style = Stroke(size.width * 0.008f, cap = StrokeCap.Round))
        }
    }
}
