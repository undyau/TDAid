package com.undy.tdaid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Cream,
    primaryContainer = ForestTint,
    onPrimaryContainer = ForestDark,
    secondary = Accent,
    onSecondary = ForestDark,
    secondaryContainer = AccentTint,
    onSecondaryContainer = ForestDark,
    background = BgPaper,
    onBackground = Ink,
    surface = SurfaceColor,
    onSurface = Ink,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = Border,
    error = Accent,
)

private val DarkColors = darkColorScheme(
    primary = ForestTint,
    onPrimary = ForestDark,
    primaryContainer = Forest,
    onPrimaryContainer = Cream,
    secondary = Accent,
    onSecondary = ForestDark,
    background = Color(0xFF15241A),
    onBackground = Cream,
    surface = Color(0xFF1B2E21),
    onSurface = Cream,
    surfaceVariant = Color(0xFF223A29),
    onSurfaceVariant = Color(0xFFB7C4BA),
    outline = Color(0xFF3A4F40),
    error = Accent,
)

@Composable
fun TDAidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TDAidTypography,
        content = content,
    )
}
