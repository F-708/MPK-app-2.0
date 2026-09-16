package com.example.ui.screens

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
import androidx.compose.ui.draw.drawBehind
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
import com.example.ui.theme.ColorSurfaceHighlight
import com.example.ui.theme.ColorSurfaceVariantLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.theme.ColorTopBar
import com.example.ui.theme.TextStylePageTitle
import com.example.ui.util.bouncyClickable
import com.example.util.DebugClock
import java.util.Calendar
import kotlinx.coroutines.delay

private val RAIL_BLUE = Color(0xFF0072CE)
private val RAIL_BLUE_SOFT = Color(0xFF0072CE).copy(alpha = 0.30f)
private val RAIL_GRAY = Color(0xFF94A3B8).copy(alpha = 0.55f)
private val RAIL_GRAY_SOFT = Color(0xFFCBD5E1)

/** Строка списка звонков: урок/инфочас либо перемена между ними. */
private sealed interface BellRow {
    data class Lesson(val item: BellItem) : BellRow
    data class Break(val startMinutes: Int, val endMinutes: Int, val minutes: Int, val isBig: Boolean) : BellRow
}

/**
 * Экран расписания звонков «Мой Политех».
 *
 * Шкала времени рисуется ПРЯМО В КАРТОЧКАХ (drawBehind): вертикальная линия
 * и риски справа — часть самих строк, поэтому при прокрутке всё движется
 * идеально синхронно. Синяя зона — до последнего урока по факту расписания
 * группы; пульсирующая точка живёт на карточке текущего интервала.
 * При включённом дебаг-времени учитывается оно (DebugClock).
 */
@Composable
fun BellsScreen(
    groupInfo: com.example.data.model.GroupInfo,
    scheduleRepository: com.example.data.repository.ScheduleRepository,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val now = remember { DebugClock.now(context) }
    val todayDow = remember {
        when (now.get(Calendar.DAY_OF_WEEK)) {
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
    val lastLessonToday = remember(todayLessons) {
        todayLessons.maxOfOrNull { it.lessonNumber }
    }

    val todayType = remember { CollegeBellSchedule.getTypeForDay(todayDow) }
    var selectedScheduleType by remember { mutableStateOf(todayType) }

    var currentTimeMinutes by remember {
        mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
    }

    // Фоновое обновление времени каждые 30 секунд
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val c = DebugClock.now(context)
            currentTimeMinutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        }
    }

    val isTodaySelected = selectedScheduleType == todayType
    val activeSlot = remember(currentTimeMinutes, selectedScheduleType, isTodaySelected) {
        if (!isTodaySelected) null
        else CollegeBellSchedule.getBellsForType(selectedScheduleType)
            .firstOrNull { currentTimeMinutes in it.startMinutes..it.endMinutes }
    }

    // Последний урок по факту расписания (для синей зоны)
    val bells = CollegeBellSchedule.getBellsForType(selectedScheduleType)
    val blueEndNum = if (isTodaySelected) {
        lastLessonToday ?: bells.lastOrNull { !it.isInfoHour }?.lessonNumber
    } else null
    val blueEndMinutes = blueEndNum?.let { num ->
        bells.lastOrNull { !it.isInfoHour && it.lessonNumber <= num }?.endMinutes
    }

    // Ряды: уроки с переменами
    val rows = remember(selectedScheduleType) {
        val b = CollegeBellSchedule.getBellsForType(selectedScheduleType)
        val result = mutableListOf<BellRow>()
        b.forEach { item ->
            result.add(BellRow.Lesson(item))
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

        // Переключатель графиков (сегодняшний отмечен чертой снизу)
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

        // Индикатор текущего урока
        activeSlot?.let { currentSlot ->
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorSurfaceHighlight,
                border = BorderStroke(1.dp, ColorActiveBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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

        // Список: шкала нарисована drawBehind прямо в строках
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rows.forEach { row ->
                when (row) {
                    is BellRow.Lesson ->
                        LessonBellCard(
                            item = row.item,
                            isActive = activeSlot == row.item,
                            railTopColor = railColor(row.item.startMinutes, blueEndMinutes, isTodaySelected),
                            railBottomColor = railColor(row.item.endMinutes, blueEndMinutes, isTodaySelected),
                            inBlue = row.item.startMinutes < (blueEndMinutes ?: -1),
                            dotProgress = dotProgressIn(row.item, currentTimeMinutes, isTodaySelected)
                        )
                    is BellRow.Break -> BreakRow(row)
                }
            }
        }
    }
}

private fun railColor(minutes: Int, blueEnd: Int?, isActive: Boolean): Color {
    if (!isActive) return RAIL_GRAY_SOFT
    return if (blueEnd != null && minutes <= blueEnd) RAIL_BLUE else RAIL_GRAY
}

/** Прогресс точки внутри интервала (0..1) или null, если время вне интервала. */
private fun dotProgressIn(bell: BellItem, nowMinutes: Int, isActive: Boolean): Float? {
    if (!isActive) return null
    if (nowMinutes in bell.startMinutes..bell.endMinutes) {
        return (nowMinutes - bell.startMinutes).toFloat() /
            (bell.endMinutes - bell.startMinutes).coerceAtLeast(1)
    }
    return null
}

/**
 * Карточка урока со встроенной шкалой (drawBehind):
 * - вертикальный сегмент линии справа (синий/серый по зоне);
 * - риски на верхней и нижней границах карточки;
 * - пульсирующая точка на карточке текущего интервала.
 */
@Composable
private fun LessonBellCard(
    item: BellItem,
    isActive: Boolean,
    railTopColor: Color,
    railBottomColor: Color,
    inBlue: Boolean,
    dotProgress: Float?
) {
    val tickColor = if (inBlue) RAIL_BLUE else RAIL_GRAY_SOFT
    val pulseRadius by if (dotProgress != null) {
        rememberInfiniteTransition(label = "DotPulse").animateFloat(
            initialValue = 4.5f,
            targetValue = 7f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
            label = "DotPulseRadius"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, if (isActive) ColorActiveBlue else ColorBorderLight),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val cx = size.width + 10.dp.toPx() // линия справа за карточкой
                val tickLen = 5.dp.toPx()

                // Вертикальный сегмент линии (градация верх→низ по зонам)
                drawLine(
                    color = railTopColor,
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height / 2f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = railBottomColor,
                    start = Offset(cx, size.height / 2f),
                    end = Offset(cx, size.height),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Риски: верх и низ карточки
                drawLine(
                    color = tickColor,
                    start = Offset(cx - tickLen, 0f),
                    end = Offset(cx + tickLen, 0f),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = tickColor,
                    start = Offset(cx - tickLen, size.height),
                    end = Offset(cx + tickLen, size.height),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Точка времени на текущей карточке
                if (dotProgress != null) {
                    // Точка ходит не по всей высоте карточки, а с отступом:
                    // пульсирующее кольцо (до 11dp) не должно касаться рамки урока.
                    val inset = 16.dp.toPx()
                    val travel = (size.height - inset * 2).coerceAtLeast(1f)
                    val y = inset + travel * dotProgress
                    drawCircle(
                        color = RAIL_BLUE_SOFT,
                        radius = pulseRadius.dp.toPx() + 4.dp.toPx(),
                        center = Offset(cx, y)
                    )
                    drawCircle(
                        color = RAIL_BLUE,
                        radius = 4.5.dp.toPx(),
                        center = Offset(cx, y)
                    )
                }
            }
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
 * Компактный подпункт «перемена» между уроками
 * (с серым сегментом линии справа).
 */
@Composable
private fun BreakRow(row: BellRow.Break) {
    val range = "${minutesToTime(row.startMinutes)} – ${minutesToTime(row.endMinutes)}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val cx = size.width + 10.dp.toPx()
                drawLine(
                    color = RAIL_GRAY_SOFT,
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
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
