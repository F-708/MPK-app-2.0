package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BellItem
import com.example.data.model.BellScheduleType
import com.example.data.model.CollegeBellSchedule
import com.example.ui.theme.ColorActiveBlue
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorDividerLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.theme.ColorTopBar
import com.example.ui.theme.TextStylePageTitle
import com.example.ui.util.bouncyClickable
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * Экран расписания звонков колледжа МГПК на 2026 год по официальному Style Guide.
 */
@Composable
fun BellsScreen(
    modifier: Modifier = Modifier
) {
    // Определение текущего дня недели и минут от начала суток
    val initialDayType = remember {
        val cal = Calendar.getInstance()
        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 1
        }
        CollegeBellSchedule.getTypeForDay(dayOfWeek)
    }

    var selectedScheduleType by remember { mutableStateOf(initialDayType) }

    var currentTimeMinutes by remember {
        val cal = Calendar.getInstance()
        mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
    }

    // Фоновое обновление времени каждую минуту
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val cal = Calendar.getInstance()
            currentTimeMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }
    }

    val activeSlot = remember(currentTimeMinutes, selectedScheduleType) {
        val bells = CollegeBellSchedule.getBellsForType(selectedScheduleType)
        bells.firstOrNull { currentTimeMinutes in it.startMinutes..it.endMinutes }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        // Заголовок страницы
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Расписание звонков",
                style = TextStylePageTitle,
                maxLines = 1
            )
            Text(
                text = "Основное расписание пар колледжа",
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = ColorBrandBlue
                )
            )
        }

        // Переключатель графиков звонков
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BellScheduleType.entries.forEach { type ->
                val isSelected = selectedScheduleType == type
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                    color = if (isSelected) ColorTopBar else ColorBgMain,
                    modifier = Modifier
                        .weight(1f)
                        .bouncyClickable { selectedScheduleType = type }
                ) {
                    Text(
                        text = when (type) {
                            BellScheduleType.STANDARD -> "ПН - ПТ"
                            BellScheduleType.THURSDAY -> "ЧЕТВЕРГ"
                            BellScheduleType.SATURDAY -> "СУББОТА"
                        },
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White else ColorTextBody
                        ),
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(1.dp)
                .background(ColorDividerLight)
        )

        AnimatedContent(
            targetState = selectedScheduleType,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "BellsTypeTransition"
        ) { targetType ->
            val bells = CollegeBellSchedule.getBellsForType(targetType)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Баннер-дисклеймер
                item {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bells_disclaimer_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ColorBrandBlue,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = targetType.title.uppercase(),
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = ColorBrandBlue
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = CollegeBellSchedule.DISCLAIMER,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorTextMuted,
                                        lineHeight = 15.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Индикатор текущего активного урока / инфочаса
                item {
                    activeSlot?.let { currentSlot ->
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = Color(0xFFEDF2F7),
                            border = BorderStroke(1.dp, ColorActiveBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(ColorActiveBlue, RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (currentSlot.isInfoHour) Icons.Default.Campaign else Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (currentSlot.isInfoHour) "СЕЙЧАС: ИНФОРМАЦИОННЫЙ ЧАС" else "СЕЙЧАС: ${currentSlot.title.uppercase()}",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = ColorBrandBlue
                                        )
                                    )
                                    Text(
                                        text = currentSlot.displayRange,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 11.sp,
                                            color = ColorTextBody
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Список уроков
                items(bells) { item ->
                    val isActive = activeSlot == item

                    if (item.isInfoHour) {
                        InfoHourCard(
                            item = item,
                            isActive = isActive
                        )
                    } else {
                        LessonBellCard(
                            item = item,
                            isActive = isActive
                        )
                    }
                }
            }
        }
    }
}

/**
 * Карточка урока с указанием времени, длительности и перемены по Style Guide.
 */
@Composable
private fun LessonBellCard(
    item: BellItem,
    isActive: Boolean
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, if (isActive) ColorActiveBlue else ColorBorderLight),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = if (isActive) ColorActiveBlue else ColorBrandBlue,
                    contentColor = Color.White,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${item.lessonNumber}",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = item.title,
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isActive) ColorActiveBlue else ColorTextTitle
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "45 мин",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 11.sp,
                                color = ColorTextMuted
                            )
                        )
                        if (item.breakAfterMinutes > 0) {
                            Text(
                                text = "•",
                                style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = ColorTextMuted)
                            )
                            Text(
                                text = if (item.isBigBreak) "перемена ${item.breakAfterMinutes} мин (большая)"
                                else "перемена ${item.breakAfterMinutes} мин",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                    color = if (item.isBigBreak) ColorBrandBlue else ColorTextMuted,
                                    fontWeight = if (item.isBigBreak) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, ColorBorderLight),
                color = Color(0xFFF8FAFC),
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text = item.displayRange,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ColorBrandBlue
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Специальная карточка Информационного часа (четверг, 14:15 - 14:35).
 */
@Composable
private fun InfoHourCard(
    item: BellItem,
    isActive: Boolean
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color(0xFFF0FDF4),
        border = BorderStroke(1.dp, if (isActive) ColorActiveBlue else Color(0xFFBBF7D0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(0xFF16A34A), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ИНФОРМАЦИОННЫЙ ЧАС",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF166534)
                        )
                    )
                    Text(
                        text = "20 мин • перемена 10 мин",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            color = ColorTextMuted
                        )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                color = Color.White,
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text = item.displayRange,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF166534)
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
