package dev.agneswd.stillpoint.ui.design

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
import kotlin.math.PI
import kotlin.math.sin

/** Animated backgrounds for the focus screen. All are drawn on the device; none are photos. */
enum class FocusTheme(val label: String) {
    LAKE("Still lake"),
    DAWN("Dawn"),
    FOREST("Firefly forest"),
    SPACE("Deep space"),
    RAIN("Rain"),
}

/**
 * Draws a [FocusTheme]. [center] is where the timer sits, as a fraction of the size.
 * The lake ripples spread from there.
 */
@Composable
fun FocusBackdrop(theme: FocusTheme, modifier: Modifier, center: Offset = Offset(0.5f, 0.38f)) {
    val slow = loop(14000, "slow")
    val mid = loop(5000, "mid")
    val fast = loop(1200, "fast")
    // Space stars drift on a long loop of their own, so whole-number speeds stay calm.
    val drift = loop(36000, "drift")
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
    val sunY = size.height * (0.66f - 0.04f * sin(slow * 2 * PI).toFloat())
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFE7B3), Color.Transparent), Offset(size.width / 2, sunY), size.width * 0.6f), size.width * 0.6f, Offset(size.width / 2, sunY))
    drawCircle(Color(0xFFFFE4A8), size.width * 0.16f, Offset(size.width / 2, sunY))
    repeat(4) { i ->
        val y = size.height * (0.18f + i * 0.12f)
        val x = ((slow + hash(i, 7)) % 1f) * (size.width * 1.6f) - size.width * 0.3f
        cloud(Offset(x, y), size.width * (0.18f + hash(i, 8) * 0.1f), Color.White.copy(alpha = 0.22f))
    }
    drawRect(Color(0xFF4A2F5E).copy(alpha = 0.9f), Offset(0f, size.height * 0.78f), Size(size.width, size.height * 0.22f))
    stars(14, mid, 0f, 0.25f, 0.6f)
}

private fun DrawScope.cloud(at: Offset, w: Float, color: Color) {
    drawOval(color, Offset(at.x, at.y), Size(w, w * 0.32f))
    drawOval(color, Offset(at.x + w * 0.2f, at.y - w * 0.14f), Size(w * 0.5f, w * 0.36f))
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
    val planet = Offset(size.width * 0.8f, size.height * 0.78f)
    val r = size.width * 0.16f
    drawCircle(Brush.linearGradient(listOf(Color(0xFFFF9CB0), Color(0xFF8391FF)), planet - Offset(r, r), planet + Offset(r, r)), r, planet)
    drawOval(Color(0xFFFFD39A).copy(alpha = 0.7f), planet - Offset(r * 1.7f, r * 0.25f), Size(r * 3.4f, r * 0.5f), style = Stroke(5f))
}

private fun DrawScope.rain(fast: Float, mid: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A2333), Color(0xFF2B3A52), Color(0xFF3D5070))))
    repeat(90) { i ->
        val x = hash(i, 31) * size.width
        val len = 18f + hash(i, 32) * 22f
        // Whole-number speeds keep the fall continuous when the loop restarts.
        val speed = if (hash(i, 34) > 0.6f) 2f else 1f
        val y = ((hash(i, 33) + fast * speed) % 1f) * (size.height + len) - len
        drawLine(Color(0xFFBFD4F2).copy(alpha = 0.25f + hash(i, 35) * 0.3f), Offset(x, y), Offset(x - 4f, y + len), 1.6f, StrokeCap.Round)
    }
    // Small splashes on the ground.
    repeat(10) { i ->
        val p = (mid * 4 + hash(i, 36)) % 1f
        val at = Offset(hash(i, 37) * size.width, size.height * (0.9f + hash(i, 38) * 0.08f))
        drawOval(Color.White.copy(alpha = (1f - p) * 0.3f), at - Offset(12f * p, 3f * p), Size(24f * p, 6f * p), style = Stroke(1.5f))
    }
}
