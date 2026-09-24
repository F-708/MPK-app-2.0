package com.politex.ui.theme

import androidx.compose.ui.graphics.Color

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
    ),

    // Б12: добавлены по выбору заказчика (2026-09-24). Все три — светлые, как и
    // предыдущие: в приложении палитра целиком светлая (белый фон, тёмный текст),
    // поэтому тёмная тема потребовала бы второго набора цветов во всей MpkPalette.
    INDIGO(
        "indigo",
        "Индиго",
        "Насыщенный сине-фиолетовый акцент",
        paletteWithAccents(
            topBar = Color(0xFF1E1B4B),
            // brand заливает кнопки под белым текстом — тёмный, чтобы текст читался;
            // active — только рамки и подписи, ему хватает среднего тона
            brand = Color(0xFF3730A3),
            active = Color(0xFF4F46E5)
        )
    ),
    AMBER(
        "amber",
        "Янтарная",
        "Тёплая коричнево-золотая гамма",
        paletteWithAccents(
            topBar = Color(0xFF3E2723),
            brand = Color(0xFF92400E),
            active = Color(0xFFB45309)
        )
    ),
    STEEL(
        "steel",
        "Стальная",
        "Светло-голубая, легче фирменной",
        paletteWithAccents(
            topBar = Color(0xFF0C4A6E),
            brand = Color(0xFF075985),
            active = Color(0xFF0369A1)
        )
    );

    companion object {
        fun from(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: COLLEGE
    }
}

private fun paletteWithAccents(topBar: Color, brand: Color, active: Color): MpkPalette =
    LightPalette.copy(
        topBar = topBar,
        brandBlue = brand,
        activeBlue = active,
        brandFill = brand,
        activeFill = active,

        badgeDiploma = brand
    )

fun applyAppTheme(theme: AppTheme) {
    paletteState = theme.palette
}

val currentAppTheme: AppTheme get() = AppTheme.entries.firstOrNull { it.palette == paletteState } ?: AppTheme.COLLEGE
