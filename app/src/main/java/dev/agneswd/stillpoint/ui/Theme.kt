package dev.agneswd.stillpoint.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Ink and periwinkle with a dusk rose accent. The original app is green and orange.
private val Ink = Color(0xFF0E1018)
private val InkRaised = Color(0xFF161925)
private val InkHigh = Color(0xFF1F2333)
private val Periwinkle = Color(0xFFA3B0FF)
private val PeriwinkleDeep = Color(0xFF3F4FC4)
private val Rose = Color(0xFFFF9CB0)
private val RoseDeep = Color(0xFFB8355A)
private val Mist = Color(0xFFE6E8F2)
private val Paper = Color(0xFFF7F6FB)

private val DarkColors = darkColorScheme(
    primary = Periwinkle,
    onPrimary = Color(0xFF111640),
    primaryContainer = Color(0xFF2B3478),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Rose,
    onSecondary = Color(0xFF3F0A1C),
    secondaryContainer = Color(0xFF5C1D33),
    onSecondaryContainer = Color(0xFFFFD9E0),
    background = Ink,
    onBackground = Mist,
    surface = Ink,
    onSurface = Mist,
    surfaceVariant = InkHigh,
    onSurfaceVariant = Color(0xFF9EA3B8),
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = Color(0xFF292E42),
    outline = Color(0xFF454B63),
    outlineVariant = Color(0xFF2A2F42),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = PeriwinkleDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE2FF),
    onPrimaryContainer = Color(0xFF0B1557),
    secondary = RoseDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E0),
    onSecondaryContainer = Color(0xFF3F0A1C),
    background = Paper,
    onBackground = Color(0xFF1A1C24),
    surface = Paper,
    onSurface = Color(0xFF1A1C24),
    surfaceVariant = Color(0xFFE6E5F0),
    onSurfaceVariant = Color(0xFF5B5E70),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F0F8),
    surfaceContainer = Color(0xFFECEBF4),
    surfaceContainerHigh = Color(0xFFE6E5F0),
    surfaceContainerHighest = Color(0xFFE0DFEA),
    outline = Color(0xFF8D90A3),
    outlineVariant = Color(0xFFD3D3E0),
)

private val Type = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Big tabular numbers for timers and totals. */
val NumberStyle = TextStyle(fontWeight = FontWeight.Light, fontSize = 56.sp, letterSpacing = (-1.5).sp)

@Composable
fun StillpointTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Type,
        content = content,
    )
}
