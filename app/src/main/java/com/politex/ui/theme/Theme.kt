package com.politex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    // Схема строится здесь, а не в val верхнего уровня: палитра — mutableState
    // (`paletteState`), поэтому при смене темы схема пересобирается и компоненты
    // Material3 (RadioButton, Switch, OutlinedTextField) меняют цвет вместе с
    // приложением. Раньше val снимал палитру один раз при первом обращении и
    // навсегда оставался на теме, активной в момент запуска — отсюда «точка
    // выбора всегда зелёная» у заказчика с изумрудной темой.
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
