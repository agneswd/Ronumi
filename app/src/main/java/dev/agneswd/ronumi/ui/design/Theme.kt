package dev.agneswd.ronumi.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.agneswd.ronumi.R

/**
 * The Stillpoint palette. Bright and playful like a game, with periwinkle as the brand color.
 * Each fill color has a darker "lip" for the 3D edge under buttons and cards.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val text: Color,
    val textDim: Color,
    val brand: Color,
    val brandLip: Color,
    val brandSoft: Color,
    val rose: Color,
    val roseLip: Color,
    val flame: Color,
    val flameLip: Color,
    val gold: Color,
    val goldLip: Color,
    val mint: Color,
    val mintLip: Color,
    val danger: Color,
    val dangerLip: Color,
    val onFill: Color,
)

val LightPalette = Palette(
    dark = false,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5FC),
    surfaceHigh = Color(0xFFECECF8),
    border = Color(0xFFE3E4F0),
    text = Color(0xFF262841),
    textDim = Color(0xFF626780),
    brand = Color(0xFF5362D6),
    brandLip = Color(0xFF4C59D6),
    brandSoft = Color(0xFFE6E9FF),
    rose = Color(0xFFFF6F91),
    roseLip = Color(0xFFD94C70),
    flame = Color(0xFFFF9F1C),
    flameLip = Color(0xFFD97E00),
    gold = Color(0xFFFFC53D),
    goldLip = Color(0xFFDDA000),
    mint = Color(0xFF34D399),
    mintLip = Color(0xFF1FA874),
    danger = Color(0xFFFF5A5F),
    dangerLip = Color(0xFFD63A40),
    onFill = Color.White,
)

val DarkPalette = Palette(
    dark = true,
    background = Color(0xFF12131C),
    surface = Color(0xFF1C1E2B),
    surfaceHigh = Color(0xFF262939),
    border = Color(0xFF42465F),
    text = Color(0xFFF1F2FA),
    textDim = Color(0xFFB2B7D0),
    brand = Color(0xFF8391FF),
    brandLip = Color(0xFF5865E0),
    brandSoft = Color(0xFF262B55),
    rose = Color(0xFFFF7C9C),
    roseLip = Color(0xFFD9577A),
    flame = Color(0xFFFFA62E),
    flameLip = Color(0xFFD98200),
    gold = Color(0xFFFFCB4F),
    goldLip = Color(0xFFD9A300),
    mint = Color(0xFF3FDDA2),
    mintLip = Color(0xFF22A97A),
    danger = Color(0xFFFF6B70),
    dangerLip = Color(0xFFD9474D),
    onFill = Color(0xFF171A30),
)

private val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Shortcut for the current palette, like `Sp.colors.brand`. */
object Sp {
    val colors: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun nunito(weight: Int) = Font(
    R.font.nunito,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Nunito = FontFamily(
    nunito(400), nunito(500), nunito(600), nunito(700), nunito(800), nunito(900),
)

private val Type = Typography().let { base ->
    fun TextStyle.n(weight: Int, size: Int? = null, spacing: Float? = null) = copy(
        fontFamily = Nunito,
        fontWeight = FontWeight(weight),
        fontSize = size?.sp ?: fontSize,
        letterSpacing = spacing?.sp ?: letterSpacing,
    )
    base.copy(
        displayLarge = base.displayLarge.n(900, 60, -1.5f),
        displayMedium = base.displayMedium.n(900, 44, -1f),
        displaySmall = base.displaySmall.n(900, 34, -0.5f),
        headlineLarge = base.headlineLarge.n(900, 30),
        headlineMedium = base.headlineMedium.n(800, 26),
        headlineSmall = base.headlineSmall.n(800, 22),
        titleLarge = base.titleLarge.n(800, 20),
        titleMedium = base.titleMedium.n(800, 17),
        titleSmall = base.titleSmall.n(800, 15),
        bodyLarge = base.bodyLarge.n(600, 17),
        bodyMedium = base.bodyMedium.n(600, 15),
        bodySmall = base.bodySmall.n(600, 13),
        labelLarge = base.labelLarge.n(900, 16, 0.6f),
        labelMedium = base.labelMedium.n(800, 13, 0.4f),
        labelSmall = base.labelSmall.n(800, 11, 0.4f),
    )
}

/** Large rounded numbers for timers and totals. */
val NumberStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 64.sp, letterSpacing = (-2).sp)

@Composable
fun StillpointTheme(themeMode: String = "SYSTEM", content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> systemDark
    }
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (palette.dark) {
        darkColorScheme(
            primary = palette.brand, onPrimary = palette.onFill, secondary = palette.rose, onSecondary = palette.onFill,
            background = palette.background, onBackground = palette.text, surface = palette.background, onSurface = palette.text,
            surfaceVariant = palette.surfaceHigh, onSurfaceVariant = palette.textDim, outline = palette.border, outlineVariant = palette.border,
            surfaceContainerLowest = palette.background, surfaceContainerLow = palette.surface, surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceHigh, surfaceContainerHighest = palette.surfaceHigh, error = palette.danger, onError = Color(0xFF171A30),
            tertiary = palette.mint, onTertiary = Color(0xFF171A30),
            inverseSurface = palette.text, inverseOnSurface = palette.background,
            primaryContainer = palette.brandSoft, onPrimaryContainer = palette.text, secondaryContainer = palette.brandSoft, onSecondaryContainer = palette.brand,
        )
    } else {
        lightColorScheme(
            primary = palette.brand, onPrimary = palette.onFill, secondary = palette.rose, onSecondary = palette.onFill,
            background = palette.background, onBackground = palette.text, surface = palette.background, onSurface = palette.text,
            surfaceVariant = palette.surfaceHigh, onSurfaceVariant = palette.textDim, outline = palette.border, outlineVariant = palette.border,
            surfaceContainerLowest = palette.background, surfaceContainerLow = palette.surface, surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceHigh, surfaceContainerHighest = palette.surfaceHigh, error = palette.danger, onError = Color(0xFF171A30),
            tertiary = palette.mint, onTertiary = Color(0xFF171A30),
            inverseSurface = palette.text, inverseOnSurface = palette.background,
            primaryContainer = palette.brandSoft, onPrimaryContainer = palette.text, secondaryContainer = palette.brandSoft, onSecondaryContainer = palette.brand,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}
