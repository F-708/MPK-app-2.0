package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Шапка приложения «Мой Политех» — баннер колледжа на всю ширину.
 * Дополнительных кнопок нет: настройки живут во вкладке «Другое».
 */
@Composable
fun MainTopBar(
    modifier: Modifier = Modifier
) {
    MainHeaderBanner(modifier = modifier)
}
