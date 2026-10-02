package dev.agneswd.stillpoint.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** What Pebble feels. Each mood changes the face, the arms and the motion. */
enum class Mood { IDLE, HAPPY, CELEBRATE, CALM, SLEEPY, SAD, GUARD, WAVE, THINK, PROUD, STRICT }

val LocalPebbleStyle = staticCompositionLocalOf<Set<String>> { emptySet() }

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
fun Pebble(
    mood: Mood,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    look: Offset = Offset.Zero,
    style: Set<String> = LocalPebbleStyle.current,
) {
    val pet = remember { Animatable(0f) }
    var petting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
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

    Canvas(
        modifier.size(size, size * 1.1f)
            .semantics { contentDescription = "Pebble" }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Pet Pebble") {
                // Ignore extra taps until the response ends. Keep the current mood and its animation phase.
                if (!petting) {
                    petting = true
                    scope.launch {
                        try {
                            pet.animateTo(1f, tween(180))
                            pet.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 65f))
                        } finally {
                            petting = false
                        }
                    }
                }
            },
    ) {
        val response = pet.value
        val gentle = when (mood) {
            Mood.SLEEPY -> 0.35f
            Mood.CALM, Mood.STRICT -> 0.5f
            Mood.SAD, Mood.THINK -> 0.7f
            Mood.CELEBRATE, Mood.HAPPY -> 1.2f
            else -> 1f
        }
        val lean = if (mood == Mood.WAVE || mood == Mood.GUARD) -1f else 1f
        rotate(response * 5f * gentle * lean, Offset(this.size.width * 0.5f, this.size.height * 0.9f)) {
            scale(1f + response * 0.055f * gentle, 1f - response * 0.055f * gentle,
                pivot = Offset(this.size.width * 0.5f, this.size.height * 0.9f)) {
                drawPebble(mood, breath, blink, hop, wave, drift, look, style)
            }
        }
        if (response > 0f) drawPetHearts(response)
    }
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
    style: Set<String> = emptySet(),
) {
    val palette = paletteFor(style)
    val closedHat = style.any { it in ClosedHats }
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
            val raised = mood in setOf(Mood.CELEBRATE, Mood.WAVE, Mood.GUARD, Mood.THINK, Mood.STRICT)
            if ("outfit_cape" in style) drawCape(u)
            if (!raised) drawArms(mood, u, wave, palette)
            drawFeet(u, palette)
            // A closed hat replaces the top of the head. The cap cannot leak around its edges.
            clipRect(top = if (closedHat) 23f * u else 0f) { drawBody(u, palette) }
            drawOutfit(u, style)
            if ("accessory_glasses" !in style) drawAccessory(u, style)
            if (raised) drawArms(mood, u, wave, palette)
            if (!closedHat) drawSprout(mood, u, b, wave)
            drawHat(u, style)
            drawFace(mood, u, blink, look)
            if ("accessory_glasses" in style) drawAccessory(u, style)
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

private fun DrawScope.drawBody(u: Float, palette: PebblePalette) {
    val path = bodyPath(u)
    drawPath(path, Brush.verticalGradient(listOf(palette.top, palette.bottom), startY = 14f * u, endY = 100f * u))
    clipPath(path) {
        // Shade on the lower right gives the stone some volume.
        drawOval(palette.shade.copy(alpha = 0.55f), topLeft = Offset(48f * u, 70f * u), size = Size(70f * u, 50f * u))
        drawOval(palette.belly, topLeft = Offset(29f * u, 66f * u), size = Size(42f * u, 30f * u))
        // Shine on the upper left.
        drawOval(Color.White.copy(alpha = 0.35f), topLeft = Offset(20f * u, 24f * u), size = Size(16f * u, 9f * u))
    }
}

private fun DrawScope.drawFeet(u: Float, palette: PebblePalette) {
    drawOval(palette.shade, topLeft = Offset(28f * u, 95f * u), size = Size(18f * u, 9f * u))
    drawOval(palette.shade, topLeft = Offset(54f * u, 95f * u), size = Size(18f * u, 9f * u))
}

private fun DrawScope.drawArm(u: Float, pivot: Offset, angle: Float, left: Boolean, palette: PebblePalette) {
    rotate(angle, pivot) {
        val w = 12f * u
        val h = 22f * u
        drawOval(palette.bottom, topLeft = Offset(pivot.x - w / 2, pivot.y), size = Size(w, h))
        drawOval(palette.top.copy(alpha = 0.6f), topLeft = Offset(pivot.x - w / 2 + (if (left) 2f else 4f) * u, pivot.y + 3f * u), size = Size(5f * u, 8f * u))
    }
}

private fun DrawScope.drawArms(mood: Mood, u: Float, wave: Float, palette: PebblePalette) {
    if (mood == Mood.STRICT) {
        drawCrossedArms(u, palette)
        return
    }
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
    drawArm(u, leftPivot, left, left = true, palette = palette)
    drawArm(u, rightPivot, right, left = false, palette = palette)
    if (mood == Mood.GUARD) {
        // An open palm that says "stop".
        val hand = Offset(rightPivot.x + 8f * u, rightPivot.y - 22f * u)
        drawCircle(palette.bottom, 7.5f * u, hand)
        drawCircle(palette.top.copy(alpha = 0.6f), 3f * u, hand)
    }
}

/** Arms folded over the belly: strict and not moving. */
private fun DrawScope.drawCrossedArms(u: Float, palette: PebblePalette) {
    val back = Path().apply {
        moveTo(15f * u, 63f * u)
        cubicTo(5f * u, 65f * u, 8f * u, 84f * u, 22f * u, 88f * u)
        cubicTo(35f * u, 91f * u, 53f * u, 83f * u, 66f * u, 77f * u)
        cubicTo(74f * u, 73f * u, 69f * u, 65f * u, 62f * u, 68f * u)
        lineTo(29f * u, 78f * u)
        quadraticTo(21f * u, 77f * u, 23f * u, 67f * u)
        quadraticTo(22f * u, 63f * u, 15f * u, 63f * u)
        close()
    }
    drawPath(back, palette.bottom)
    val front = Path().apply {
        moveTo(85f * u, 63f * u)
        cubicTo(96f * u, 67f * u, 91f * u, 88f * u, 77f * u, 90f * u)
        cubicTo(63f * u, 92f * u, 46f * u, 86f * u, 32f * u, 80f * u)
        cubicTo(24f * u, 77f * u, 28f * u, 69f * u, 36f * u, 71f * u)
        lineTo(70f * u, 80f * u)
        quadraticTo(79f * u, 79f * u, 77f * u, 68f * u)
        quadraticTo(78f * u, 63f * u, 85f * u, 63f * u)
        close()
    }
    drawPath(front, Brush.verticalGradient(listOf(palette.top, palette.bottom), 63f * u, 91f * u))
    // A single lower seam separates the folded arms without outlining the shoulders.
    val fold = Path().apply {
        moveTo(35f * u, 81f * u)
        cubicTo(48f * u, 86f * u, 65f * u, 91f * u, 77f * u, 89f * u)
    }
    drawPath(fold, palette.shade.copy(alpha = 0.6f), style = Stroke(1.8f * u, cap = StrokeCap.Round))
}

private fun DrawScope.drawSprout(mood: Mood, u: Float, b: Float, wave: Float) {
    val sway = when (mood) {
        Mood.CELEBRATE -> wave * 18f
        Mood.SAD, Mood.SLEEPY -> 35f
        Mood.STRICT -> 0f
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
        Mood.STRICT -> {
            // Narrowed eyes under flat lids, looking straight ahead.
            listOf(left, right).forEach { e ->
                val lid = e.y - 1.5f * u
                clipRect(top = lid) {
                    drawOval(Color.White, Offset(e.x - 10.5f * u, e.y - 8f * u), Size(21f * u, 20f * u))
                    drawCircle(Ink, 6.4f * u, Offset(e.x, e.y + 3f * u))
                    drawCircle(Color.White, 2f * u, Offset(e.x - 2.2f * u, e.y + 0.5f * u))
                }
                drawLine(Ink, Offset(e.x - 10f * u, lid), Offset(e.x + 10f * u, lid), 2.6f * u, StrokeCap.Round)
            }
        }
        Mood.SLEEPY -> {
            listOf(left, right).forEach { e ->
                drawArc(Ink, 20f, 140f, false, Offset(e.x - 8f * u, e.y - 6f * u), Size(16f * u, 10f * u), style = stroke)
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
        Mood.STRICT -> {
            drawLine(Ink, Offset(25f * u, 36f * u), Offset(44f * u, 42.5f * u), 3.6f * u, StrokeCap.Round)
            drawLine(Ink, Offset(75f * u, 36f * u), Offset(56f * u, 42.5f * u), 3.6f * u, StrokeCap.Round)
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
        Mood.STRICT -> drawArc(Ink, 210f, 120f, false, Offset(mouth.x - 7f * u, mouth.y + 1f * u), Size(14f * u, 8f * u), style = stroke)
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

private data class PebblePalette(val top: Color, val bottom: Color, val shade: Color, val belly: Color)

private fun paletteFor(style: Set<String>): PebblePalette = when {
    "color_mint" in style -> PebblePalette(Color(0xFFA3E8CB), Color(0xFF58BC9E), Color(0xFF3C987D), Color(0xFFD9F5E8))
    "color_peach" in style -> PebblePalette(Color(0xFFFFC5A2), Color(0xFFE99680), Color(0xFFC77E71), Color(0xFFFFE6CE))
    "color_sky" in style -> PebblePalette(Color(0xFFAFE3FA), Color(0xFF69B5DE), Color(0xFF4B92BB), Color(0xFFDDF3FF))
    "color_rose" in style -> PebblePalette(Color(0xFFF5BFD5), Color(0xFFD982AD), Color(0xFFB46695), Color(0xFFFFDEEC))
    "color_sand" in style -> PebblePalette(Color(0xFFE8D4A9), Color(0xFFC3A878), Color(0xFFA68B60), Color(0xFFF5EACF))
    "color_slate" in style -> PebblePalette(Color(0xFFA6BDCD), Color(0xFF6E899F), Color(0xFF536C85), Color(0xFFD8E5EC))
    "color_lilac" in style -> PebblePalette(Color(0xFFD9BBF5), Color(0xFFAA83D5), Color(0xFF8863B4), Color(0xFFEEDFFF))
    "color_moon" in style -> PebblePalette(Color(0xFFF1EBFF), Color(0xFFB9B4DA), Color(0xFF928CB9), Color(0xFFFFF8E8))
    else -> PebblePalette(BodyTop, BodyBottom, BodyShade, Belly)
}

private fun DrawScope.drawOutfit(u: Float, style: Set<String>) {
    val id = style.firstOrNull { it.startsWith("outfit_") } ?: return
    val fabric = when (id) {
        "outfit_tee" -> Color(0xFFF8C779)
        "outfit_stripes" -> Color(0xFFF8F3DF)
        "outfit_overalls" -> Color(0xFF568AA5)
        "outfit_sweater" -> Color(0xFFCA8078)
        "outfit_raincoat" -> Color(0xFFFFD35E)
        "outfit_vest" -> Color(0xFF849E79)
        "outfit_apron" -> Color(0xFFFFE5BC)
        "outfit_stars" -> Color(0xFF56618E)
        "outfit_suit" -> Color(0xFF415269)
        "outfit_cape" -> Color(0xFF9F6CB5)
        else -> return
    }
    clipPath(bodyPath(u)) {
        val bib = id == "outfit_overalls" || id == "outfit_apron"
        val garment = Path().apply {
            when {
                bib -> {
                    moveTo(32f * u, 79f * u); lineTo(68f * u, 79f * u)
                    lineTo(72f * u, 87f * u)
                    if (id == "outfit_overalls") lineTo(93f * u, 87f * u)
                    lineTo((if (id == "outfit_overalls") 93f else 76f) * u, 102f * u)
                    lineTo((if (id == "outfit_overalls") 7f else 24f) * u, 102f * u)
                    if (id == "outfit_overalls") lineTo(7f * u, 87f * u)
                    lineTo(28f * u, 87f * u)
                }
                id == "outfit_cape" -> {
                    moveTo(17f * u, 70f * u)
                    quadraticTo(50f * u, 89f * u, 83f * u, 70f * u)
                    lineTo(79f * u, 80f * u)
                    quadraticTo(50f * u, 96f * u, 21f * u, 80f * u)
                }
                else -> {
                    moveTo(7f * u, 65f * u)
                    lineTo(23f * u, 68f * u)
                    quadraticTo(50f * u, 90f * u, 77f * u, 68f * u)
                    lineTo(93f * u, 65f * u)
                    lineTo(93f * u, 102f * u); lineTo(7f * u, 102f * u)
                }
            }
            close()
        }
        drawPath(garment, fabric)
        if (!bib && id != "outfit_cape") {
            val collar = Path().apply {
                moveTo(23f * u, 68f * u)
                quadraticTo(50f * u, 90f * u, 77f * u, 68f * u)
            }
            drawPath(collar, Color.White.copy(alpha = 0.28f), style = Stroke(2f * u, cap = StrokeCap.Round))
        }
        when (id) {
            "outfit_stripes" -> repeat(3) { i ->
                drawRect(Color(0xFF688BAA), Offset(7f * u, (80f + i * 7f) * u), Size(86f * u, 3f * u))
            }
            "outfit_overalls", "outfit_apron" -> {
                drawLine(fabric, Offset(24f * u, 69f * u), Offset(36f * u, 83f * u), 7f * u)
                drawLine(fabric, Offset(76f * u, 69f * u), Offset(64f * u, 83f * u), 7f * u)
                drawRoundRect(fabric.copy(red = fabric.red * 0.83f, green = fabric.green * 0.83f, blue = fabric.blue * 0.83f),
                    Offset(38f * u, 84f * u), Size(24f * u, 12f * u), CornerRadius(3f * u))
                drawCircle(Spark, 1.8f * u, Offset(34f * u, 79f * u))
                drawCircle(Spark, 1.8f * u, Offset(66f * u, 79f * u))
                if (id == "outfit_apron") {
                    drawCircle(Color(0xFF84BBA5), 2.5f * u, Offset(59f * u, 91f * u))
                    drawCircle(Color(0xFFD686A7), 2f * u, Offset(44f * u, 88f * u))
                }
            }
            "outfit_sweater" -> {
                repeat(7) { i -> drawLine(Color(0xFFE8AEA0), Offset((21f + i * 10f) * u, 94f * u), Offset((21f + i * 10f) * u, 99f * u), 1.3f * u) }
                val knit = Path().apply {
                    moveTo(20f * u, 83f * u)
                    for (i in 1..6) lineTo((20f + i * 10f) * u, (if (i % 2 == 0) 83f else 88f) * u)
                }
                drawPath(knit, Color(0xFFF0CFB1), style = Stroke(2f * u))
            }
            "outfit_raincoat", "outfit_vest", "outfit_suit" -> {
                drawLine(fabric.copy(red = fabric.red * 0.7f, green = fabric.green * 0.7f, blue = fabric.blue * 0.7f),
                    Offset(50f * u, 79f * u), Offset(50f * u, 100f * u), 1.5f * u)
                repeat(3) { i -> drawCircle(if (id == "outfit_suit") Spark else Color(0xFF59676D), 1.4f * u, Offset(54f * u, (83f + i * 6f) * u)) }
                drawRoundRect(Color.White.copy(alpha = 0.24f), Offset(24f * u, 86f * u), Size(14f * u, 8f * u), CornerRadius(2f * u))
                drawRoundRect(Color.White.copy(alpha = 0.24f), Offset(65f * u, 86f * u), Size(14f * u, 8f * u), CornerRadius(2f * u))
                if (id == "outfit_suit") {
                    val collar = Path().apply { moveTo(33f * u, 73f * u); lineTo(50f * u, 83f * u); lineTo(67f * u, 73f * u); lineTo(50f * u, 91f * u); close() }
                    drawPath(collar, Color(0xFFF7ECD5))
                }
            }
            "outfit_stars" -> listOf(Offset(30f, 84f), Offset(65f, 90f), Offset(45f, 96f), Offset(78f, 80f)).forEach {
                sparkle(it * u, 3.2f * u, Color(0xFFFFE6A2))
            }
            "outfit_cape" -> {
                drawCircle(Spark, 3f * u, Offset(50f * u, 82f * u))
            }
            else -> Unit
        }
    }
}

private val ClosedHats = setOf(
    "hat_beanie", "hat_bucket", "hat_sun", "hat_beret", "hat_sleep", "hat_captain", "hat_wizard",
)

private fun DrawScope.drawCape(u: Float) {
    val cape = Path().apply {
        moveTo(20f * u, 66f * u)
        quadraticTo(2f * u, 79f * u, 1f * u, 96f * u)
        quadraticTo(18f * u, 108f * u, 50f * u, 99f * u)
        quadraticTo(82f * u, 108f * u, 99f * u, 96f * u)
        quadraticTo(98f * u, 79f * u, 80f * u, 66f * u)
        close()
    }
    drawPath(cape, Color(0xFF81539A))
}

private fun DrawScope.drawHat(u: Float, style: Set<String>) {
    val id = style.firstOrNull { it.startsWith("hat_") } ?: return
    fun brim(color: Color, x: Float = 19f, width: Float = 62f) {
        drawRoundRect(color, Offset(x * u, 22f * u), Size(width * u, 6f * u), CornerRadius(3f * u))
    }
    when (id) {
        "hat_beanie" -> {
            val cap = Path().apply {
                moveTo(25f * u, 24f * u)
                cubicTo(25f * u, -1f * u, 75f * u, -1f * u, 75f * u, 24f * u)
                close()
            }
            drawPath(cap, Color(0xFFE7A57F))
            brim(Color(0xFFBD7C68), 25f, 50f)
            repeat(8) { i -> drawLine(Color(0xFFDEA087), Offset((29f + i * 6f) * u, 23f * u), Offset((29f + i * 6f) * u, 27f * u), 1.4f * u) }
            drawCircle(Color(0xFFF3C2A0), 4.5f * u, Offset(50f * u, 5f * u))
        }
        "hat_bucket", "hat_sun", "hat_captain" -> {
            val color = when (id) { "hat_sun" -> Color(0xFFEAC887); "hat_captain" -> Color(0xFFF1EBD5); else -> Color(0xFF90C5B0) }
            val cap = Path().apply {
                moveTo(26f * u, 24f * u); lineTo(31f * u, 9f * u)
                quadraticTo(50f * u, 3f * u, 69f * u, 9f * u)
                lineTo(74f * u, 24f * u); close()
            }
            drawPath(cap, color)
            brim(if (id == "hat_captain") Ink else color, if (id == "hat_sun") 9f else 19f, if (id == "hat_sun") 82f else 62f)
            drawLine(if (id == "hat_captain") Color(0xFFCDA758) else color.copy(red = color.red * 0.8f, green = color.green * 0.8f, blue = color.blue * 0.8f),
                Offset(28f * u, 20f * u), Offset(72f * u, 20f * u), 3f * u)
            if (id == "hat_captain") sparkle(Offset(50f * u, 13f * u), 4f * u, Spark)
        }
        "hat_flower" -> {
            repeat(6) { i -> rotate(i * 60f, Offset(69f * u, 22f * u)) {
                drawOval(Color(0xFFFFF6D8), Offset(66f * u, 11f * u), Size(6f * u, 12f * u))
            } }
            drawCircle(Spark, 4.4f * u, Offset(69f * u, 22f * u))
        }
        "hat_beret" -> {
            drawOval(Color(0xFFD78380), Offset(18f * u, 5f * u), Size(62f * u, 21f * u))
            brim(Color(0xFFA96471), 24f, 52f)
            drawLine(Color(0xFFA96471), Offset(48f * u, 8f * u), Offset(51f * u, 3f * u), 3f * u, StrokeCap.Round)
        }
        "hat_sleep", "hat_wizard" -> {
            val night = id == "hat_sleep"
            val cap = Path().apply {
                moveTo(25f * u, 25f * u)
                if (night) {
                    quadraticTo(43f * u, -2f * u, 66f * u, 6f * u)
                    quadraticTo(81f * u, 10f * u, 80f * u, 19f * u)
                    quadraticTo(68f * u, 10f * u, 75f * u, 25f * u)
                } else {
                    quadraticTo(30f * u, 14f * u, 49f * u, 1f * u)
                    quadraticTo(65f * u, 11f * u, 75f * u, 25f * u)
                }
                close()
            }
            drawPath(cap, if (night) Color(0xFF86A9BD) else Color(0xFF7764AC))
            brim(if (night) Color(0xFFD9EBEC) else Color(0xFF5E508E), 23f, 54f)
            if (night) drawCircle(Color(0xFFD9EBEC), 4f * u, Offset(80f * u, 19f * u))
            else sparkle(Offset(49f * u, 15f * u), 4.5f * u, Spark)
        }
        "hat_bow" -> drawBow(Offset(68f * u, 20f * u), 11f * u, Color(0xFFD987AC))
        "hat_crown" -> {
            val crown = Path().apply {
                moveTo(26f * u, 25f * u); lineTo(23f * u, 9f * u); lineTo(38f * u, 16f * u)
                lineTo(50f * u, 4f * u); lineTo(62f * u, 16f * u); lineTo(77f * u, 9f * u)
                lineTo(74f * u, 25f * u); close()
            }
            drawPath(crown, Spark)
            brim(Color(0xFFE3A633), 26f, 48f)
            drawCircle(Color(0xFFD582A6), 3f * u, Offset(50f * u, 19f * u))
            listOf(23f to 9f, 50f to 4f, 77f to 9f).forEach { (x, y) -> drawCircle(Color(0xFFFFE4A4), 2f * u, Offset(x * u, y * u)) }
        }
    }
}

private fun DrawScope.drawAccessory(u: Float, style: Set<String>) {
    when (style.firstOrNull { it.startsWith("accessory_") }) {
        "accessory_scarf" -> {
            drawRoundRect(Color(0xFFD88D73), Offset(25f * u, 73f * u), Size(50f * u, 8f * u), CornerRadius(4f * u))
            drawRoundRect(Color(0xFFB96E64), Offset(64f * u, 77f * u), Size(9f * u, 19f * u), CornerRadius(3f * u))
            drawLine(Color(0xFFF1C09D), Offset(65f * u, 90f * u), Offset(72f * u, 90f * u), 2f * u)
        }
        "accessory_glasses" -> {
            listOf(36f, 64f).forEach { drawCircle(Color(0xFF7D564C), 12f * u, Offset(it * u, 50f * u), style = Stroke(2f * u)) }
            drawArc(Color(0xFF7D564C), 180f, 180f, false, Offset(48f * u, 47f * u), Size(4f * u, 4f * u), style = Stroke(2f * u))
            drawLine(Color(0xFF7D564C), Offset(19f * u, 47f * u), Offset(24f * u, 49f * u), 2f * u)
            drawLine(Color(0xFF7D564C), Offset(76f * u, 49f * u), Offset(81f * u, 47f * u), 2f * u)
        }
        "accessory_bowtie" -> drawBow(Offset(50f * u, 79f * u), 8f * u, Color(0xFF729E98))
        "accessory_satchel" -> {
            drawLine(Color(0xFFAB805C), Offset(28f * u, 71f * u), Offset(72f * u, 92f * u), 4f * u, StrokeCap.Round)
            drawRoundRect(Color(0xFFB98C66), Offset(62f * u, 82f * u), Size(22f * u, 17f * u), CornerRadius(4f * u))
            drawRoundRect(Color(0xFF93684E), Offset(62f * u, 82f * u), Size(22f * u, 6f * u), CornerRadius(3f * u))
            drawCircle(Spark, 1.6f * u, Offset(73f * u, 89f * u))
        }
        "accessory_headphones" -> {
            drawArc(Color(0xFF46566D), 180f, 180f, false, Offset(14f * u, 15f * u), Size(72f * u, 66f * u), style = Stroke(4f * u))
            listOf(11f, 80f).forEach { x ->
                drawRoundRect(Color(0xFF46566D), Offset(x * u, 42f * u), Size(10f * u, 21f * u), CornerRadius(5f * u))
                drawRoundRect(Color(0xFFEDC486), Offset((x + 2f) * u, 46f * u), Size(6f * u, 13f * u), CornerRadius(3f * u))
            }
        }
        "accessory_neckerchief" -> {
            val cloth = Path().apply { moveTo(27f * u, 73f * u); lineTo(50f * u, 94f * u); lineTo(73f * u, 73f * u); quadraticTo(50f * u, 84f * u, 27f * u, 73f * u); close() }
            drawPath(cloth, Color(0xFF739F86))
            drawCircle(Color(0xFFECCF85), 3f * u, Offset(50f * u, 82f * u))
        }
        "accessory_medal" -> {
            drawLine(Color(0xFFD9838B), Offset(35f * u, 74f * u), Offset(50f * u, 88f * u), 4f * u)
            drawLine(Color(0xFFD9838B), Offset(65f * u, 74f * u), Offset(50f * u, 88f * u), 4f * u)
            drawCircle(Spark, 6f * u, Offset(50f * u, 91f * u))
            sparkle(Offset(50f * u, 91f * u), 3.8f * u, Color(0xFFFFEDB4))
        }
        "accessory_star" -> {
            drawRoundRect(Color(0xFF96A5C6), Offset(62f * u, 82f * u), Size(17f * u, 13f * u), CornerRadius(3f * u))
            sparkle(Offset(70f * u, 82f * u), 8f * u, Spark)
            drawCircle(Ink, 0.8f * u, Offset(68f * u, 82f * u))
            drawCircle(Ink, 0.8f * u, Offset(72f * u, 82f * u))
        }
        else -> Unit
    }
}

private fun DrawScope.drawBow(center: Offset, radius: Float, color: Color) {
    val bow = Path().apply {
        moveTo(center.x, center.y)
        quadraticTo(center.x - radius * 1.6f, center.y - radius * 1.5f, center.x - radius, center.y)
        quadraticTo(center.x - radius * 1.6f, center.y + radius * 1.5f, center.x, center.y)
        quadraticTo(center.x + radius * 1.6f, center.y - radius * 1.5f, center.x + radius, center.y)
        quadraticTo(center.x + radius * 1.6f, center.y + radius * 1.5f, center.x, center.y)
        close()
    }
    drawPath(bow, color)
    drawCircle(color.copy(red = color.red * 0.8f, green = color.green * 0.8f, blue = color.blue * 0.8f), radius * 0.3f, center)
}

/** The response fades over the existing pose. Petting never changes focus or navigation. */
private fun DrawScope.drawPetHearts(response: Float) {
    val u = size.width / 100f
    val alpha = response.coerceIn(0f, 1f)
    listOf(Offset(13f, 30f), Offset(88f, 21f)).forEachIndexed { index, position ->
        val x = position.x * u
        val y = (position.y - (1f - alpha) * 10f) * u
        val r = (3.5f + index) * u
        val heart = Path().apply {
            moveTo(x, y + r)
            cubicTo(x - r * 2f, y, x - r, y - r * 1.4f, x, y - r * 0.4f)
            cubicTo(x + r, y - r * 1.4f, x + r * 2f, y, x, y + r)
            close()
        }
        drawPath(heart, Cheek.copy(alpha = alpha))
    }
}
