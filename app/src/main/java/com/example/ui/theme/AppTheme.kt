package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Темы оформления приложения.
 *
 * Меняются только акцентные цвета — фон, текст, рамки и структура остаются
 * прежними, как на сайте колледжа. Поэтому «сменить тему» здесь означает
 * «сменить цвет», а не «переделать оформление»: экраны трогать не нужно,
 * они берут цвета из общей палитры.
 */
enum class AppTheme(val id: String, val title: String, val hint: String, val palette: MpkPalette) {
    COLLEGE(
        "college",
        "Фирменная",
        "Синие цвета колледжа — как на сайте",
        LightPalette
    ),
    EMERALD(
        "emerald",
        "Изумрудная",
        "Тёмно-зелёный акцент вместо синего",
        paletteWithAccents(
            topBar = Color(0xFF03291A),
            brand = Color(0xFF0B5D3B),
            active = Color(0xFF0E8A55)
        )
    ),
    BORDEAUX(
        "bordeaux",
        "Бордовая",
        "Тёмно-красный акцент",
        paletteWithAccents(
            topBar = Color(0xFF2B0A10),
            brand = Color(0xFF7B1225),
            active = Color(0xFFA81B33)
        )
    ),
    GRAPHITE(
        "graphite",
        "Графитовая",
        "Спокойная серая, без ярких цветов",
        paletteWithAccents(
            topBar = Color(0xFF1C1E20),
            brand = Color(0xFF37474F),
            active = Color(0xFF546E7A)
        )
    );

    companion object {
        fun from(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: COLLEGE
    }
}

/**
 * Та же светлая палитра, но с другими акцентами.
 *
 * Всё остальное (фон, текст, рамки, статусные цвета) берётся из фирменной
 * палитры как есть — так темы гарантированно остаются в стиле колледжа
 * и не ломают читаемость.
 */
private fun paletteWithAccents(topBar: Color, brand: Color, active: Color): MpkPalette =
    LightPalette.copy(
        topBar = topBar,
        brandBlue = brand,
        activeBlue = active,
        brandFill = brand,
        activeFill = active,
        // Дипломный бейдж исторически фирменного цвета — пусть следует за темой
        badgeDiploma = brand
    )

/** Применить тему ко всему приложению. Все экраны подхватят цвета сами. */
fun applyAppTheme(theme: AppTheme) {
    paletteState = theme.palette
}

/** Текущая тема — для отметки выбранного варианта в настройках. */
val currentAppTheme: AppTheme get() = AppTheme.entries.firstOrNull { it.palette == paletteState } ?: AppTheme.COLLEGE
