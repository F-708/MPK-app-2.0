package com.politex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.politex.data.local.entity.LessonEntity
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable

/**
 * Чистая логика выбора/сортировки пар для кабинета — вынесена отдельно для тестируемости.
 *
 * Правила (как в ScheduleScreen для дат):
 * 1. Оставляем пары, у которых номер кабинета совпадает с roomFirst ИЛИ roomSecond (дробные пары).
 * 2. Если есть строки с точной датой — только они; пустой dateString — фолбэк-шаблон дня недели.
 * 3. Сортировка по lessonNumber.
 */
fun filterRoomLessonsForDate(
    lessons: List<LessonEntity>,
    room: String,
    dateString: String,
): List<LessonEntity> {
    if (room.isBlank()) return emptyList()
    val inRoom = lessons.filter { it.roomFirst == room || it.roomSecond == room }
    val exact = inRoom.filter { it.dateString == dateString }
    return (if (exact.isNotEmpty()) exact else inRoom.filter { it.dateString.isBlank() })
        .sortedBy { it.lessonNumber }
}

/**
 * Номер текущей (идущей прямо сейчас) пары, null — если сейчас перемена/вне занятий.
 * Тот же расчёт, что во вкладке «Расписание»: звонки дня недели (dayOfWeek 1=Пн..7=Вс)
 * и текущая минута суток. Инфочас четверга (lessonNumber=0) парой не считается.
 */
fun currentRoomLessonNumber(
    currentMinutes: Int,
    dayOfWeek: Int,
): Int? {
    val bells =
        com.politex.data.model.CollegeBellSchedule
            .getBellsForDay(dayOfWeek)
    return bells
        .firstOrNull { currentMinutes in it.startMinutes..it.endMinutes }
        ?.lessonNumber
        ?.takeIf { it > 0 }
}

/**
 * Диалог «Расписание кабинета»: список пар (предмет, время, группа, преподаватель),
 * текущая пара выделена синей рамкой и лёгким фоном — как во вкладке «Расписание».
 *
 * W7: шапка — две строки без каши: слева номер кабинета (цифра 20sp bold,
 * подпись «кабинет» 11sp muted сверху), крестик справа (48dp хитбокс);
 * ниже — дата одной строкой слева и компактный сегмент «Сегодня|Завтра» справа.
 * W8: карточки без секционных заголовков и без номера урока внутри —
 * плоский список, отсортированный по lessonNumber.
 *
 * @param roomNumber номер кабинета (для заголовка)
 * @param dateTitle  подпись с датой («02.10.2026, пятница») — приходит одной строкой,
 *                   рендерим в одну строку (maxLines=1), перенос не вставляем
 * @param lessons    готовый список пар по кабинету (загружается снаружи через репозиторий)
 * @param currentLessonNumber номер текущей пары или null
 * @param onTeacherClick клик по преподавателю — открытие его карточки
 */
@Composable
fun RoomScheduleDialog(
    roomNumber: String,
    dateTitle: String,
    lessons: List<LessonEntity>,
    currentLessonNumber: Int?,
    onTeacherClick: (String) -> Unit,
    onDismissRequest: () -> Unit,
    // Б8: «сегодня»/«завтра» — заказчик просил смотреть кабинет на оба дня
    tomorrowSelected: Boolean = false,
    onDayChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier =
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        containerColor = ColorBgMain,
        confirmButton = {},
        title = {
            // W7: шапка в две строки — цифра крупно слева, крестик справа,
            // дата + переключатель дня второй строкой
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "кабинет",
                            style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                            maxLines = 1,
                        )
                        Text(
                            text = roomNumber,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColorTextTitle),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("room_header_room"),
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        // W7: крупный хитбокс крестика 48dp, выше контента
                        modifier = Modifier.size(48.dp).testTag("room_close"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = ColorTextMuted,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (dateTitle.isNotBlank()) {
                        Text(
                            text = dateTitle,
                            // W7: дата строго одной строкой — не переносим посреди числа
                            style = TextStyle(fontSize = 13.sp, color = ColorTextBody),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier =
                            Modifier
                                .weight(1f)
                                .testTag("room_header_date"),
                        )
                    }
                    DayToggle(
                        tomorrowSelected = tomorrowSelected,
                        onDayChange = onDayChange,
                    )
                }
            }
        },
        text = {
            if (lessons.isEmpty()) {
                RoomScheduleEmptyState(tomorrow = tomorrowSelected)
            } else {
                // W8: плоский список карточек — без секционных заголовков «к 1 уроку 1 группа»
                LazyColumn(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        lessons,
                        key = { "${it.groupName}_${it.lessonNumber}_${it.id}" },
                    ) { lesson ->
                        RoomLessonCard(
                            lesson = lesson,
                            isCurrent = lesson.lessonNumber == currentLessonNumber,
                            onTeacherClick = onTeacherClick,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun RoomScheduleEmptyState(tomorrow: Boolean) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoomScheduleEmptyContent(tomorrow = tomorrow)
    }
}

/**
 * Б8/W7: компактный сегмент «Сегодня|Завтра» — Row из двух кнопок высотой 32dp,
 * активная залита брендовым цветом.
 */
@Composable
private fun DayToggle(
    tomorrowSelected: Boolean,
    onDayChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag("room_day_toggle"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(false to "Сегодня", true to "Завтра").forEach { (isTomorrow, title) ->
            val selected = tomorrowSelected == isTomorrow
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = if (selected) ColorBrandFill else ColorBgMain,
                border = BorderStroke(1.dp, if (selected) ColorBrandFill else ColorBorderLight),
                modifier =
                Modifier
                    .heightIn(min = 32.dp)
                    .bouncyClickable(scaleDown = 0.97f) { onDayChange(isTomorrow) }
                    .testTag(if (isTomorrow) "room_day_tomorrow" else "room_day_today"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = title,
                        softWrap = false,
                        maxLines = 1,
                        style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else ColorTextMuted,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomScheduleEmptyContent(tomorrow: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = ColorSurfaceHighlight,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.EventBusy,
                    contentDescription = null,
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text =
            if (tomorrow) {
                "Нет занятий в этом кабинете завтра"
            } else {
                "Нет занятий в этом кабинете сегодня"
            },
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * W8: компактная карточка пары — ТОЛЬКО предмет (14sp semi-bold, максимум 2 строки),
 * время (12sp muted), группа (12sp) и преподаватель (12sp, кликабельный чип).
 * Текущая пара: рамка 2dp ColorActiveBlue + лёгкий фон.
 */
@Composable
private fun RoomLessonCard(
    lesson: LessonEntity,
    isCurrent: Boolean,
    onTeacherClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        // W8: подсветка текущей пары — рамка 2dp синим + лёгкий синий фон
        color = if (isCurrent) ColorActiveBlue.copy(alpha = 0.07f) else ColorBgMain,
        border = BorderStroke(if (isCurrent) 2.dp else 1.dp, if (isCurrent) ColorActiveBlue else ColorBorderLight),
        shadowElevation = 0.dp,
        modifier =
        modifier
            .fillMaxWidth()
            .testTag("room_lesson_card_${lesson.lessonNumber}")
            .then(
                if (isCurrent) {
                    Modifier.testTag("room_lesson_current_${lesson.lessonNumber}")
                } else {
                    Modifier
                },
            ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = lesson.shortSubjectName,
                style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ColorTextTitle),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = lesson.timeRangeDisplay,
                style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                maxLines = 1,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // W8: группа и преподаватели в один ряд, компактно
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lesson.groupName,
                    style = TextStyle(fontSize = 12.sp, color = ColorTextBody),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("room_lesson_group_${lesson.lessonNumber}"),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoomTeacherChip(name = lesson.teacherFirst, onClick = onTeacherClick)
                    RoomTeacherChip(name = lesson.teacherSecond, onClick = onTeacherClick)
                }
            }
        }
    }
}

@Composable
private fun RoomTeacherChip(
    name: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (name.isBlank()) return
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
        color = ColorSurfaceHighlight,
        modifier = modifier.bouncyClickable(scaleDown = 0.97f) { onClick(name) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 5.dp, end = 5.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = ColorBrandBlue,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = name,
                style = TextStyle(fontSize = 12.sp, color = ColorBrandBlue, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
