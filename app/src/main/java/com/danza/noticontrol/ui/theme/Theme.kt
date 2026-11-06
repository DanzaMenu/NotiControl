package com.danza.noticontrol.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GlassWhite = Color(0xCCFFFFFF)
val GlassWhiteDim = Color(0x33FFFFFF)
val GlassBorder = Color(0x55FFFFFF)
val GlassBackground = Color(0x1AFFFFFF)
val AccentBlue = Color(0xFF4FC3F7)
val AccentPurple = Color(0xFFCE93D8)
val DeepBackground = Color(0xFF050A14)
val SurfaceColor = Color(0xFF0D1A2E)
val DangerRed = Color(0xFFEF5350)

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentPurple,
    background = DeepBackground,
    surface = SurfaceColor,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun NotiControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
