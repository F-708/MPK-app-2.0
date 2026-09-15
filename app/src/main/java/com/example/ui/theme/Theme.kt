package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ColorActiveBlue,
    onPrimary = Color.White,
    primaryContainer = ColorBrandBlue,
    onPrimaryContainer = Color.White,
    secondary = ColorActiveBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = ColorMenuSubtext,
    tertiary = Color(0xFF38BDF8),
    onTertiary = ColorTopBar,
    background = ColorMenuBg,
    onBackground = ColorMenuText,
    surface = ColorMenuBg,
    onSurface = ColorMenuText,
    surfaceVariant = ColorMenuSubBg,
    onSurfaceVariant = ColorMenuSubtext,
    outline = ColorMenuBorder,
    outlineVariant = ColorMenuBorder,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

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

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Светлая тема активна по умолчанию
    dynamicColor: Boolean = false, // false для строгого сохранения институциональной палитры МПК
    content: @Composable () -> Unit
) {
    // Переключаем палитру дизайн-системы (все токены Color* — живые геттеры)
    androidx.compose.runtime.SideEffect {
        paletteState = if (darkTheme) DarkPalette else LightPalette
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
