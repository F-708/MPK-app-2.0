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
import androidx.compose.material.icons.filled.LocationOn
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
    dateString: String
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
fun currentRoomLessonNumber(currentMinutes: Int, dayOfWeek: Int): Int? {
    val bells = com.politex.data.model.CollegeBellSchedule.getBellsForDay(dayOfWeek)
    return bells.firstOrNull { currentMinutes in it.startMinutes..it.endMinutes }
        ?.lessonNumber
        ?.takeIf { it > 0 }
}

/**
 * Диалог «Расписание кабинета на сегодня»: список пар (время, группа, преподаватель, предмет),
 * текущая пара выделена синей рамкой с меткой «СЕЙЧАС» — как во вкладке «Расписание».
 *
 * @param roomNumber номер кабинета (для заголовка)
 * @param dateTitle  подпись с датой («сегодня, 22.09»)
 * @param lessons    готовый список пар по кабинету (загружается снаружи через репозиторий)
 * @param currentLessonNumber номер текущей пары или null
 * @param onTeacherClick клик по преподавателю — открытие его карточки (проводит интегратор)
 */
@Composable
fun RoomScheduleDialog(
    roomNumber: String,
    dateTitle: String,
    lessons: List<LessonEntity>,
    currentLessonNumber: Int?,
    onTeacherClick: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        containerColor = ColorBgMain,
        confirmButton = {},
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = roomNumber,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = androidx.compose.ui.graphics.Color.White),
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Кабинет $roomNumber — сегодня",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitle)
                    )
                    if (dateTitle.isNotBlank()) {
                        Text(
                            text = dateTitle,
                            style = TextStyle(fontSize = 12.sp, color = ColorTextMuted)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = ColorTextMuted
                    )
                }
            }
        },
        text = {
            if (lessons.isEmpty()) {
                RoomScheduleEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(lessons, key = { "${it.groupName}_${it.lessonNumber}_${it.id}" }) { lesson ->
                        RoomLessonCard(
                            lesson = lesson,
                            isCurrent = lesson.lessonNumber == currentLessonNumber,
                            onTeacherClick = onTeacherClick
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun RoomScheduleEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = ColorSurfaceHighlight,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.EventBusy,
                    contentDescription = null,
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Нет занятий в этом кабинете сегодня",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/**
 * Компактная карточка пары для кабинета: номер, время, предмет, группа, преподаватель(и).
 * Стили совпадают с LessonCard из ScheduleScreen (2.dp радиус, палитра, размеры шрифтов),
 * без привязки к экрану расписания.
 */
@Composable
private fun RoomLessonCard(
    lesson: LessonEntity,
    isCurrent: Boolean,
    onTeacherClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(if (isCurrent) 2.dp else 1.dp, if (isCurrent) ColorActiveBlue else ColorBorderLight),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("room_lesson_card_${lesson.lessonNumber}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBrandFill,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${lesson.lessonNumber}",
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.White)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = lesson.timeRangeDisplay,
                        style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ColorTextBody)
                    )
                }

                if (isCurrent) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorActiveBlue,
                        modifier = Modifier.testTag("room_lesson_current_${lesson.lessonNumber}")
                    ) {
                        Text(
                            text = "СЕЙЧАС",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.White),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = lesson.shortSubjectName,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorSurfaceHighlight,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.testTag("room_lesson_group_${lesson.lessonNumber}")
                ) {
                    Text(
                        text = lesson.groupName,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ColorTextMuted),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoomTeacherChip(name = lesson.teacherFirst, onClick = onTeacherClick)
                    RoomTeacherChip(name = lesson.teacherSecond, onClick = onTeacherClick)
                }
            }

            if (lesson.isSplit && lesson.roomFirst.isNotBlank() && lesson.roomSecond.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = ColorBrandBlue,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp)
                    )
                    Text(
                        text = "дробная: каб. ${lesson.roomFirst} / каб. ${lesson.roomSecond}",
                        style = TextStyle(fontSize = 11.sp, color = ColorTextMuted)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomTeacherChip(
    name: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (name.isBlank()) return
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
        color = ColorSurfaceHighlight,
        modifier = modifier.bouncyClickable(scaleDown = 0.97f) { onClick(name) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 5.dp, end = 5.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = ColorBrandBlue,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = name,
                style = TextStyle(fontSize = 12.sp, color = ColorBrandBlue, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
