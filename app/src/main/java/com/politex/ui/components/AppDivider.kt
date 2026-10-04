package com.politex.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.politex.ui.theme.ColorDividerLight

/** R4 §10: разделитель вместо Box(height 1dp, background ColorDividerLight). */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = ColorDividerLight)
}
