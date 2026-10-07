package dev.agneswd.ronumi.ui.design

import dev.agneswd.ronumi.R
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import kotlin.math.PI
import kotlin.math.sin

/** Animated backgrounds for the focus screen. All are drawn on the device; none are photos. [plus] scenes need Ronumi Plus. */
enum class FocusTheme(@param:StringRes val labelRes: Int, val plus: Boolean = false) {
    LAKE(R.string.focus_scene_lake),
    DAWN(R.string.focus_scene_dawn),
    FOREST(R.string.focus_scene_forest),
    SPACE(R.string.focus_scene_space),
    RAIN(R.string.focus_scene_rain),
    NORTHERN_LIGHTS(R.string.focus_scene_northern_lights, plus = true),
    CAMPFIRE(R.string.focus_scene_campfire, plus = true),
}

/**
 * Draws a [FocusTheme]. [center] is where the timer sits, as a fraction of the size.
 * The lake ripples spread from there.
 * Pass [animated] false for a paused session. The scene then stays on one frame.
 * A system animator duration scale of 0 also keeps the scene still.
 */
@Composable
fun FocusBackdrop(theme: FocusTheme, modifier: Modifier, center: Offset = Offset(0.5f, 0.38f), animated: Boolean = true) {
    val motion = animated && systemAnimationsEnabled()
    // Each scene reads two phases. The other loops are not created.
    val primary: State<Float>
    val secondary: State<Float>
    when (theme) {
        FocusTheme.LAKE, FocusTheme.DAWN, FocusTheme.FOREST -> {
            primary = scenePhase(motion, 14_000, "slow")
            secondary = scenePhase(motion, 5_000, "mid")
        }
        FocusTheme.SPACE -> {
            primary = scenePhase(motion, 36_000, "drift")
            secondary = scenePhase(motion, 5_000, "mid")
        }
        FocusTheme.RAIN -> {
            primary = scenePhase(motion, 1_200, "fast")
            secondary = scenePhase(motion, 5_000, "mid")
        }
        FocusTheme.NORTHERN_LIGHTS -> {
            primary = scenePhase(motion, 16_000, "sway")
            secondary = scenePhase(motion, 5_000, "mid")
        }
        FocusTheme.CAMPFIRE -> {
            primary = scenePhase(motion, 3_200, "sparks")
            secondary = scenePhase(motion, 1_100, "flicker")
        }
    }
    // The fraction, not DrawScope.center. A draw lambda would hide this parameter.
    val anchor = center
    var size by remember { mutableStateOf(Size.Zero) }
    // Recomposition and a new draw lambda must not build the paths again.
    val scene = remember(theme, size, anchor) {
        if (size.width == 0f || size.height == 0f) null else sceneCache(theme, size, anchor)
    }
    Spacer(
        modifier
            .onSizeChanged { size = Size(it.width.toFloat(), it.height.toFloat()) }
            .drawBehind {
                scene?.draw(this, primary.value, secondary.value)
            },
    )
}

/** A still phase, or one endless ramp. The caller reads [State.value] while drawing. */
@Composable
private fun scenePhase(enabled: Boolean, periodMillis: Int, label: String): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0.35f) }
    return rememberInfiniteTransition(label = label).animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMillis, easing = LinearEasing)), label = label,
    )
}

private fun interface SceneDraw {
    fun draw(scope: DrawScope, primary: Float, secondary: Float)
}

/** A cheap repeatable random number in 0..1 for star and firefly positions. */
private fun hash(i: Int, salt: Int = 0): Float {
    val x = sin((i * 127.1f + salt * 311.7f)) * 43758.547f
    return x - kotlin.math.floor(x)
}

private class StarLayer(
    val x: FloatArray,
    val y: FloatArray,
    val radius: FloatArray,
    val phase: FloatArray,
    val gain: FloatArray,
) {
    fun draw(scope: DrawScope, twinkle: Float) {
        for (i in x.indices) {
            val tw = 0.4f + 0.6f * ((sin((twinkle + phase[i]) * 2 * PI) + 1) / 2).toFloat()
            scope.drawCircle(Color.White.copy(alpha = tw * gain[i]), radius[i], Offset(x[i], y[i]))
        }
    }
}

private fun starLayer(count: Int, width: Float, height: Float, top: Float, bottom: Float, alpha: Float): StarLayer {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val radius = FloatArray(count)
    val phase = FloatArray(count)
    val gain = FloatArray(count)
    for (i in 0 until count) {
        x[i] = hash(i, 1) * width
        y[i] = (top + hash(i, 2) * (bottom - top)) * height
        radius[i] = 1f + hash(i, 5) * 2.2f
        phase[i] = hash(i, 3)
        gain[i] = alpha * (0.5f + hash(i, 4) * 0.5f)
    }
    return StarLayer(x, y, radius, phase, gain)
}

private val RingStroke = Stroke(2.5f)

private fun sceneCache(theme: FocusTheme, size: Size, center: Offset): SceneDraw {
    val width = size.width
    val height = size.height
    return when (theme) {
        FocusTheme.LAKE -> lakeCache(width, height, center)
        FocusTheme.DAWN -> dawnCache(width, height)
        FocusTheme.FOREST -> forestCache(width, height)
        FocusTheme.SPACE -> spaceCache(width, height)
        FocusTheme.RAIN -> rainCache(width, height)
        FocusTheme.NORTHERN_LIGHTS -> northernLightsCache(width, height)
        FocusTheme.CAMPFIRE -> campfireCache(width, height)
    }
}

private fun lakeCache(width: Float, height: Float, center: Offset): SceneDraw {
    val sky = Brush.verticalGradient(listOf(Color(0xFF0E1033), Color(0xFF242C7A), Color(0xFF3D4BB8)))
    val stars = starLayer(60, width, height, 0f, 0.55f, 1f)
    val shore = shorePath(width, height)
    val shoreColor = Color(0xFF0B0D29).copy(alpha = 0.85f)
    val cx = center.x * width
    val cy = center.y * height
    return SceneDraw { scope, slow, mid ->
        scope.drawRect(sky)
        stars.draw(scope, slow * 3f)
        val c = Offset(cx, cy)
        repeat(5) { i ->
            val p = (mid + i / 5f) % 1f
            val r = width * (0.3f + p * 0.9f)
            scope.drawCircle(Color.White.copy(alpha = sin(p * PI).toFloat() * 0.16f), r, c, style = RingStroke)
        }
        scope.drawPath(shore, shoreColor)
    }
}

private fun shorePath(width: Float, height: Float) = Path().apply {
    moveTo(0f, height * 0.8f)
    repeat(9) { i ->
        val x = width * (i + 1) / 8f
        quadraticTo(x - width / 16f, height * (0.77f - hash(i, 9) * 0.04f), x, height * 0.8f)
    }
    lineTo(width, height)
    lineTo(0f, height)
    close()
}

private fun dawnCache(width: Float, height: Float): SceneDraw {
    val sky = Brush.verticalGradient(listOf(Color(0xFF3A2C6E), Color(0xFFB45A8C), Color(0xFFFFA77A), Color(0xFFFFD3A1)))
    val restY = height * 0.66f
    val sunX = width / 2f
    // The glow brush is tied to one center. Keep it at rest so the draw does not allocate a new brush.
    val glow = Brush.radialGradient(listOf(Color(0x66FFE7B3), Color.Transparent), Offset(sunX, restY), width * 0.6f)
    val stars = starLayer(14, width, height, 0f, 0.22f, 0.45f)
    val clouds = listOf(
        cloudPath(width * 0.39f) to Color(0xFFE9B6C6),
        cloudPath(width * 0.35f) to Color(0xFFF5C3CA),
        cloudPath(width * 0.32f) to Color(0xFFFFD5C1),
    )
    val hills = listOf(
        hillPath(width, height, 0.76f, 3) to Color(0xFFAD6A8B),
        hillPath(width, height, 0.83f, 4) to Color(0xFF805276),
        hillPath(width, height, 0.91f, 3) to Color(0xFF4A2F5E),
    )
    return SceneDraw { scope, slow, mid ->
        scope.drawRect(sky)
        val sunY = height * (0.66f - 0.015f * sin(slow * 2 * PI).toFloat())
        scope.drawCircle(glow, width * 0.6f, Offset(sunX, restY))
        scope.drawCircle(Color(0xFFFFE4A8), width * 0.16f, Offset(sunX, sunY))
        stars.draw(scope, mid)
        val drift = sin(slow * 2 * PI).toFloat() * width * 0.035f
        scope.drawCloud(clouds[0].first, Offset(-width * 0.08f + drift, height * 0.19f), clouds[0].second)
        scope.drawCloud(clouds[1].first, Offset(width * 0.72f - drift, height * 0.29f), clouds[1].second)
        scope.drawCloud(clouds[2].first, Offset(-width * 0.12f - drift, height * 0.53f), clouds[2].second)
        hills.forEach { (path, color) -> scope.drawPath(path, color) }
    }
}

/** One filled outline keeps the cloud lobes solid where they meet. The path origin is the cloud anchor. */
private fun cloudPath(w: Float) = Path().apply {
    moveTo(w * 0.14f, w * 0.25f)
    cubicTo(-w * 0.03f, w * 0.25f, -w * 0.04f, w * 0.04f, w * 0.16f, w * 0.03f)
    cubicTo(w * 0.18f, -w * 0.17f, w * 0.47f, -w * 0.20f, w * 0.54f, -w * 0.02f)
    cubicTo(w * 0.67f, -w * 0.13f, w * 0.84f, -w * 0.03f, w * 0.84f, w * 0.06f)
    cubicTo(w * 1.05f, w * 0.04f, w * 1.09f, w * 0.25f, w * 0.90f, w * 0.25f)
    close()
}

private fun DrawScope.drawCloud(path: Path, at: Offset, color: Color) {
    translate(at.x, at.y) { drawPath(path, color) }
}

private fun forestCache(width: Float, height: Float): SceneDraw {
    val sky = Brush.verticalGradient(listOf(Color(0xFF0B1F22), Color(0xFF123C35), Color(0xFF1C5A45)))
    val stars = starLayer(30, width, height, 0f, 0.35f, 0.7f)
    val moon = Color(0xFFEFF7D9).copy(alpha = 0.85f)
    val hills = listOf(
        hillPath(width, height, 0.62f, 3) to Color(0xFF0F3A30),
        hillPath(width, height, 0.72f, 5) to Color(0xFF0B2B24),
        hillPath(width, height, 0.82f, 7) to Color(0xFF071C18),
    )
    val flies = fireflies(width, height)
    val glowColor = Color(0xFFE4FF8A)
    val bodyColor = Color(0xFFF2FFB8)
    return SceneDraw { scope, slow, mid ->
        scope.drawRect(sky)
        stars.draw(scope, slow * 2f)
        scope.drawCircle(moon, width * 0.07f, Offset(width * 0.78f, height * 0.16f))
        hills.forEach { (path, color) -> scope.drawPath(path, color) }
        val xs = flies.x
        for (i in xs.indices) {
            val p = (mid + flies.phase[i]) % 1f
            val x = xs[i] + sin((p + i) * 2 * PI).toFloat() * 20f
            val y = flies.y[i] - p * 40f
            val glow = ((sin((p * 3 + flies.glow[i]) * 2 * PI) + 1) / 2).toFloat()
            val life = sin(p * PI).toFloat()
            val at = Offset(x, y)
            scope.drawCircle(glowColor.copy(alpha = 0.15f * glow * life), 10f, at)
            scope.drawCircle(bodyColor.copy(alpha = 0.9f * glow * life), 2.6f, at)
        }
    }
}

private class Fireflies(val x: FloatArray, val y: FloatArray, val phase: FloatArray, val glow: FloatArray)

private fun fireflies(width: Float, height: Float): Fireflies {
    val count = 24
    val x = FloatArray(count)
    val y = FloatArray(count)
    val phase = FloatArray(count)
    val glow = FloatArray(count)
    for (i in 0 until count) {
        x[i] = hash(i, 12) * width
        y[i] = height * (0.45f + hash(i, 13) * 0.5f)
        phase[i] = hash(i, 11)
        glow[i] = hash(i, 14)
    }
    return Fireflies(x, y, phase, glow)
}

private fun hillPath(width: Float, height: Float, level: Float, bumps: Int) = Path().apply {
    moveTo(0f, height * level)
    repeat(bumps) { i ->
        val x0 = width * i / bumps
        val x1 = width * (i + 1) / bumps
        quadraticTo((x0 + x1) / 2, height * (level - 0.06f - hash(i, bumps) * 0.05f), x1, height * level)
    }
    lineTo(width, height)
    lineTo(0f, height)
    close()
}

private class SpaceStars(
    val x: FloatArray,
    val base: FloatArray,
    val speed: FloatArray,
    val radius: FloatArray,
    val phase: FloatArray,
    val dim: FloatArray,
)

private fun spaceCache(width: Float, height: Float): SceneDraw {
    val sky = Brush.verticalGradient(listOf(Color(0xFF05060F), Color(0xFF141238), Color(0xFF2A1B4F)))
    val nebulaCenter = Offset(width * 0.2f, height * 0.3f)
    val nebula = Brush.radialGradient(listOf(Color(0x447C5CFF), Color.Transparent), nebulaCenter, width * 0.7f)
    val count = 70
    val x = FloatArray(count)
    val base = FloatArray(count)
    val speed = FloatArray(count)
    val radius = FloatArray(count)
    val phase = FloatArray(count)
    val dim = FloatArray(count)
    for (i in 0 until count) {
        val near = i % 3 == 0
        x[i] = hash(i, 22) * width
        base[i] = hash(i, 21)
        speed[i] = if (near) 2f else 1f
        radius[i] = if (near) 2.2f else 1.2f
        phase[i] = hash(i, 23)
        dim[i] = if (near) 1f else 0.4f
    }
    val stars = SpaceStars(x, base, speed, radius, phase, dim)
    val planet = Offset(width * 0.82f, height * 0.17f)
    val r = width * 0.115f
    val ringAt = planet - Offset(r * 1.65f, r * 0.34f)
    val ringSize = Size(r * 3.3f, r * 0.68f)
    val ringStroke = Stroke((r * 0.12f).coerceAtLeast(2f))
    val disc = Path().apply {
        addOval(Rect(planet - Offset(r, r), Size(r * 2, r * 2)))
    }
    return SceneDraw { scope, drift, mid ->
        scope.drawRect(sky)
        scope.drawCircle(nebula, width * 0.7f, nebulaCenter)
        val xs = stars.x
        for (i in xs.indices) {
            val y = ((stars.base[i] + drift * stars.speed[i]) % 1f) * height
            val tw = 0.5f + 0.5f * sin((mid + stars.phase[i]) * 2 * PI).toFloat()
            scope.drawCircle(
                Color.White.copy(alpha = 0.3f + 0.7f * tw * stars.dim[i]),
                stars.radius[i],
                Offset(xs[i], y),
            )
        }
        scope.rotate(-16f, planet) {
            drawOval(Color(0xFFB68CAA), ringAt, ringSize, style = ringStroke)
            drawCircle(Color(0xFFB6A4ED), r, planet)
            clipPath(disc) {
                drawOval(Color(0xFFD8B6ED), planet - Offset(r * 1.3f, r * 0.8f), Size(r * 2.6f, r * 0.65f))
                drawOval(Color(0xFF9589CE), planet - Offset(r * 1.3f, -r * 0.25f), Size(r * 2.6f, r * 0.7f))
            }
            drawArc(Color(0xFFFFD7AF), 0f, 180f, false, ringAt, ringSize, style = ringStroke)
        }
    }
}

private class RainField(
    val x: FloatArray,
    val len: FloatArray,
    val speed: FloatArray,
    val base: FloatArray,
    val stroke: FloatArray,
    val near: BooleanArray,
)

private class RippleField(val x: FloatArray, val y: FloatArray, val phase: FloatArray)

private fun rainCache(width: Float, height: Float): SceneDraw {
    val sky = Brush.verticalGradient(listOf(Color(0xFF1B293C), Color(0xFF354D65), Color(0xFF607E8E)))
    val cloudA = cloudPath(width * 0.48f)
    val cloudB = cloudPath(width * 0.41f)
    val cloudColorA = Color(0xFF35485E)
    val cloudColorB = Color(0xFF405970)
    val hillA = hillPath(width, height, 0.69f, 4)
    val hillB = hillPath(width, height, 0.73f, 3)
    val water = Brush.verticalGradient(listOf(Color(0xFF4D7284), Color(0xFF233C50)), startY = height * 0.74f)
    val waterTop = height * 0.74f
    val rippleStroke = Stroke(width * 0.002f)
    val rippleColor = Color(0xFFB6D4DC)
    val ripples = RippleField(FloatArray(12), FloatArray(12), FloatArray(12)).also { field ->
        for (i in 0 until 12) {
            field.x[i] = hash(i, 37) * width
            field.y[i] = height * (0.76f + hash(i, 38) * 0.22f)
            field.phase[i] = hash(i, 36)
        }
    }
    val drops = RainField(FloatArray(64), FloatArray(64), FloatArray(64), FloatArray(64), FloatArray(64), BooleanArray(64))
    for (i in 0 until 64) {
        val near = i % 3 == 0
        drops.near[i] = near
        drops.x[i] = hash(i, 31) * width
        drops.len[i] = width * if (near) 0.034f else 0.022f
        drops.speed[i] = if (near) 1f else 2f
        drops.base[i] = hash(i, 33)
        drops.stroke[i] = width * if (near) 0.003f else 0.0018f
    }
    val nearDrop = Color(0xFFCEE2EC).copy(alpha = 0.32f)
    val farDrop = Color(0xFFCEE2EC).copy(alpha = 0.15f)
    val reeds = Array(8) { index ->
        val side = index / 4
        val i = index % 4
        val x = width * if (side == 0) (0.01f + i * 0.018f) else (0.99f - i * 0.018f)
        val y = height * (0.92f + hash(i, 42) * 0.045f)
        val lean = width * if (side == 0) 0.018f else -0.018f
        Path().apply {
            moveTo(x, height)
            quadraticTo(x + lean, y + height * 0.025f, x + lean, y)
        }
    }
    val reedStroke = Stroke(width * 0.008f, cap = StrokeCap.Round)
    val reedColor = Color(0xFF172E3D)
    return SceneDraw { scope, fast, mid ->
        scope.drawRect(sky)
        val drift = sin(mid * 2 * PI).toFloat() * width * 0.008f
        scope.drawCloud(cloudA, Offset(-width * 0.12f + drift, height * 0.12f), cloudColorA)
        scope.drawCloud(cloudB, Offset(width * 0.71f - drift, height * 0.23f), cloudColorB)
        scope.drawPath(hillA, Color(0xFF354F5E))
        scope.drawPath(hillB, Color(0xFF293F4D))
        scope.drawRect(water, Offset(0f, waterTop), Size(width, height * 0.26f))
        for (i in ripples.x.indices) {
            val p = (mid + ripples.phase[i]) % 1f
            val radius = width * (0.018f + p * 0.055f)
            scope.drawOval(
                rippleColor.copy(alpha = sin(p * PI).toFloat() * 0.28f),
                Offset(ripples.x[i] - radius, ripples.y[i] - radius * 0.18f),
                Size(radius * 2, radius * 0.36f),
                style = rippleStroke,
            )
        }
        for (i in drops.x.indices) {
            val len = drops.len[i]
            val y = ((drops.base[i] + fast * drops.speed[i]) % 1f) * (height + len) - len
            val x = drops.x[i]
            scope.drawLine(
                if (drops.near[i]) nearDrop else farDrop,
                Offset(x, y),
                Offset(x - len * 0.16f, y + len),
                drops.stroke[i],
                StrokeCap.Round,
            )
        }
        for (reed in reeds) scope.drawPath(reed, reedColor, style = reedStroke)
    }
}

/** A wavy ribbon across the sky. [base] and [thickness] are fractions of the height. */
private fun auroraBand(width: Float, height: Float, base: Float, thickness: Float, waves: Int, salt: Int) = Path().apply {
    val steps = 12
    fun top(i: Int) = height * (base + 0.035f * sin((i.toFloat() / steps * waves + hash(salt)) * 2 * PI).toFloat())
    fun bottom(i: Int) = top(i) + height * thickness * (0.55f + 0.45f * hash(i, salt))
    val x = { i: Int -> -width * 0.15f + width * 1.3f * i / steps }
    moveTo(x(0), top(0))
    for (i in 1..steps) quadraticTo((x(i - 1) + x(i)) / 2, (top(i - 1) + top(i)) / 2 - height * 0.01f, x(i), top(i))
    lineTo(x(steps), bottom(steps))
    for (i in steps - 1 downTo 0) quadraticTo((x(i + 1) + x(i)) / 2, (bottom(i + 1) + bottom(i)) / 2 + height * 0.01f, x(i), bottom(i))
    close()
}

/** Small pine silhouettes along a ground line at [level], at fixed positions. */
private fun pines(width: Float, height: Float, level: Float, xs: List<Float>, scale: Float) = Path().apply {
    xs.forEachIndexed { i, fx ->
        val x = fx * width
        val h = height * scale * (0.75f + hash(i, 51) * 0.5f)
        val w = h * 0.42f
        val y = height * level
        moveTo(x, y - h); lineTo(x - w * 0.55f, y - h * 0.45f); lineTo(x + w * 0.55f, y - h * 0.45f); close()
        moveTo(x, y - h * 0.7f); lineTo(x - w, y); lineTo(x + w, y); close()
    }
}

private fun northernLightsCache(width: Float, height: Float): SceneDraw {
    val sky = Brush.verticalGradient(listOf(Color(0xFF040A1C), Color(0xFF0A1C38), Color(0xFF132F4E)))
    val stars = starLayer(45, width, height, 0f, 0.6f, 0.9f)
    // Three bands, drawn back to front. Each sways with its own offset of the same slow phase.
    val bands = listOf(
        Triple(auroraBand(width, height, 0.14f, 0.12f, 2, 61), Color(0xFF8C6BFF).copy(alpha = 0.22f), 0.0f),
        Triple(auroraBand(width, height, 0.22f, 0.10f, 3, 62), Color(0xFF3BE3A2).copy(alpha = 0.26f), 0.33f),
        Triple(auroraBand(width, height, 0.30f, 0.07f, 2, 63), Color(0xFF6FF0C4).copy(alpha = 0.2f), 0.66f),
    )
    val farHill = hillPath(width, height, 0.80f, 3)
    val nearHill = hillPath(width, height, 0.88f, 4)
    val snow = Color(0xFFB7C7DD)
    val trees = pines(width, height, 0.9f, listOf(0.06f, 0.12f, 0.19f, 0.78f, 0.85f, 0.93f), 0.07f)
    val ridge = height * 0.012f
    return SceneDraw { scope, sway, mid ->
        scope.drawRect(sky)
        stars.draw(scope, mid)
        for (i in bands.indices) {
            val (path, color, offset) = bands[i]
            val dx = sin((sway + offset) * 2 * PI).toFloat() * width * 0.04f
            scope.translate(dx, 0f) { drawPath(path, color) }
        }
        // A light copy above each hill reads as snow along the ridge.
        scope.drawPath(farHill, snow.copy(alpha = 0.35f))
        scope.translate(0f, ridge) { drawPath(farHill, Color(0xFF1B2B44)) }
        scope.drawPath(nearHill, snow.copy(alpha = 0.55f))
        scope.translate(0f, ridge) { drawPath(nearHill, Color(0xFF132036)) }
        scope.drawPath(trees, Color(0xFF0A1322))
    }
}

private class Sparks(val x: FloatArray, val phase: FloatArray, val drift: FloatArray)

private fun campfireCache(width: Float, height: Float): SceneDraw {
    val sky = Brush.verticalGradient(listOf(Color(0xFF0D1430), Color(0xFF1B2852), Color(0xFF2B3C6C)))
    val stars = starLayer(28, width, height, 0f, 0.45f, 0.7f)
    val ground = hillPath(width, height, 0.84f, 3)
    val fire = Offset(width * 0.27f, height * 0.88f)
    val glow = Brush.radialGradient(listOf(Color(0x4DFFB060), Color.Transparent), fire, width * 0.42f)
    val groundLight = Color(0xFFFFA24D).copy(alpha = 0.16f)
    val unit = width * 0.075f
    // The flame is drawn once at rest. The flicker only scales and tilts it around its base.
    val outer = Path().apply {
        moveTo(fire.x, fire.y - unit * 2.3f)
        cubicTo(fire.x + unit * 1.1f, fire.y - unit * 1.2f, fire.x + unit * 1.0f, fire.y - unit * 0.1f, fire.x, fire.y)
        cubicTo(fire.x - unit * 1.0f, fire.y - unit * 0.1f, fire.x - unit * 1.1f, fire.y - unit * 1.2f, fire.x, fire.y - unit * 2.3f)
        close()
    }
    val inner = Path().apply {
        moveTo(fire.x, fire.y - unit * 1.3f)
        cubicTo(fire.x + unit * 0.6f, fire.y - unit * 0.7f, fire.x + unit * 0.5f, fire.y, fire.x, fire.y)
        cubicTo(fire.x - unit * 0.5f, fire.y, fire.x - unit * 0.6f, fire.y - unit * 0.7f, fire.x, fire.y - unit * 1.3f)
        close()
    }
    val logStroke = unit * 0.38f
    val trees = pines(width, height, 0.86f, listOf(0.04f, 0.1f, 0.88f, 0.95f), 0.15f)
    val sparks = Sparks(FloatArray(10), FloatArray(10), FloatArray(10)).also {
        for (i in 0 until 10) {
            it.x[i] = fire.x + (hash(i, 71) - 0.5f) * unit * 1.2f
            it.phase[i] = hash(i, 72)
            it.drift[i] = (hash(i, 73) - 0.5f) * unit * 1.6f
        }
    }
    val spark = Color(0xFFFFC46B)
    return SceneDraw { scope, rise, flicker ->
        scope.drawRect(sky)
        stars.draw(scope, flicker * 0.2f)
        scope.drawPath(ground, Color(0xFF141B34))
        scope.drawCircle(glow, width * 0.42f, fire)
        scope.drawOval(groundLight, Offset(fire.x - width * 0.22f, fire.y - height * 0.012f), Size(width * 0.44f, height * 0.05f))
        scope.drawLine(Color(0xFF5A3A2A), Offset(fire.x - unit * 1.1f, fire.y + unit * 0.25f), Offset(fire.x + unit * 1.1f, fire.y - unit * 0.1f), logStroke, StrokeCap.Round)
        scope.drawLine(Color(0xFF4B2F23), Offset(fire.x - unit * 1.1f, fire.y - unit * 0.1f), Offset(fire.x + unit * 1.1f, fire.y + unit * 0.25f), logStroke, StrokeCap.Round)
        val wave = sin(flicker * 2 * PI).toFloat()
        scope.rotate(wave * 4f, fire) {
            scale(1f + wave * 0.04f, 1f + sin((flicker + 0.3f) * 2 * PI).toFloat() * 0.08f, fire) {
                drawPath(outer, Color(0xFFFF8A3D))
                drawPath(inner, Color(0xFFFFD36B))
            }
        }
        for (i in sparks.x.indices) {
            val p = (rise + sparks.phase[i]) % 1f
            val at = Offset(sparks.x[i] + sparks.drift[i] * p, fire.y - unit * 1.6f - p * height * 0.22f)
            scope.drawCircle(spark.copy(alpha = (1f - p) * 0.9f), unit * 0.09f, at)
        }
        scope.drawPath(trees, Color(0xFF0A1024))
    }
}
