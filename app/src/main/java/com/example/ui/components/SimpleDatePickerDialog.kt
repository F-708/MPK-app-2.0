package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorDividerLight
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.util.bouncyClickable
import java.util.Calendar
import java.util.Locale

@Composable
fun SimpleDatePickerDialog(
    initialDateIso: String?,
    minYear: Int,
    maxYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val today = remember { Calendar.getInstance() }

    val startYear = remember(initialDateIso) {
        initialDateIso?.split("-")?.getOrNull(0)?.toIntOrNull()
            ?: today.get(Calendar.YEAR)
    }
    val startMonth = remember(initialDateIso) {
        (initialDateIso?.split("-")?.getOrNull(1)?.toIntOrNull()?.minus(1))
            ?: today.get(Calendar.MONTH)
    }

    var year by remember { mutableIntStateOf(startYear.coerceIn(minYear, maxYear)) }
    var month by remember { mutableIntStateOf(startMonth.coerceIn(0, 11)) }
    var selected by remember { mutableStateOf(initialDateIso) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = ColorBgMain,
            border = BorderStroke(1.dp, ColorBorderLight),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArrowButton(
                        forward = false,
                        enabled = !(year <= minYear && month == 0),
                        onClick = { if (month == 0) { month = 11; year-- } else month-- }
                    )

                    Text(
                        text = MONTH_NAMES_NOMINATIVE[month] + " " + year,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ColorTextTitle
                        )
                    )

                    ArrowButton(
                        forward = true,
                        enabled = !(year >= maxYear && month == 11),
                        onClick = { if (month == 11) { month = 0; year++ } else month++ }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                MonthGrid(
                    year = year,
                    month = month,
                    selectedDateIso = selected,
                    onSelect = { selected = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorDividerLight)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionButton(text = "ОТМЕНА", filled = false, onClick = onDismiss)
                    Spacer(modifier = Modifier.width(8.dp))
                    ActionButton(
                        text = "ГОТОВО",
                        filled = true,
                        onClick = { selected?.let(onConfirm) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArrowButton(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .bouncyClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (forward) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = if (enabled) ColorBrandBlue else ColorTextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ActionButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (filled) ColorBrandFill else ColorBgMain,
        border = BorderStroke(1.dp, if (filled) ColorBrandFill else ColorBorderLight),
        modifier = Modifier.bouncyClickable(onClick = onClick)
    ) {
        Text(
            text = text,
            softWrap = false,
            maxLines = 1,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (filled) Color.White else ColorTextTitle
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

internal fun isoFromParts(year: Int, monthZeroBased: Int, day: Int): String =
    String.format(Locale.ROOT, "%04d-%02d-%02d", year, monthZeroBased + 1, day)
