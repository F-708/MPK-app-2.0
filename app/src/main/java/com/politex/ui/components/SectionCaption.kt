package com.politex.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.politex.ui.theme.ColorBrandBlue

/** R2-дубль12: общий заголовок секции (был SectionLabel в CollegeScreen и SectionHeader в SettingsScreen). */
@Composable
fun SectionCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = ColorBrandBlue,
            ),
        modifier = modifier,
    )
}
