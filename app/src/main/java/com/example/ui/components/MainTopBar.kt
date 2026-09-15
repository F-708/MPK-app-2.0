package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Шапка приложения «Мой Политех»: баннер колледжа + шестерёнка настроек.
 */
@Composable
fun MainTopBar(
    onSettingsClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    MainHeaderBanner(onSettingsClicked = onSettingsClicked, modifier = modifier)
}
