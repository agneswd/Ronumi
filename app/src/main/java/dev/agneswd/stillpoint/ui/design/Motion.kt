package dev.agneswd.stillpoint.ui.design

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fades and springs an element in when it first shows. Give list items rising
 * [delayMillis] values for a staggered entrance.
 */
fun Modifier.appear(delayMillis: Int = 0, fromY: Float = 40f): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * fromY.dp.toPx() / 2
        val s = 0.92f + 0.08f * progress.value
        scaleX = s
        scaleY = s
    }
}

/** Pops an element in from nothing with an overshoot. Good for badges and rewards. */
fun Modifier.popIn(delayMillis: Int = 0): Modifier = composed {
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = scale.value.coerceIn(0f, 1f)
    }
}

/** A slow, endless grow and shrink, for things that ask to be tapped. */
fun Modifier.pulse(amount: Float = 0.05f, periodMillis: Int = 1400): Modifier = composed {
    val t by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMillis), RepeatMode.Reverse), label = "pulseValue",
    )
    graphicsLayer {
        val s = 1f + amount * t
        scaleX = s
        scaleY = s
    }
}

/** Counts a number up from zero, for XP and totals on reward screens. */
@Composable
fun CountUp(target: Int, style: TextStyle, color: Color, durationMillis: Int = 900, delayMillis: Int = 0, format: @Composable (Int) -> String = { androidx.compose.ui.res.stringResource(dev.agneswd.stillpoint.R.string.common_number, it) }) {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        value.animateTo(target.toFloat(), tween(durationMillis))
    }
    Text(format(value.value.toInt()), style = style, color = color)
}

private class Piece(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var angle: Float, val spin: Float, val color: Color, val w: Float, val h: Float,
)

/**
 * A burst of confetti from the top of its area. It runs once each time [key] changes,
 * then the pieces fall off the screen.
 */
@Composable
fun Confetti(key: Any, modifier: Modifier = Modifier.fillMaxSize(), colors: List<Color> = confettiColors()) {
    val pieces = remember(key) { mutableListOf<Piece>() }
    var frame by remember { mutableLongStateOf(0L) }
    var seeded by remember(key) { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(key) {
        var last = 0L
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else (now - last) / 1000f
            last = now
            pieces.forEach { p ->
                p.vy += 900f * dt
                p.vx *= 0.99f
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.angle += p.spin * dt
            }
            frame = now
            if (now - start > 4500) break
        }
    }
    Canvas(modifier) {
        if (!seeded) {
            seeded = true
            val r = Random(key.hashCode())
            repeat(120) {
                val a = (-PI / 2 + (r.nextFloat() - 0.5f) * PI * 0.9).toFloat()
                val speed = 700f + r.nextFloat() * 900f
                pieces += Piece(
                    x = size.width / 2 + (r.nextFloat() - 0.5f) * size.width * 0.3f,
                    y = size.height * 0.35f,
                    vx = cos(a) * speed,
                    vy = sin(a) * speed,
                    angle = r.nextFloat() * 360f,
                    spin = (r.nextFloat() - 0.5f) * 720f,
                    color = colors[r.nextInt(colors.size)],
                    w = 10f + r.nextFloat() * 10f,
                    h = 6f + r.nextFloat() * 8f,
                )
            }
        }
        frame // read so the canvas redraws every frame
        pieces.forEach { p ->
            if (p.y < size.height + 40f) {
                rotate(p.angle, Offset(p.x, p.y)) {
                    drawRect(p.color, Offset(p.x - p.w / 2, p.y - p.h / 2), Size(p.w, p.h))
                }
            }
        }
    }
}

@Composable
fun confettiColors(): List<Color> {
    val c = Sp.colors
    return listOf(c.brand, c.rose, c.flame, c.gold, c.mint)
}

/** An endless 0..1 ramp, for loops such as ripples and orbits. */
@Composable
fun loop(periodMillis: Int, label: String = "loop"): Float {
    val t by rememberInfiniteTransition(label = label).animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMillis, easing = LinearEasing)), label = "${label}Value",
    )
    return t
}

/**
 * True when the system animator duration scale is above 0.
 * A change to the setting updates this value.
 */
@Composable
fun systemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = ValueAnimator.areAnimatorsEnabled()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        enabled = ValueAnimator.areAnimatorsEnabled()
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return enabled
}
