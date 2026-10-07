package dev.agneswd.ronumi.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.agneswd.ronumi.R
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.Settings
import dev.agneswd.ronumi.game.GameState
import dev.agneswd.ronumi.ui.design.ButtonKind
import dev.agneswd.ronumi.ui.design.ChunkyButton
import dev.agneswd.ronumi.ui.design.Flame
import dev.agneswd.ronumi.ui.design.LightPalette
import dev.agneswd.ronumi.ui.design.Mood
import dev.agneswd.ronumi.ui.design.Ronumi
import dev.agneswd.ronumi.ui.design.Sfx
import dev.agneswd.ronumi.ui.design.Sound
import dev.agneswd.ronumi.ui.design.Sp
import dev.agneswd.ronumi.ui.design.ThoughtDot
import kotlinx.coroutines.delay

val ScreenPadding = 20.dp

/** Saves a change to the settings row. */
typealias SettingsUpdate = ((Settings) -> Settings) -> Unit

/** A section title with space above it. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = ScreenPadding, end = ScreenPadding, top = 36.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Sp.colors.text, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** A full-width row with a title, an optional subtitle and an optional end slot. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick) else Modifier)
            .padding(horizontal = ScreenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Sp.colors.textDim)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, leading: (@Composable () -> Unit)? = null, onChange: (Boolean) -> Unit) {
    val toggle: (Boolean) -> Unit = { on ->
        onChange(on)
    }
    ListRow(title, subtitle, onClick = { toggle(!checked) }, leading = leading) {
        Switch(
            checked = checked,
            onCheckedChange = toggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Sp.colors.mint,
                checkedThumbColor = LightPalette.text,
                uncheckedTrackColor = Sp.colors.surfaceHigh,
                uncheckedBorderColor = Sp.colors.border,
                uncheckedThumbColor = Sp.colors.textDim,
            ),
        )
    }
}

/**
 * The app's launcher icon. Apps that are not installed show [logo] when there is one,
 * else the first letter of [name] or of their label.
 */
@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp, name: String? = null, logo: Int? = null) {
    val context = LocalContext.current
    val bitmap = remember(packageName) { context.app.catalog.icon(packageName) }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), null, Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else if (logo != null) {
        Image(painterResource(logo), null, Modifier.size(size))
    } else {
        val label = name ?: remember(packageName) { context.app.catalog.label(packageName) }
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Sp.colors.brandSoft), contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase(androidx.compose.ui.platform.LocalLocale.current.platformLocale), style = MaterialTheme.typography.titleMedium, color = Sp.colors.text)
        }
    }
}

/** A colored rounded square with an icon, for setting rows. */
@Composable
fun IconTile(icon: Int, color: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** A value with minus and plus buttons. */
@Composable
fun Stepper(label: String, value: Int, range: IntRange, step: Int, format: @Composable (Int) -> String, onChange: (Int) -> Unit) {
    ListRow(label) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundKey(R.drawable.ic_minus, stringResource(R.string.common_decrease, label), enabled = value > range.first) { onChange((value - step).coerceIn(range)) }
            Text(format(value), style = MaterialTheme.typography.titleMedium, color = Sp.colors.text, modifier = Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center)
            RoundKey(R.drawable.ic_plus, stringResource(R.string.common_increase, label), enabled = value < range.last) { onChange((value + step).coerceIn(range)) }
        }
    }
}

@Composable
private fun RoundKey(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Sp.colors.surface)
            .border(2.dp, Sp.colors.border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = if (enabled) Sp.colors.brand else Sp.colors.border, modifier = Modifier.size(20.dp))
    }
}

/**
 * Ronumi with a speech bubble. New text types itself out, like a chat message.
 * [side] puts the bubble to the right of Ronumi; otherwise it sits above.
 */
@Composable
fun RonumiSays(
    text: String,
    mood: Mood,
    modifier: Modifier = Modifier,
    side: Boolean = true,
    ronumiSize: Dp = 92.dp,
    onMascotClick: (() -> Unit)? = null,
    mascotDescription: String? = null,
) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        while (shown < text.length) {
            delay(18)
            shown++
        }
    }
    val bubble: @Composable () -> Unit = {
        // Screen readers get the whole line once, not the hidden sizing copy and each typed letter.
        Box(Modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
            // The full text keeps the bubble size steady while the words type out.
            Text(text, style = MaterialTheme.typography.titleMedium, color = Color.Transparent, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            Text(
                text.take(shown),
                style = MaterialTheme.typography.titleMedium,
                color = Sp.colors.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
    val bubbleShape = RoundedCornerShape(18.dp)
    if (side) {
        val thinking = mood == Mood.THINK
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            MascotTap(mood, ronumiSize, thoughtDots = !thinking, onMascotClick, mascotDescription)
            if (thinking) ThoughtTrail() else Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).border(2.dp, Sp.colors.border, bubbleShape)) { bubble() }
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(horizontal = 24.dp).border(2.dp, Sp.colors.border, bubbleShape)) { bubble() }
            Spacer(Modifier.height(14.dp))
            MascotTap(mood, ronumiSize, thoughtDots = true, onMascotClick, mascotDescription)
        }
    }
}

/**
 * Ronumi, with an optional tap target over the drawing.
 * The drawing already pets Ronumi. The cover is used when a screen needs a different tap, such as Home opening the wardrobe.
 */
@Composable
private fun MascotTap(mood: Mood, size: Dp, thoughtDots: Boolean, onClick: (() -> Unit)?, description: String?) {
    if (onClick == null) {
        Ronumi(mood, size = size, thoughtDots = thoughtDots)
        return
    }
    val label = description.orEmpty()
    Box(
        Modifier.clearAndSetSemantics {
            role = Role.Button
            contentDescription = label
            onClick(label = label) { onClick(); true }
        },
    ) {
        Ronumi(mood, size = size, thoughtDots = thoughtDots)
        Box(
            Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        )
    }
}

/** Three dots that grow from Ronumi toward the middle of the bubble on its right. */
@Composable
private fun ThoughtTrail() {
    Canvas(Modifier.size(20.dp, 24.dp)) {
        val u = size.width / 20f
        listOf(Offset(3f, 16.5f) to 1.9f, Offset(9.5f, 13.8f) to 2.7f, Offset(16.5f, 12f) to 3.4f).forEach { (center, radius) ->
            drawCircle(ThoughtDot, radius * u, Offset(center.x * u, center.y * u))
        }
    }
}

/** The bar at the top of the main tabs: streak on the left, level on the right. XP stays on Progress. */
@Composable
fun GameBar(game: GameState?, modifier: Modifier = Modifier, onOpen: () -> Unit = {}) {
    val c = Sp.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = ScreenPadding - 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Stat(onOpen) {
            Flame(size = 26.dp, lit = (game?.streak ?: 0) > 0 || game?.streakSafeToday == true)
            Counter(game?.streak ?: 0, if ((game?.streak ?: 0) > 0 || game?.streakSafeToday == true) c.text else c.textDim)
        }
        Spacer(Modifier.weight(1f))
        Stat(onOpen) {
            Text(stringResource(R.string.common_level), style = MaterialTheme.typography.titleSmall, color = c.textDim)
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(c.brand), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.common_number, game?.level?.number ?: 1), style = MaterialTheme.typography.labelMedium, color = c.onFill)
            }
        }
    }
}

@Composable
private fun Stat(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** A number that pops when it changes. */
@Composable
private fun Counter(value: Int, color: Color) {
    AnimatedContent(
        value,
        transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) + fadeIn()) togetherWith fadeOut() },
        label = "counter",
    ) { v ->
        Text(stringResource(R.string.common_number, v), style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/** The top bar of full-screen editors: close on the left, an optional action on the right. */
@Composable
fun TopBar(title: String, onClose: () -> Unit, action: String? = null, actionEnabled: Boolean = true, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.common_close), tint = Sp.colors.textDim, modifier = Modifier.size(22.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Sp.colors.text, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        if (action != null) {
            Text(
                action.uppercase(androidx.compose.ui.platform.LocalLocale.current.platformLocale),
                style = MaterialTheme.typography.labelLarge,
                color = if (actionEnabled) Sp.colors.brand else Sp.colors.border,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = actionEnabled, onClick = onAction).padding(12.dp),
            )
        }
    }
}

/** A thin share bar, for app usage rows. */
@Composable
fun ShareBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Sp.colors.brand) {
    val shape = RoundedCornerShape(4.dp)
    Box(modifier.fillMaxWidth().height(8.dp).clip(shape).background(Sp.colors.surfaceHigh)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(8.dp).clip(shape).background(color))
    }
}

@Composable
fun appCount(count: Int): String = pluralStringResource(R.plurals.common_app_count, count, count)

/** A button in a row of choices. The chosen one is filled. */
@Composable
fun ChoiceButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    shrinkLabel: Boolean = true,
    horizontalPadding: Dp = 10.dp,
    onClick: () -> Unit,
) {
    ChunkyButton(
        text,
        onClick,
        modifier,
        kind = if (selected) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
        height = 46.dp,
        shrinkLabel = shrinkLabel,
        horizontalPadding = horizontalPadding,
    )
}

/**
 * Equal choice buttons on one line when every label fits at the style size.
 * When they do not fit, the buttons wrap with 8 dp gaps. A wrapped button is at least 64 dp wide.
 * Labels stay at [labelLarge] size.
 */
@Composable
fun ChoiceRow(options: List<Pair<String, Boolean>>, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val locale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labels = options.map { it.first.uppercase(locale) }
    val textWidths = labels.map { measurer.measure(it, style = style, maxLines = 1).size.width }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 8.dp
        val count = options.size
        val rowWidth = maxWidth
        val equal = if (count == 0) rowWidth else (rowWidth - gap * (count - 1).coerceAtLeast(0)) / count
        val widest = textWidths.maxOfOrNull { with(density) { it.toDp() } } ?: 0.dp
        // Room left for padding when every button has an equal share of the row.
        val side = if (count == 0) 0.dp else (equal - widest) / 2
        // At font scale 1.0 a dialog row can be a few dp short of the usual 10 dp padding.
        // Keep one line with tighter padding instead of wrapping. Larger text wraps.
        val oneLine = count > 0 && side >= 4.dp && (side >= 8.dp || density.fontScale <= 1.05f)
        if (oneLine) {
            val pad = side.coerceIn(4.dp, 10.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                options.forEachIndexed { index, (label, selected) ->
                    ChoiceButton(label, selected, Modifier.weight(1f), shrinkLabel = false, horizontalPadding = pad) { onSelect(index) }
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                options.forEachIndexed { index, (label, selected) ->
                    val textWidth = with(density) { textWidths[index].toDp() }
                    val width = (textWidth + 20.dp).coerceAtLeast(64.dp).coerceAtMost(rowWidth)
                    ChoiceButton(label, selected, Modifier.width(width), shrinkLabel = false) { onSelect(index) }
                }
            }
        }
    }
}
