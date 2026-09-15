package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.example.ui.theme.ColorActiveFill
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorSuccess
import com.example.ui.theme.ColorSuccessBg
import com.example.ui.theme.ColorSuccessBorder
import com.example.ui.theme.ColorSuccessText
import com.example.ui.theme.ColorSurfaceHighlight
import com.example.ui.theme.ColorSurfaceVariantLight
import com.example.ui.theme.ColorTextDisabled

/** Строка списка звонков: урок/инфочас либо перемена между ними. */
private sealed interface BellRow {
    data class Lesson(val item: BellItem) : BellRow
    data class Break(val startMinutes: Int, val endMinutes: Int, val minutes: Int, val isBig: Boolean) : BellRow
}

/**
 * Экран расписания звонков колледжа МГПК на 2026 год по официальному Style Guide.
 *
 * ОСОБЕННОСТИ:
 * 1. При входе автоматически выбран сегодняшний график (Пн-Ср,Пт / Четверг / Суббота);
 *    сегодняшний тип дня отмечен синей чертой под кнопкой.
 * 2. Между уроками — компактные подпункты «перемена N мин».
 * 3. Справа — вертикальная шкала времени: полосы уроков, риски границ и бегущий
 *    кружок текущего времени. Активна (синяя) только для сегодняшнего графика;
 *    для остальных дней шкала серая и неактивная.
 */
@Composable
fun BellsScreen(
    groupInfo: com.example.data.model.GroupInfo,
    scheduleRepository: com.example.data.repository.ScheduleRepository,
    modifier: Modifier = Modifier
) {
    // Фактическое расписание на сегодня: до какого урока идут занятия
    val todayDow = remember {
        when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }
    val todayLessons by scheduleRepository
        .getLessonsForDay(groupInfo.canonicalName, todayDow)
        .collectAsState(initial = emptyList<com.example.data.local.entity.LessonEntity>())
    // Последний урок по факту расписания (null = данных нет, считаем полный день)
    val lastLessonToday = remember(todayLessons) {
        todayLessons.maxOfOrNull { it.lessonNumber }
    }

    // Определение текущего дня недели и минут от начала суток
    val todayType = remember {
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

    // При входе во вкладку всегда открыт сегодняшний график
    var selectedScheduleType by remember { mutableStateOf(todayType) }

    var currentTimeMinutes by remember {
        val cal = Calendar.getInstance()
        mutableIntStateOf(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
    }

    // Фоновое обновление времени каждые 30 секунд
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val cal = Calendar.getInstance()
            currentTimeMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        }
    }

    val isTodaySelected = selectedScheduleType == todayType

    val activeSlot = remember(currentTimeMinutes, selectedScheduleType, isTodaySelected) {
        if (!isTodaySelected) null
        else CollegeBellSchedule.getBellsForType(selectedScheduleType)
            .firstOrNull { currentTimeMinutes in it.startMinutes..it.endMinutes }
    }

    // Ряды списка: уроки с переменами между ними
    val rows = remember(selectedScheduleType) {
        val bells = CollegeBellSchedule.getBellsForType(selectedScheduleType)
        val result = mutableListOf<BellRow>()
        bells.forEachIndexed { index, item ->
            result.add(BellRow.Lesson(item))
            val next = bells.getOrNull(index + 1) ?: return@forEachIndexed
            if (item.breakAfterMinutes > 0) {
                result.add(
                    BellRow.Break(
                        startMinutes = item.endMinutes,
                        endMinutes = item.endMinutes + item.breakAfterMinutes,
                        minutes = item.breakAfterMinutes,
                        isBig = item.isBigBreak
                    )
                )
            }
        }
        result
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
                text = "Расписание уроков колледжа",
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = ColorBrandBlue
                ),
                maxLines = 1
            )
        }

        // Переключатель графиков звонков (сегодняшний отмечен чертой снизу)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BellScheduleType.entries.forEach { type ->
                val isSelected = selectedScheduleType == type
                val isToday = type == todayType
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                        color = if (isSelected) ColorTopBar else ColorBgMain,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable { selectedScheduleType = type }
                    ) {
                        Text(
                            text = when (type) {
                                BellScheduleType.STANDARD -> "ПН - ПТ"
                                BellScheduleType.THURSDAY -> "ЧЕТВЕРГ"
                                BellScheduleType.SATURDAY -> "СУББОТА"
                            },
                            softWrap = false,
                            maxLines = 1,
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
                    // Черта «сегодня» под кнопкой сегодняшнего графика
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .width(28.dp)
                            .height(3.dp)
                            .background(
                                if (isToday) ColorActiveBlue else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(1.dp)
                .background(ColorDividerLight)
        )

        AnimatedContent(
            targetState = selectedScheduleType,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "BellsTypeTransition"
        ) { targetType ->
            val bells = CollegeBellSchedule.getBellsForType(targetType)

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 24.dp)
            ) {
                // Список уроков и переменных
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Баннер-дисклеймер
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorSurfaceVariantLight,
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

                    // Индикатор текущего активного урока / инфочаса (только для сегодняшнего графика)
                    activeSlot?.let { currentSlot ->
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorSurfaceHighlight,
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
                                        .background(ColorActiveFill, RoundedCornerShape(2.dp)),
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

                    rows.forEach { row ->
                        when (row) {
                            is BellRow.Lesson ->
                                if (row.item.isInfoHour) {
                                    InfoHourCard(item = row.item, isActive = activeSlot == row.item)
                                } else {
                                    LessonBellCard(item = row.item, isActive = activeSlot == row.item)
                                }
                            is BellRow.Break -> BreakRow(row)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Вертикальная шкала времени с ползунком
                BellsTimelineRail(
                    bells = bells,
                    nowMinutes = currentTimeMinutes,
                    isActive = isTodaySelected,
                    lastLessonToday = lastLessonToday,
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight()
                )
            }
        }
    }
}

/**
 * Компактный подпункт «перемена» между уроками.
 */
@Composable
private fun BreakRow(row: BellRow.Break) {
    val range = "${minutesToTime(row.startMinutes)} – ${minutesToTime(row.endMinutes)}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (row.isBig) "перемена ${row.minutes} мин (большая)" else "перемена ${row.minutes} мин",
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 11.sp,
                fontWeight = if (row.isBig) FontWeight.SemiBold else FontWeight.Normal,
                color = if (row.isBig) ColorBrandBlue else ColorTextMuted
            )
        )
        Text(
            text = range,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 10.sp,
                color = ColorTextMuted
            )
        )
    }
}

private fun minutesToTime(minutes: Int): String =
    String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

/**
 * Вертикальная шкала времени справа от списка:
 * - базовая серая линия и риски на границах уроков;
 * - СИНЯЯ зона от начала 1-го урока до конца последнего урока ПО ФАКТУ
 *   расписания на сегодня (нет данных — до конца последнего звонка);
 * - СЕРАЯ зона — остальное время дня;
 * - бегущий кружок: на занятиях синий и ПУЛЬСИРУЕТ; после последнего урока
 *   серый, но продолжает двигаться по времени.
 * Для чужого дня шкала полностью серая и неактивная.
 */
@Composable
private fun BellsTimelineRail(
    bells: List<BellItem>,
    nowMinutes: Int,
    isActive: Boolean,
    lastLessonToday: Int?,
    modifier: Modifier = Modifier
) {
    val grayColor = Color(0xFF94A3B8).copy(alpha = 0.55f)
    val grayBand = Color(0xFF64748B).copy(alpha = 0.35f)
    val blueColor = ColorActiveBlue

    val first = bells.minOf { it.startMinutes }
    val last = bells.maxOf { it.endMinutes }
    val span = (last - first).coerceAtLeast(1)

    // Конец синей зоны: последний урок по факту (или последний звонок, если данных нет)
    val blueEnd = if (isActive) {
        lastLessonToday?.let { num ->
            bells.filter { !it.isInfoHour }.lastOrNull { it.lessonNumber <= num }?.endMinutes
        } ?: last
    } else -1

    // Точка на занятиях — пульсирует
    val isOnLessons = isActive && nowMinutes <= blueEnd
    val pulseRadius by if (isOnLessons) {
        rememberInfiniteTransition(label = "DotPulse").animateFloat(
            initialValue = 5f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse
            ),
            label = "DotPulseRadius"
        )
    } else {
        remember { mutableStateOf(5f) }
    }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cx = size.width / 2
        val h = size.height

        fun y(minutes: Int): Float = (minutes - first).toFloat() / span * h
        val blueEndY = y(blueEnd)

        // Базовая линия: серая часть
        drawLine(
            color = if (isActive) grayColor else grayColor.copy(alpha = 0.45f),
            start = Offset(cx, 0f),
            end = Offset(cx, h),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Синяя зона: от начала до последнего фактического урока
        if (isActive && blueEnd > first) {
            drawLine(
                color = blueColor.copy(alpha = 0.75f),
                start = Offset(cx, y(first)),
                end = Offset(cx, blueEndY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Отметка конца занятий
            drawLine(
                color = blueColor,
                start = Offset(cx - 6.dp.toPx(), blueEndY),
                end = Offset(cx + 6.dp.toPx(), blueEndY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Риски на границах уроков
        bells.forEach { item ->
            val top = y(item.startMinutes)
            val bottom = y(item.endMinutes)
            val inBlue = isActive && item.startMinutes < blueEnd
            drawLine(
                color = if (inBlue) blueColor else grayBand,
                start = Offset(cx - 5.dp.toPx(), top),
                end = Offset(cx + 5.dp.toPx(), top),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = if (inBlue) blueColor else grayBand,
                start = Offset(cx - 5.dp.toPx(), bottom),
                end = Offset(cx + 5.dp.toPx(), bottom),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // Бегущая точка времени
        if (isActive) {
            val dotY = ((nowMinutes - first).toFloat() / span)
                .coerceIn(0f, 1f) * h
            val dotColor = if (isOnLessons) blueColor else grayColor
            if (isOnLessons) {
                // Пульсирующее кольцо
                drawCircle(
                    color = blueColor.copy(alpha = 0.25f),
                    radius = pulseRadius.dp.toPx() + 5.dp.toPx(),
                    center = Offset(cx, dotY)
                )
            }
            drawCircle(
                color = dotColor,
                radius = pulseRadius.dp.toPx(),
                center = Offset(cx, dotY)
            )
        }
    }
}

/**
 * Карточка урока с указанием времени и длительности по Style Guide.
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
                    color = if (isActive) ColorActiveFill else ColorBrandFill,
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
                Text(
                    text = item.title,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isActive) ColorActiveBlue else ColorTextTitle
                    )
                )
            }

            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, ColorBorderLight),
                color = ColorSurfaceVariantLight,
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
        color = ColorSuccessBg,
        border = BorderStroke(1.dp, if (isActive) ColorActiveBlue else ColorSuccessBorder),
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
                        .background(ColorSuccess, RoundedCornerShape(2.dp)),
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
                            color = ColorSuccessText
                        )
                    )
                    Text(
                        text = "20 мин",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            color = ColorTextMuted
                        )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, ColorSuccessBorder),
                color = Color.White,
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text = item.displayRange,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ColorSuccessText
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
