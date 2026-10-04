package com.politex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {

    val scheme =
        lightColorScheme(
            primary = ColorBrandBlue,
            onPrimary = Color.White,
            primaryContainer = ColorSurfaceHighlight,
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
            onError = Color.White,
        )

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}
