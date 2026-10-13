package com.politex.ui.teacher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.politex.util.UiScale
import com.politex.util.UiScaleStore

@Composable
fun ScaledDensityProvider(
    scale: UiScale,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale.factor, base.fontScale),
        content = content,
    )
}

@Composable
fun rememberUiScale(): UiScale {
    val context = LocalContext.current
    return UiScaleStore.load(context)
}
