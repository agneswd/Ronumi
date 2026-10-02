package dev.agneswd.stillpoint.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** What Pebble feels. Each mood changes the face, the arms and the motion. */
enum class Mood { IDLE, HAPPY, CELEBRATE, CALM, SLEEPY, SAD, GUARD, WAVE, THINK, PROUD }

private val Ink = Color(0xFF262841)
private val BodyTop = Color(0xFFA3AEFF)
private val BodyBottom = Color(0xFF6C7BFF)
private val BodyShade = Color(0xFF5867E6)
private val Belly = Color(0xFFD3D8FF)
private val Cheek = Color(0xFFFF8FAB)
private val Leaf = Color(0xFF34D399)
private val LeafDark = Color(0xFF1FA874)
private val Tongue = Color(0xFFFF7C9C)
private val Spark = Color(0xFFFFC53D)

/**
 * Pebble, the Stillpoint mascot: a round stone with a sprout on top.
 * It is drawn in code, so it scales to any size and animates without image files.
 * [look] moves the pupils, from -1 to 1 on each axis.
 */
@Composable
fun Pebble(mood: Mood, modifier: Modifier = Modifier, size: Dp = 160.dp, look: Offset = Offset.Zero) {
    val time = rememberInfiniteTransition(label = "pebble")
    val breath by time.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "breath")
    val blink by time.animateFloat(
        1f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 3600
                1f at 0
                1f at 3300
                0.08f at 3400
                1f at 3550
            },
        ),
        label = "blink",
    )
    val hop by time.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "hop")
    val wave by time.animateFloat(-1f, 1f, infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wave")
    val drift by time.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "drift")

    Canvas(modifier.size(size, size * 1.1f)) { drawPebble(mood, breath, blink, hop, wave, drift, look) }
}

/**
 * Draws one frame of Pebble that fills the width of the draw area. The height is 1.1 times the width.
 * The animation phases go from 0 to 1, except [wave] (-1 to 1) and [blink] (1 open, near 0 closed).
 * Widgets use it with the defaults to draw a still Pebble into a bitmap.
 */
fun DrawScope.drawPebble(
    mood: Mood,
    breath: Float = 0f,
    blink: Float = 1f,
    hop: Float = 0.5f,
    wave: Float = 0f,
    drift: Float = 0.3f,
    look: Offset = Offset.Zero,
) {
    val u = size.width / 100f
    val b = sin(breath * 2 * PI).toFloat()
    val jump = when (mood) {
        Mood.CELEBRATE -> abs(sin(hop * PI)).toFloat() * 14f
        Mood.HAPPY, Mood.PROUD -> abs(sin(hop * PI)).toFloat() * 3f
        Mood.CALM -> 3f + sin(breath * 2 * PI).toFloat() * 3f
        else -> 1f + b
    }
    // Squash a little near the ground when hopping.
    val squash = if (mood == Mood.CELEBRATE && jump < 3f) 0.06f else 0f

    // Ground shadow, smaller when Pebble is in the air.
    val shadowScale = 1f - jump / 30f
    drawOval(
        Color.Black.copy(alpha = 0.12f),
        topLeft = Offset((50f - 30f * shadowScale) * u, 102f * u),
        size = Size(60f * shadowScale * u, 7f * u),
    )

    translate(top = -jump * u) {
        scale(1f - b * 0.008f + squash, 1f + b * 0.015f - squash, pivot = Offset(50f * u, 100f * u)) {
            val raised = mood in setOf(Mood.CELEBRATE, Mood.WAVE, Mood.GUARD, Mood.THINK)
            if (!raised) drawArms(mood, u, wave)
            drawFeet(u)
            drawBody(u)
            if (raised) drawArms(mood, u, wave)
            drawSprout(mood, u, b, wave)
            drawFace(mood, u, blink, look)
        }
    }
    drawExtras(mood, u, drift, hop)
}

private fun bodyPath(u: Float) = Path().apply {
    moveTo(50f * u, 14f * u)
    cubicTo(77f * u, 14f * u, 93f * u, 42f * u, 93f * u, 66f * u)
    cubicTo(93f * u, 90f * u, 74f * u, 100f * u, 50f * u, 100f * u)
    cubicTo(26f * u, 100f * u, 7f * u, 90f * u, 7f * u, 66f * u)
    cubicTo(7f * u, 42f * u, 23f * u, 14f * u, 50f * u, 14f * u)
    close()
}

private fun DrawScope.drawBody(u: Float) {
    val path = bodyPath(u)
    drawPath(path, Brush.verticalGradient(listOf(BodyTop, BodyBottom), startY = 14f * u, endY = 100f * u))
    clipPath(path) {
        // Shade on the lower right gives the stone some volume.
        drawOval(BodyShade.copy(alpha = 0.55f), topLeft = Offset(48f * u, 70f * u), size = Size(70f * u, 50f * u))
        drawOval(Belly, topLeft = Offset(29f * u, 66f * u), size = Size(42f * u, 30f * u))
        // Shine on the upper left.
        drawOval(Color.White.copy(alpha = 0.35f), topLeft = Offset(20f * u, 24f * u), size = Size(16f * u, 9f * u))
    }
}

private fun DrawScope.drawFeet(u: Float) {
    drawOval(BodyShade, topLeft = Offset(28f * u, 95f * u), size = Size(18f * u, 9f * u))
    drawOval(BodyShade, topLeft = Offset(54f * u, 95f * u), size = Size(18f * u, 9f * u))
}

private fun DrawScope.drawArm(u: Float, pivot: Offset, angle: Float, left: Boolean) {
    rotate(angle, pivot) {
        val w = 12f * u
        val h = 22f * u
        drawOval(BodyBottom, topLeft = Offset(pivot.x - w / 2, pivot.y), size = Size(w, h))
        drawOval(BodyTop.copy(alpha = 0.6f), topLeft = Offset(pivot.x - w / 2 + (if (left) 2f else 4f) * u, pivot.y + 3f * u), size = Size(5f * u, 8f * u))
    }
}

private fun DrawScope.drawArms(mood: Mood, u: Float, wave: Float) {
    val leftPivot = Offset(12f * u, 62f * u)
    val rightPivot = Offset(88f * u, 62f * u)
    val (left, right) = when (mood) {
        Mood.CELEBRATE -> 150f + wave * 10f to -150f - wave * 10f
        Mood.WAVE -> 25f to -150f + wave * 25f
        Mood.GUARD -> 25f to -160f
        Mood.THINK -> 25f to -120f
        Mood.PROUD -> -40f to 40f
        Mood.CALM -> 60f to -60f
        Mood.SAD -> 8f to -8f
        else -> 25f to -25f
    }
    drawArm(u, leftPivot, left, left = true)
    drawArm(u, rightPivot, right, left = false)
    if (mood == Mood.GUARD) {
        // An open palm that says "stop".
        val hand = Offset(rightPivot.x + 8f * u, rightPivot.y - 22f * u)
        drawCircle(BodyBottom, 7.5f * u, hand)
        drawCircle(BodyTop.copy(alpha = 0.6f), 3f * u, hand)
    }
}

private fun DrawScope.drawSprout(mood: Mood, u: Float, b: Float, wave: Float) {
    val sway = when (mood) {
        Mood.CELEBRATE -> wave * 18f
        Mood.SAD, Mood.SLEEPY -> 35f
        else -> b * 8f
    }
    val base = Offset(50f * u, 15f * u)
    rotate(sway, base) {
        val stem = Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(base.x - 1f * u, base.y - 6f * u, base.x + 1f * u, base.y - 11f * u)
        }
        drawPath(stem, LeafDark, style = Stroke(2.6f * u, cap = StrokeCap.Round))
        val top = Offset(base.x + 1f * u, base.y - 11f * u)
        leaf(top, -1f, u)
        leaf(top, 1f, u)
    }
}

private fun DrawScope.leaf(at: Offset, side: Float, u: Float) {
    val tip = Offset(at.x + side * 13f * u, at.y - 7f * u)
    val path = Path().apply {
        moveTo(at.x, at.y)
        quadraticTo(at.x + side * 4f * u, at.y - 10f * u, tip.x, tip.y)
        quadraticTo(at.x + side * 9f * u, at.y + 2f * u, at.x, at.y)
        close()
    }
    drawPath(path, Leaf)
    drawLine(LeafDark, at, Offset(at.x + side * 9f * u, at.y - 4.5f * u), 1.2f * u, StrokeCap.Round)
}

private fun DrawScope.drawFace(mood: Mood, u: Float, blink: Float, look: Offset) {
    val left = Offset(36f * u, 50f * u)
    val right = Offset(64f * u, 50f * u)
    val stroke = Stroke(3f * u, cap = StrokeCap.Round)

    when (mood) {
        Mood.HAPPY, Mood.CELEBRATE, Mood.PROUD -> {
            // Smiling eyes: upside-down arcs.
            listOf(left, right).forEach { e ->
                drawArc(Ink, 200f, 140f, false, Offset(e.x - 9f * u, e.y - 6f * u), Size(18f * u, 16f * u), style = stroke)
            }
        }
        Mood.CALM -> {
            listOf(left, right).forEach { e ->
                drawArc(Ink, 20f, 140f, false, Offset(e.x - 8f * u, e.y - 8f * u), Size(16f * u, 12f * u), style = stroke)
            }
        }
        Mood.SLEEPY -> {
            listOf(left, right).forEach { e ->
                drawOval(Color.White, Offset(e.x - 10f * u, e.y - 1f * u), Size(20f * u, 9f * u))
                drawCircle(Ink, 4.5f * u, Offset(e.x, e.y + 4f * u))
                drawLine(BodyBottom, Offset(e.x - 11f * u, e.y), Offset(e.x + 11f * u, e.y), 3.2f * u, StrokeCap.Round)
            }
        }
        else -> {
            val lookAt = when (mood) {
                Mood.THINK -> Offset(0.6f, -0.8f)
                Mood.SAD -> Offset(0f, 0.6f)
                else -> look
            }
            listOf(left, right).forEach { e ->
                val h = 26f * u * blink
                drawOval(Color.White, Offset(e.x - 10.5f * u, e.y - h / 2), Size(21f * u, h))
                if (blink > 0.3f) {
                    val p = Offset(e.x + lookAt.x * 3.5f * u, e.y + lookAt.y * 4.5f * u)
                    drawCircle(Ink, 6.8f * u, p)
                    drawCircle(Color.White, 2.3f * u, Offset(p.x - 2.2f * u, p.y - 2.6f * u))
                    drawCircle(Color.White.copy(alpha = 0.8f), 1.1f * u, Offset(p.x + 2.4f * u, p.y + 2.2f * u))
                }
            }
        }
    }

    // Brows show determination or worry.
    when (mood) {
        Mood.GUARD -> {
            drawLine(Ink, Offset(26f * u, 34f * u), Offset(43f * u, 38f * u), 3.2f * u, StrokeCap.Round)
            drawLine(Ink, Offset(74f * u, 34f * u), Offset(57f * u, 38f * u), 3.2f * u, StrokeCap.Round)
        }
        Mood.SAD -> {
            drawLine(Ink, Offset(27f * u, 37f * u), Offset(42f * u, 33f * u), 3f * u, StrokeCap.Round)
            drawLine(Ink, Offset(73f * u, 37f * u), Offset(58f * u, 33f * u), 3f * u, StrokeCap.Round)
        }
        else -> Unit
    }

    drawOval(Cheek.copy(alpha = 0.7f), Offset(20f * u, 60f * u), Size(13f * u, 7f * u))
    drawOval(Cheek.copy(alpha = 0.7f), Offset(67f * u, 60f * u), Size(13f * u, 7f * u))

    val mouth = Offset(50f * u, 66f * u)
    when (mood) {
        Mood.HAPPY, Mood.CELEBRATE, Mood.WAVE, Mood.PROUD -> {
            val open = Path().apply {
                moveTo(mouth.x - 8f * u, mouth.y - 2f * u)
                quadraticTo(mouth.x, mouth.y - 0.5f * u, mouth.x + 8f * u, mouth.y - 2f * u)
                quadraticTo(mouth.x + 7f * u, mouth.y + 10f * u, mouth.x, mouth.y + 10f * u)
                quadraticTo(mouth.x - 7f * u, mouth.y + 10f * u, mouth.x - 8f * u, mouth.y - 2f * u)
                close()
            }
            drawPath(open, Ink)
            clipPath(open) { drawOval(Tongue, Offset(mouth.x - 6f * u, mouth.y + 4f * u), Size(12f * u, 8f * u)) }
        }
        Mood.SAD -> drawArc(Ink, 200f, 140f, false, Offset(mouth.x - 6f * u, mouth.y + 1f * u), Size(12f * u, 9f * u), style = stroke)
        Mood.GUARD -> drawLine(Ink, Offset(mouth.x - 6f * u, mouth.y + 2f * u), Offset(mouth.x + 6f * u, mouth.y + 2f * u), 3f * u, StrokeCap.Round)
        Mood.THINK -> drawCircle(Ink, 3f * u, Offset(mouth.x + 3f * u, mouth.y + 2f * u))
        Mood.SLEEPY -> drawOval(Ink, Offset(mouth.x - 3f * u, mouth.y), Size(6f * u, 7f * u))
        else -> drawArc(Ink, 20f, 140f, false, Offset(mouth.x - 7f * u, mouth.y - 5f * u), Size(14f * u, 10f * u), style = stroke)
    }
}

private fun DrawScope.drawExtras(mood: Mood, u: Float, drift: Float, hop: Float) {
    when (mood) {
        Mood.SLEEPY -> repeat(3) { i ->
            val t = (drift + i / 3f) % 1f
            val x = (78f + t * 14f + i * 2f) * u
            val y = (30f - t * 30f) * u
            val s = (4f + i * 1.5f) * u
            val alpha = 1f - t
            val z = Path().apply {
                moveTo(x, y); lineTo(x + s, y); lineTo(x, y + s); lineTo(x + s, y + s)
            }
            drawPath(z, Color(0xFF9AA6FF).copy(alpha = alpha), style = Stroke(1.8f * u, cap = StrokeCap.Round))
        }

        Mood.CELEBRATE, Mood.PROUD -> listOf(
            Offset(8f, 18f), Offset(90f, 14f), Offset(96f, 52f), Offset(2f, 50f),
        ).forEachIndexed { i, p ->
            val tw = abs(sin((hop + i * 0.25f) * PI)).toFloat()
            sparkle(Offset(p.x * u, p.y * u), (3f + 4f * tw) * u, Spark.copy(alpha = 0.5f + 0.5f * tw))
        }

        Mood.THINK -> repeat(3) { i ->
            drawCircle(Color(0xFF9AA6FF).copy(alpha = 0.7f), (2f + i * 1.6f) * u, Offset((82f + i * 6f) * u, (24f - i * 8f) * u))
        }

        else -> Unit
    }
}

/** A four-point star. */
fun DrawScope.sparkle(center: Offset, radius: Float, color: Color) {
    val r = radius
    val i = radius * 0.28f
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x + i, center.y - i, center.x + r, center.y)
        quadraticTo(center.x + i, center.y + i, center.x, center.y + r)
        quadraticTo(center.x - i, center.y + i, center.x - r, center.y)
        quadraticTo(center.x - i, center.y - i, center.x, center.y - r)
        close()
    }
    drawPath(path, color)
}
