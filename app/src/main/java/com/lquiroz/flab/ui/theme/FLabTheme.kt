package com.lquiroz.flab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF090B0F)
val Surface = Color(0xFF12161D)
val SurfaceRaised = Color(0xFF1A202A)
val Cyan = Color(0xFF8CF6FF)
val Violet = Color(0xFFA993FF)
val TextPrimary = Color(0xFFF5F7FB)
val TextSecondary = Color(0xFF9DA8B8)

private val FLabColors = darkColorScheme(
    primary = Cyan,
    secondary = Violet,
    background = Ink,
    surface = Surface,
    surfaceVariant = SurfaceRaised,
    onPrimary = Ink,
    onSecondary = Ink,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
)

@Composable
fun FLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FLabColors,
        content = content,
    )
}
