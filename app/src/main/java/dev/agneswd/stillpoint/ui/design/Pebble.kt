package dev.agneswd.stillpoint.ui.design

import dev.agneswd.stillpoint.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import android.os.SystemClock

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

/** The color of Pebble's thought dots. Speech bubbles reuse it for their trail. */
val ThoughtDot = Color(0xFF9AA6FF).copy(alpha = 0.7f)

/**
 * Pebble, the Stillpoint mascot: a round stone with a sprout on top.
 * It is drawn in code, so it scales to any size and animates without image files.
 * [look] moves the pupils, from -1 to 1 on each axis.
 * [thoughtDots] is false when a speech bubble beside Pebble draws its own thought trail.
 */
@Composable
fun Pebble(
    mood: Mood,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    look: Offset = Offset.Zero,
    style: Set<String> = LocalPebbleStyle.current,
    thoughtDots: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pet = remember { PetMotion() }
    val taps = remember { Channel<Unit>(Channel.CONFLATED) }
    var petFrame by remember { mutableIntStateOf(0) }
    LaunchedEffect(pet) {
        // A single frame loop consumes all taps. Each tap adds momentum before it sends a wake-up signal.
        for (ignored in taps) {
            var previous = withFrameNanos { it }
            while (pet.active) {
                val now = withFrameNanos { it }
                val durationScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
                if (durationScale <= 0f) pet.clear()
                else pet.advance(((now - previous) / 1_000_000_000f / durationScale).coerceIn(0f, 0.04f))
                previous = now
                petFrame++
            }
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pebbleName = stringResource(R.string.pebble_name)
    val petLabel = stringResource(R.string.pebble_pet_action)
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
            .semantics { contentDescription = pebbleName }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = petLabel) {
                dev.agneswd.stillpoint.game.PebblePets.pet(context)
                pet.tap(SystemClock.uptimeMillis())
                taps.trySend(Unit)
            },
    ) {
        // Read one state value in the draw phase. The physics model does not recompose the screen.
        @Suppress("UNUSED_VARIABLE") val frame = petFrame
        val gentle = when (mood) {
            Mood.SLEEPY -> 0.22f
            Mood.CALM, Mood.STRICT -> 0.4f
            Mood.SAD, Mood.THINK -> 0.65f
            Mood.CELEBRATE, Mood.HAPPY -> 1.1f
            else -> 1f
        }
        val u = this.size.width / 100f
        val pivot = Offset(this.size.width * 0.5f, this.size.height * 0.9f)
        val petBlink = blink * (1f - pet.warmth * 0.5f * gentle)
        val petLook = Offset((look.x + pet.lean * 0.045f).coerceIn(-1f, 1f), look.y)
        translate(top = -pet.lift.coerceAtLeast(0f) * gentle * u) {
            rotate(pet.lean * gentle, pivot) {
                scale(1f + pet.press * 0.065f * gentle, 1f - pet.press * 0.065f * gentle, pivot) {
                    drawPebble(mood, breath, petBlink, hop, wave + pet.lean * 0.035f * gentle, drift, petLook, style, thoughtDots)
                }
            }
        }
        drawPetParticles(pet, gentle)

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
    thoughtDots: Boolean = true,
) {
    val palette = paletteFor(style)
    val closedHat = style.any { it in ClosedHats }
    val u = size.width / 100f
    val b = sin(breath * 2 * PI).toFloat()
    val slow = sin(drift * 2 * PI).toFloat()
    fun pulse(start: Float, end: Float): Float {
        if (drift <= start || drift >= end) return 0f
        val value = sin((drift - start) / (end - start) * PI).toFloat()
        return value * value
    }
    val happyHop = pulse(0.04f, 0.21f) * 4.5f + pulse(0.28f, 0.45f) * 3f
    val jump = when (mood) {
        Mood.CELEBRATE -> sin(hop * PI).toFloat().let { it * it * 14f }
        Mood.HAPPY -> happyHop
        Mood.PROUD -> (1f + b) * 0.7f
        Mood.CALM -> 3f + b * 3f
        Mood.SAD -> (1f + b) * 0.25f
        Mood.SLEEPY, Mood.STRICT -> 0f
        else -> (1f + b) * 0.6f
    }
    val squash = if (mood == Mood.CELEBRATE) {
        val landing = 1f - jump / 14f
        0.055f * landing * landing
    } else 0f
    val lean = when (mood) {
        Mood.IDLE -> slow * 1.6f
        Mood.THINK -> -4f + slow * 2.5f
        Mood.HAPPY -> slow * 1.2f
        Mood.SAD -> 2f + slow * 0.6f
        Mood.WAVE -> slow * 1.5f
        else -> 0f
    }
    val breathAmount = when (mood) {
        Mood.SAD -> 0.006f
        Mood.SLEEPY -> 0.022f
        Mood.STRICT -> 0.004f
        else -> 0.015f
    }
    val stretch = if (mood == Mood.PROUD) 0.018f + (1f + b) * 0.008f else 0f
    val gaze = look + when (mood) {
        Mood.IDLE -> Offset(slow * 0.6f, sin(drift * 4 * PI).toFloat() * 0.12f)
        Mood.THINK -> Offset(slow * 0.22f, b * 0.12f)
        Mood.SAD -> Offset(slow * 0.12f, 0f)
        else -> Offset.Zero
    }

    // Ground shadow, smaller when Pebble is in the air.
    val shadowScale = 1f - jump / 30f
    drawOval(
        Color.Black.copy(alpha = 0.12f),
        topLeft = Offset((50f - 30f * shadowScale) * u, 102f * u),
        size = Size(60f * shadowScale * u, 7f * u),
    )

    translate(top = -jump * u) {
        rotate(lean, pivot = Offset(50f * u, 100f * u)) {
            scale(1f - b * breathAmount * 0.5f + squash - stretch * 0.35f, 1f + b * breathAmount - squash + stretch, pivot = Offset(50f * u, 100f * u)) {
                val raised = mood in setOf(Mood.CELEBRATE, Mood.WAVE, Mood.GUARD, Mood.THINK, Mood.STRICT)
                if ("outfit_cape" in style || "outfit_star_guardian" in style) drawCape(u, guardian = "outfit_star_guardian" in style)
                if (!raised) drawArms(mood, u, wave, palette, drift)
                drawFeet(u, palette)
                // A closed hat replaces the top of the head. The cap cannot leak around its edges.
                clipRect(top = if (closedHat) 23f * u else 0f) { drawBody(u, palette) }
                drawOutfit(u, style)
                if ("accessory_glasses" !in style) drawAccessory(u, style)
                if (raised) drawArms(mood, u, wave, palette, drift)
                if (!closedHat) drawSprout(mood, u, b, wave)
                drawHat(u, style)
                drawFace(mood, u, blink, gaze)
                if ("accessory_glasses" in style) drawAccessory(u, style)
            }
        }
    }
    if (mood != Mood.THINK || thoughtDots) drawExtras(mood, u, drift, hop)
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

private fun DrawScope.drawArms(mood: Mood, u: Float, wave: Float, palette: PebblePalette, drift: Float) {
    if (mood == Mood.STRICT) {
        drawCrossedArms(u, palette)
        return
    }
    val leftPivot = Offset(12f * u, 62f * u)
    val rightPivot = Offset(88f * u, 62f * u)
    val sway = sin(drift * 2 * PI).toFloat()
    val wavePhase = (drift / 0.58f).coerceIn(0f, 1f)
    val waveLift = sin(wavePhase * PI).toFloat().let { it * it }
    val greeting = sin(wavePhase * 4 * PI).toFloat()
    val (left, right) = when (mood) {
        Mood.CELEBRATE -> 150f + wave * 10f to -150f - wave * 10f
        Mood.WAVE -> 25f + sway * 3f to -25f + waveLift * (-125f + greeting * 20f)
        Mood.GUARD -> 25f to -160f
        Mood.THINK -> 20f to -116f + sway * 5f
        Mood.PROUD -> -40f - sway * 4f to 40f + sway * 4f
        Mood.CALM -> 60f to -60f
        Mood.SAD -> 8f + sway to -8f - sway
        Mood.SLEEPY -> 10f to -10f
        Mood.HAPPY -> 32f + sway * 8f to -32f - sway * 8f
        Mood.IDLE -> 25f + sway * 3f to -25f + sway * 3f
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
        Mood.SAD -> 32f + b * 3f
        Mood.SLEEPY -> 35f + b * 2f
        Mood.HAPPY -> b * 12f
        Mood.PROUD -> b * 4f
        Mood.THINK -> -10f + b * 6f
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
                Mood.THINK -> Offset(0.6f, -0.8f) + look
                Mood.SAD -> Offset(0f, 0.6f) + look
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
            drawCircle(ThoughtDot, (2f + i * 1.6f) * u, Offset((82f + i * 6f) * u, (24f - i * 8f) * u))
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
        "outfit_star_guardian" -> Color(0xFF283753)
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
                    moveTo(-3f * u, 62f * u)
                    quadraticTo(22f * u, 73f * u, 50f * u, 80f * u)
                    quadraticTo(78f * u, 73f * u, 103f * u, 62f * u)
                    lineTo(103f * u, 72f * u)
                    quadraticTo(76f * u, 81f * u, 50f * u, 86f * u)
                    quadraticTo(24f * u, 81f * u, -3f * u, 72f * u)
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
        if (id == "outfit_apron") {
            // The waist tie continues around both sides, behind the front panel.
            drawPath(wrappedBand(u, 82f, 5f, 5f), fabric)
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
                listOf(false, true).forEach { right ->
                    fun x(value: Float) = (if (right) 100f - value else value) * u
                    val strap = Path().apply {
                        moveTo(x(-3f), 61f * u)
                        cubicTo(x(13f), 63f * u, x(29f), 69f * u, x(35f), 81f * u)
                    }
                    drawPath(strap, fabric, style = Stroke(7f * u))
                    drawPath(strap, Color.White.copy(alpha = 0.22f), style = Stroke(1.1f * u))
                }
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
            "outfit_star_guardian" -> {
                val trim = Color(0xFFEAC579)
                val collar = Path().apply {
                    moveTo(-3f * u, 61f * u)
                    quadraticTo(22f * u, 68f * u, 50f * u, 80f * u)
                    quadraticTo(78f * u, 68f * u, 103f * u, 61f * u)
                }
                drawPath(collar, Color(0xFF354867), style = Stroke(7f * u))
                drawPath(collar, trim, style = Stroke(1.3f * u))
                drawCircle(Color(0xFFB68A44), 4f * u, Offset(50f * u, 80f * u))
                sparkle(Offset(50f * u, 80f * u), 3.2f * u, Color(0xFFFFE6A6))
                val stars = listOf(Offset(23f, 83f), Offset(32f, 92f), Offset(43f, 88f))
                val constellation = Path().apply {
                    moveTo(stars.first().x * u, stars.first().y * u)
                    stars.drop(1).forEach { lineTo(it.x * u, it.y * u) }
                }
                drawPath(constellation, Color(0xFF8FABC2), style = Stroke(0.9f * u))
                stars.forEach { drawCircle(trim, 1.5f * u, it * u) }
                sparkle(Offset(67f * u, 88f * u), 4.5f * u, trim)
                drawCircle(Color(0xFFB2CAD9), 1.3f * u, Offset(76f * u, 94f * u))
                val hem = Path().apply {
                    moveTo(10f * u, 95f * u)
                    quadraticTo(50f * u, 106f * u, 90f * u, 95f * u)
                }
                drawPath(hem, trim, style = Stroke(1.2f * u))
            }
            "outfit_cape" -> {
                drawCircle(Color(0xFF704587), 4f * u, Offset(50f * u, 82f * u))
                drawCircle(Spark, 3f * u, Offset(50f * u, 82f * u))
            }
            else -> Unit
        }
    }
}

private val ClosedHats = setOf(
    "hat_beanie", "hat_bucket", "hat_sun", "hat_beret", "hat_sleep", "hat_captain", "hat_wizard",
)

private fun DrawScope.drawCape(u: Float, guardian: Boolean = false) {
    val cape = Path().apply {
        moveTo(10f * u, 60f * u)
        quadraticTo(1f * u, 77f * u, 0f, 97f * u)
        quadraticTo(18f * u, 108f * u, 50f * u, 100f * u)
        quadraticTo(82f * u, 108f * u, 100f * u, 97f * u)
        quadraticTo(99f * u, 77f * u, 90f * u, 60f * u)
        quadraticTo(50f * u, 76f * u, 10f * u, 60f * u)
        close()
    }
    drawPath(cape, if (guardian) Color(0xFF19243C) else Color(0xFF81539A))
    if (guardian) {
        val hem = Path().apply {
            moveTo(1f * u, 96f * u)
            quadraticTo(18f * u, 107f * u, 50f * u, 99f * u)
            quadraticTo(82f * u, 107f * u, 99f * u, 96f * u)
        }
        drawPath(hem, Color(0xFFEAC579), style = Stroke(1.5f * u))
    }
    listOf(false, true).forEach { right ->
        fun x(value: Float) = (if (right) 100f - value else value) * u
        val fold = Path().apply {
            moveTo(x(11f), 67f * u)
            quadraticTo(x(4f), 83f * u, x(7f), 97f * u)
        }
        drawPath(fold, if (guardian) Color(0xFF526787) else Color(0xFFAE80BF), style = Stroke(2f * u, cap = StrokeCap.Round))
    }
}

/** The ends extend past the body so clipping makes the band continue behind it. */
private fun wrappedBand(u: Float, edgeY: Float, dip: Float, thickness: Float) = Path().apply {
    moveTo(-3f * u, edgeY * u)
    quadraticTo(50f * u, (edgeY + dip * 2f) * u, 103f * u, edgeY * u)
    lineTo(103f * u, (edgeY + thickness) * u)
    quadraticTo(50f * u, (edgeY + dip * 2f + thickness) * u, -3f * u, (edgeY + thickness) * u)
    close()
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
            clipPath(bodyPath(u)) {
                drawRoundRect(Color(0xFFB96E64), Offset(68f * u, 77f * u), Size(10f * u, 21f * u), CornerRadius(3f * u))
                drawLine(Color(0xFFF1C09D), Offset(69f * u, 92f * u), Offset(77f * u, 92f * u), 2f * u)
                drawPath(wrappedBand(u, 64f, 15f, 8f), Color(0xFFD88D73))
                drawPath(wrappedBand(u, 70f, 15f, 2f), Color(0xFFC17A66))
                drawOval(Color(0xFFE8A486), Offset(66f * u, 76f * u), Size(12f * u, 10f * u))
            }
        }
        "accessory_glasses" -> {
            listOf(36f, 64f).forEach { drawCircle(Color(0xFF7D564C), 12f * u, Offset(it * u, 50f * u), style = Stroke(2f * u)) }
            drawArc(Color(0xFF7D564C), 180f, 180f, false, Offset(48f * u, 47f * u), Size(4f * u, 4f * u), style = Stroke(2f * u))
            drawLine(Color(0xFF7D564C), Offset(19f * u, 47f * u), Offset(24f * u, 49f * u), 2f * u)
            drawLine(Color(0xFF7D564C), Offset(76f * u, 49f * u), Offset(81f * u, 47f * u), 2f * u)
        }
        "accessory_bowtie" -> drawBow(Offset(50f * u, 79f * u), 8f * u, Color(0xFF729E98))
        "accessory_satchel" -> {
            clipPath(bodyPath(u)) {
                val strap = Path().apply {
                    moveTo(-3f * u, 63f * u)
                    cubicTo(26f * u, 66f * u, 48f * u, 85f * u, 78f * u, 89f * u)
                }
                drawPath(strap, Color(0xFF93684E), style = Stroke(5f * u))
                drawPath(strap, Color(0xFFCFA578), style = Stroke(2f * u))
            }
            drawRoundRect(Color(0xFFB98C66), Offset(67f * u, 83f * u), Size(24f * u, 18f * u), CornerRadius(4f * u))
            drawRoundRect(Color(0xFF93684E), Offset(67f * u, 83f * u), Size(24f * u, 7f * u), CornerRadius(3f * u))
            drawCircle(Spark, 1.6f * u, Offset(79f * u, 91f * u))
        }
        "accessory_headphones" -> {
            drawArc(Color(0xFF46566D), 180f, 180f, false, Offset(14f * u, 15f * u), Size(72f * u, 66f * u), style = Stroke(4f * u))
            listOf(11f, 80f).forEach { x ->
                drawRoundRect(Color(0xFF46566D), Offset(x * u, 42f * u), Size(10f * u, 21f * u), CornerRadius(5f * u))
                drawRoundRect(Color(0xFFEDC486), Offset((x + 2f) * u, 46f * u), Size(6f * u, 13f * u), CornerRadius(3f * u))
            }
        }
        "accessory_neckerchief" -> {
            clipPath(bodyPath(u)) {
                val cloth = Path().apply {
                    moveTo(-3f * u, 64f * u)
                    quadraticTo(50f * u, 96f * u, 103f * u, 64f * u)
                    quadraticTo(79f * u, 85f * u, 50f * u, 98f * u)
                    quadraticTo(21f * u, 85f * u, -3f * u, 64f * u)
                    close()
                }
                drawPath(cloth, Color(0xFF739F86))
                drawPath(wrappedBand(u, 64f, 16f, 3f), Color(0xFF9FC5A8))
                drawCircle(Color(0xFFECCF85), 3f * u, Offset(50f * u, 83f * u))
            }
        }
        "accessory_medal" -> {
            clipPath(bodyPath(u)) {
                val ribbon = Path().apply {
                    moveTo(-3f * u, 65f * u)
                    quadraticTo(23f * u, 73f * u, 50f * u, 87f * u)
                    quadraticTo(77f * u, 73f * u, 103f * u, 65f * u)
                }
                drawPath(ribbon, Color(0xFFD9838B), style = Stroke(4.5f * u))
                drawPath(ribbon, Color(0xFFF0B4B0), style = Stroke(1f * u))
                drawCircle(Color(0xFFCB9C48), 2f * u, Offset(50f * u, 87f * u), style = Stroke(1.4f * u))
                drawCircle(Spark, 6f * u, Offset(50f * u, 93f * u))
                sparkle(Offset(50f * u, 93f * u), 3.8f * u, Color(0xFFFFEDB4))
            }
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

/** Bounded impulses preserve position and momentum when another tap arrives. */
private class PetMotion {
    var press = 0f
        private set
    var lean = 0f
        private set
    var lift = 0f
        private set
    var warmth = 0f
        private set
    private var pressVelocity = 0f
    private var leanVelocity = 0f
    private var liftVelocity = 0f
    private var warmthVelocity = 0f
    private var lastTap = Long.MIN_VALUE
    private var combo = 0
    private var nextParticle = 0
    val particles = Array(12) { PetParticle() }
    val active: Boolean get() = abs(press) + abs(lean) + abs(lift) + warmth +
        abs(pressVelocity) + abs(leanVelocity) + abs(liftVelocity) + abs(warmthVelocity) > 0.002f ||
        particles.any { it.age < it.life }

    fun tap(now: Long) {
        combo = if (lastTap != Long.MIN_VALUE && now - lastTap < 900) (combo + 1) % 24 else 0
        lastTap = now
        val variation = combo % 4
        val direction = if (variation == 0 || variation == 3) 1f else -1f
        pressVelocity = (pressVelocity + if (variation == 2) 7f else 10f).coerceIn(-20f, 20f)
        leanVelocity = (leanVelocity + direction * (65f + variation * 12f)).coerceIn(-160f, 160f)
        liftVelocity = (liftVelocity + if (variation == 2) 95f else 45f).coerceIn(-100f, 145f)
        warmthVelocity = (warmthVelocity + 5f).coerceAtMost(10f)
        repeat(if (combo < 2) 2 else 3) {
            // Let visible particles finish their flight, even when taps fill the pool.
            var available = -1
            for (offset in particles.indices) {
                val index = (nextParticle + offset) % particles.size
                if (particles[index].age >= particles[index].life) {
                    available = index
                    break
                }
            }
            if (available < 0) return@repeat
            nextParticle = available
            particles[nextParticle].apply {
                age = 0f
                life = 0.85f + variation * 0.09f
                side = if (nextParticle % 2 == 0) -1f else 1f
                startY = 30f + (nextParticle % 3) * 9f
                speed = 12f + variation * 3f
                star = (nextParticle + variation) % 3 == 0
            }
            nextParticle = (nextParticle + 1) % particles.size
        }
    }

    fun advance(seconds: Float) {
        // Small fixed substeps keep springs stable after a slow frame or a rapid tap burst.
        var remaining = seconds
        while (remaining > 0f) {
            val dt = minOf(remaining, 1f / 120f)
            pressVelocity += (-170f * press - 18f * pressVelocity) * dt
            leanVelocity += (-125f * lean - 14f * leanVelocity) * dt
            liftVelocity += (-150f * lift - 17f * liftVelocity) * dt
            warmthVelocity += (-85f * warmth - 16f * warmthVelocity) * dt
            press += pressVelocity * dt
            lean += leanVelocity * dt
            lift += liftVelocity * dt
            warmth += warmthVelocity * dt
            if (abs(press) > 1.2f) { press = press.coerceIn(-1.2f, 1.2f); pressVelocity *= -0.15f }
            if (abs(lean) > 11f) { lean = lean.coerceIn(-11f, 11f); leanVelocity *= -0.15f }
            if (lift > 11f) { lift = 11f; liftVelocity *= -0.15f }
            if (lift < 0f) { lift = 0f; liftVelocity = 0f }
            if (warmth > 1f) { warmth = 1f; warmthVelocity = 0f }
            if (warmth < 0f) { warmth = 0f; warmthVelocity = 0f }
            remaining -= dt
        }
        for (particle in particles) particle.age = minOf(particle.life, particle.age + seconds)
        if (!active) clear()
    }

    fun clear() {
        press = 0f; lean = 0f; lift = 0f; warmth = 0f
        pressVelocity = 0f; leanVelocity = 0f; liftVelocity = 0f; warmthVelocity = 0f
        for (particle in particles) particle.age = particle.life
    }
}

private class PetParticle {
    var age = 1f
    var life = 1f
    var side = 1f
    var startY = 30f
    var speed = 12f
    var star = false
}

private fun DrawScope.drawPetParticles(pet: PetMotion, gentle: Float) {
    val u = size.width / 100f
    for (particle in pet.particles) {
        if (particle.age >= particle.life) continue
        val progress = particle.age / particle.life
        val alpha = (sin(progress * PI).toFloat() * (0.55f + gentle * 0.35f)).coerceIn(0f, 1f)
        val x = (50f + particle.side * (34f + sin(progress * PI).toFloat() * 10f)) * u
        val y = (particle.startY - particle.age * particle.speed * (0.5f + gentle * 0.5f)) * u
        val radius = (2.4f + sin(progress * PI).toFloat() * 1.3f) * u
        if (particle.star) {
            sparkle(Offset(x, y), radius * 1.25f, Spark.copy(alpha = alpha))
        } else {
            val heart = Path().apply {
                moveTo(x, y + radius)
                cubicTo(x - radius * 2f, y, x - radius, y - radius * 1.4f, x, y - radius * 0.4f)
                cubicTo(x + radius, y - radius * 1.4f, x + radius * 2f, y, x, y + radius)
                close()
            }
            drawPath(heart, Cheek.copy(alpha = alpha))
        }
    }
}
