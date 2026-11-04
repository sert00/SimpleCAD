package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SimpleCadColorScheme = darkColorScheme(
    primary = CadCyanPrimary,
    onPrimary = Color(0xFF002630),
    primaryContainer = CadCyanContainer,
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = CadAmberSecondary,
    onSecondary = Color(0xFF2B1700),
    secondaryContainer = CadAmberContainer,
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = CadGreenAccent,
    background = CadNavyDark,
    onBackground = CadTextLight,
    surface = CadSurfaceDark,
    onSurface = CadTextLight,
    surfaceVariant = CadSurfaceVariant,
    onSurfaceVariant = CadTextMuted,
    outline = Color(0xFF2E4361)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SimpleCadColorScheme,
        typography = Typography,
        content = content
    )
}
