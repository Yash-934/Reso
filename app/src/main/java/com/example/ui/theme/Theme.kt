package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClaudeDarkColorScheme = darkColorScheme(
    primary = ClaudeTerracottaDark,
    onPrimary = Color.White,
    primaryContainer = ClaudeTerracottaDarkSubtle,
    onPrimaryContainer = ClaudeTerracottaLight,
    secondary = ClaudeTerracotta,
    onSecondary = Color.White,
    secondaryContainer = ClaudeDarkSurfaceCard,
    onSecondaryContainer = ClaudeDarkPrimaryText,
    background = ClaudeDarkBackground,
    onBackground = ClaudeDarkPrimaryText,
    surface = ClaudeDarkSurface,
    onSurface = ClaudeDarkPrimaryText,
    surfaceVariant = ClaudeDarkSurfaceCard,
    onSurfaceVariant = ClaudeDarkSecondaryText,
    outline = ClaudeDarkBorder,
    error = ErrorRed,
    onError = Color.White
)

private val ClaudeLightColorScheme = lightColorScheme(
    primary = ClaudeTerracotta,
    onPrimary = Color.White,
    primaryContainer = ClaudeTerracottaSubtle,
    onPrimaryContainer = ClaudeTerracotta,
    secondary = ClaudeTerracottaLight,
    onSecondary = Color.White,
    secondaryContainer = ClaudeLightSurfaceCard,
    onSecondaryContainer = ClaudeLightPrimaryText,
    background = ClaudeLightBackground,
    onBackground = ClaudeLightPrimaryText,
    surface = ClaudeLightSurface,
    onSurface = ClaudeLightPrimaryText,
    surfaceVariant = ClaudeLightSurfaceCard,
    onSurfaceVariant = ClaudeLightSecondaryText,
    outline = ClaudeLightBorder,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Claude's signature aesthetic defaults to the iconic warm paper light theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ClaudeDarkColorScheme else ClaudeLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
