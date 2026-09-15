package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.util.bouncyClickable

/**
 * Бейдж текущей учебной группы студента по Design System МПК.
 *
 * ЖЕЛЕЗНОЕ ПРАВИЛО ВЕРСТКИ ДЛЯ УЗКИХ ЭКРАНОВ (HONOR X8D):
 * Номер группы НИКОГДА не переносится на две строки (цифры «41» и буква «О» всегда на одной строке):
 * softWrap = false, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.wrapContentWidth().
 */
@Composable
fun GroupBadge(
    groupName: String,
    modifier: Modifier = Modifier,
    isDarkHeader: Boolean = true,
    onGroupChanged: (String) -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }

    val bgColor = if (isDarkHeader) Color(0xFF001737) else Color(0xFFEDF2F7)
    val contentColor = if (isDarkHeader) Color.White else ColorBrandBlue
    val borderColor = if (isDarkHeader) Color(0xFF35393D) else ColorBorderLight

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = bgColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .wrapContentWidth()
            .bouncyClickable(onClick = { showDialog = true })
            .testTag("group_badge_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = groupName.ifBlank { "41О" },
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                ),
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Сменить группу",
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    if (showDialog) {
        GroupSelectionDialog(
            currentGroupName = groupName,
            onDismissRequest = { showDialog = false },
            onGroupSelected = { newGroup ->
                showDialog = false
                onGroupChanged(newGroup)
            }
        )
    }
}
