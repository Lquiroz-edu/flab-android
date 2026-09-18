package com.lquiroz.flab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * F/LAB's design tokens (DoD 40).
 *
 * The brief is minimal, high contrast, dark or light per the system, depth from material rather
 * than from decoration, and blur used sparingly. Colours are defined as a pair of schemes rather
 * than as a single dark palette with a light fallback, because "dark app that also has a light
 * mode" always shows.
 */
object FLabTokens {
    val InkDark = Color(0xFF07090D)
    val SurfaceDark = Color(0xFF11151C)
    val RaisedDark = Color(0xFF1A202A)
    val OutlineDark = Color(0x14FFFFFF)

    val InkLight = Color(0xFFF7F8FA)
    val SurfaceLight = Color(0xFFFFFFFF)
    val RaisedLight = Color(0xFFEFF2F6)
    val OutlineLight = Color(0x14000000)

    val Cyan = Color(0xFF3ED8E8)
    val CyanDeep = Color(0xFF0E7C8A)
    val Violet = Color(0xFF9B87F5)
    val VioletDeep = Color(0xFF5B45C4)
    val Amber = Color(0xFFE0A33C)
    val Green = Color(0xFF3FBF7F)
    val Red = Color(0xFFE05A5A)

    val TextPrimaryDark = Color(0xFFF2F5F9)
    val TextSecondaryDark = Color(0xFF97A2B2)
    val TextPrimaryLight = Color(0xFF0D1117)
    val TextSecondaryLight = Color(0xFF5B6675)

    /** Corner radii. One scale, used everywhere, so nothing looks borrowed. */
    val RadiusCard = 26.dp
    val RadiusControl = 18.dp
    val RadiusPill = 100.dp
}

/**
 * Semantic colours Material's scheme has no slot for.
 *
 * Kept in a CompositionLocal rather than passed down, so a card deep in a screen can reach the
 * same "this is a warning" colour the status header uses.
 */
data class FLabSemanticColors(
    val ok: Color,
    val warning: Color,
    val danger: Color,
    val outline: Color,
    val raised: Color,
    val textSecondary: Color,
    val isDark: Boolean,
)

private val LocalFLabColors = staticCompositionLocalOf {
    FLabSemanticColors(
        ok = FLabTokens.Green,
        warning = FLabTokens.Amber,
        danger = FLabTokens.Red,
        outline = FLabTokens.OutlineDark,
        raised = FLabTokens.RaisedDark,
        textSecondary = FLabTokens.TextSecondaryDark,
        isDark = true,
    )
}

val FLabColors: FLabSemanticColors
    @Composable @ReadOnlyComposable get() = LocalFLabColors.current

private val DarkScheme = darkColorScheme(
    primary = FLabTokens.Cyan,
    onPrimary = FLabTokens.InkDark,
    secondary = FLabTokens.Violet,
    onSecondary = FLabTokens.InkDark,
    background = FLabTokens.InkDark,
    onBackground = FLabTokens.TextPrimaryDark,
    surface = FLabTokens.SurfaceDark,
    onSurface = FLabTokens.TextPrimaryDark,
    surfaceVariant = FLabTokens.RaisedDark,
    onSurfaceVariant = FLabTokens.TextSecondaryDark,
    error = FLabTokens.Red,
)

private val LightScheme = lightColorScheme(
    primary = FLabTokens.CyanDeep,
    onPrimary = Color.White,
    secondary = FLabTokens.VioletDeep,
    onSecondary = Color.White,
    background = FLabTokens.InkLight,
    onBackground = FLabTokens.TextPrimaryLight,
    surface = FLabTokens.SurfaceLight,
    onSurface = FLabTokens.TextPrimaryLight,
    surfaceVariant = FLabTokens.RaisedLight,
    onSurfaceVariant = FLabTokens.TextSecondaryLight,
    error = FLabTokens.Red,
)

private val FLabTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp),
)

@Composable
fun FLabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val semantic = FLabSemanticColors(
        ok = FLabTokens.Green,
        warning = FLabTokens.Amber,
        danger = FLabTokens.Red,
        outline = if (darkTheme) FLabTokens.OutlineDark else FLabTokens.OutlineLight,
        raised = if (darkTheme) FLabTokens.RaisedDark else FLabTokens.RaisedLight,
        textSecondary = if (darkTheme) FLabTokens.TextSecondaryDark else FLabTokens.TextSecondaryLight,
        isDark = darkTheme,
    )
    CompositionLocalProvider(LocalFLabColors provides semantic) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = FLabTypography,
            content = content,
        )
    }
}
