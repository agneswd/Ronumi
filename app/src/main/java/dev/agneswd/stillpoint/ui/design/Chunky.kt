package dev.agneswd.stillpoint.ui.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ButtonKind { PRIMARY, ROSE, MINT, FLAME, DANGER, SECONDARY, GHOST }

private data class ButtonColors(val fill: Color, val lip: Color, val content: Color, val border: Color?)

@Composable
private fun colorsFor(kind: ButtonKind): ButtonColors {
    val c = Sp.colors
    return when (kind) {
        ButtonKind.PRIMARY -> ButtonColors(c.brand, c.brandLip, c.onFill, null)
        ButtonKind.ROSE -> ButtonColors(c.rose, c.roseLip, Color(0xFF262841), null)
        ButtonKind.MINT -> ButtonColors(c.mint, c.mintLip, Color(0xFF262841), null)
        ButtonKind.FLAME -> ButtonColors(c.flame, c.flameLip, Color(0xFF262841), null)
        ButtonKind.DANGER -> ButtonColors(c.danger, c.dangerLip, Color(0xFF262841), null)
        ButtonKind.SECONDARY -> ButtonColors(c.background, c.border, c.brand, c.border)
        ButtonKind.GHOST -> ButtonColors(Color.Transparent, Color.Transparent, c.brand, null)
    }
}

private val Lip = 5.dp

/**
 * The main button. It sits on a darker lip and sinks into it when pressed, like a game key.
 * Disabled buttons turn flat and grey. With an [icon] and empty [text] it is an icon key,
 * and the caller gives it a content description.
 */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    icon: Painter? = null,
    height: Dp = 54.dp,
    sound: Sound? = null,
) {
    val source = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val pressed by source.collectIsPressedAsState()
    val colors = if (enabled) colorsFor(kind) else ButtonColors(Sp.colors.surfaceHigh, Sp.colors.border, Sp.colors.textDim, null)
    val lip = if (kind == ButtonKind.GHOST) 0.dp else Lip
    val sink by animateDpAsState(if (pressed && enabled) lip else 0.dp, spring(stiffness = Spring.StiffnessHigh), label = "sink")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(height + lip)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                sound?.let(Sfx::play)
                onClick()
            },
        // The caller's width becomes the face's minimum width. Without one, the button fits its label.
        propagateMinConstraints = true,
    ) {
        if (lip > 0.dp) {
            Box(Modifier.matchParentSize().padding(top = lip).clip(shape).background(colors.lip))
        }
        Row(
            Modifier
                .height(height)
                .offset(y = sink)
                .clip(shape)
                .background(colors.fill)
                .then(if (colors.border != null) Modifier.drawBehind { drawRoundRect(colors.border, style = Stroke(2.dp.toPx()), cornerRadius = CornerRadius(16.dp.toPx())) } else Modifier)
                .padding(horizontal = if (text.isEmpty()) 0.dp else if (height < 50.dp) 10.dp else 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = colors.content, modifier = Modifier.size(if (text.isEmpty()) 24.dp else 22.dp))
                if (text.isNotEmpty()) Spacer(Modifier.width(10.dp))
            }
            // Long labels shrink to fit narrow buttons instead of being cut off.
            if (text.isNotEmpty()) {
                val style = MaterialTheme.typography.labelLarge
                BasicText(
                    text.uppercase(),
                    style = style.copy(color = colors.content, textAlign = TextAlign.Center),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize),
                )
            }
        }
    }
}

/**
 * A card with a border and a thick bottom edge, like the panels in learning games.
 * With [onClick] the card sinks when pressed. [selected] paints it in the brand color.
 */
@Composable
fun ChunkyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    fill: Color = Sp.colors.background,
    contentPadding: Dp = 16.dp,
    sound: Sound? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Sp.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val border = if (selected) c.brand else c.border
    val body = if (selected) c.brandSoft else fill
    val lipPx = 4.dp
    val sink by animateDpAsState(if (pressed) lipPx - 1.dp else 0.dp, spring(stiffness = Spring.StiffnessHigh), label = "cardSink")
    Box(
        modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(source, indication = null, role = Role.Button) {
                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        sound?.let(Sfx::play)
                        onClick()
                    }
                } else Modifier,
            )
            .padding(bottom = lipPx)
            .drawBehind {
                val r = CornerRadius(18.dp.toPx())
                val lip = lipPx.toPx()
                val down = sink.toPx()
                drawRoundRect(border, topLeft = Offset(0f, lip), size = Size(size.width, size.height), cornerRadius = r)
                drawRoundRect(body, topLeft = Offset(0f, down), size = size, cornerRadius = r)
                drawRoundRect(border, topLeft = Offset(0f, down), size = size, cornerRadius = r, style = Stroke(2.dp.toPx()))
            }
            .graphicsLayer { translationY = sink.toPx() }
            .padding(contentPadding),
        content = content,
    )
}

/** A thick rounded progress bar with a light shine along the top, as in learning games. */
@Composable
fun ChunkyProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = Sp.colors.brand, height: Dp = 16.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "progress")
    val track = Sp.colors.surfaceHigh
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                if (animated > 0f) {
                    val w = (size.width * animated).coerceAtLeast(size.height)
                    drawRoundRect(color, size = Size(w, size.height), cornerRadius = r)
                    val shine = size.height * 0.22f
                    drawRoundRect(
                        Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(size.height * 0.4f, size.height * 0.22f),
                        size = Size((w - size.height * 0.8f).coerceAtLeast(0f), shine),
                        cornerRadius = CornerRadius(shine / 2),
                    )
                }
            },
    )
}

/** A small bold label on a soft tint, for counts like "+20 XP". */
@Composable
fun Tag(text: String, color: Color = Sp.colors.brand, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Large page title used at the top of each tab. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineLarge, color = Sp.colors.text, modifier = modifier)
}
