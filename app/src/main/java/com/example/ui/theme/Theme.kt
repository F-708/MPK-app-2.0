package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ColorBrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = ColorBrandBlue,
    secondary = ColorActiveBlue,
    onSecondary = Color.White,
    secondaryContainer = ColorSurfaceHighlight,
    onSecondaryContainer = ColorBrandBlue,
    tertiary = ColorTopBar,
    onTertiary = Color.White,
    background = ColorBgMain,
    onBackground = ColorTextBody,
    surface = ColorBgMain,
    onSurface = ColorTextTitle,
    surfaceVariant = ColorSurfaceVariantLight,
    onSurfaceVariant = ColorTextMuted,
    outline = ColorBorderLight,
    outlineVariant = ColorDividerLight,
    error = ColorError,
    onError = Color.White
)

/**
 * Единая светлая тема приложения «Мой Политех».
 * Тёмная тема удалена по требованию — приложение всегда светлое.
 */
@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
