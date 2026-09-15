package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.LessonEntity
import com.example.data.model.GroupInfo
import com.example.data.network.MpkScheduleParser
import com.example.data.repository.ScheduleRepository
import com.example.ui.components.CalendarArchiveDialog
import com.example.ui.util.bouncyClickable
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Элемент дня недели для панели навигации с точным календарным расчетом.
 */
data class WeekDayItem(
    val dayOfWeek: Int, // 1=Пн, 2=Вт, 3=Ср, 4=Чт, 5=Пт, 6=Сб
    val shortName: String, // "ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ"
    val fullName: String, // "Понедельник", ...
    val dayOfMonth: Int,
    val monthNumber: Int,
    val dateFormatted: String, // "15.09"
    val fullDateString: String, // "15.09.2026"
    val isToday: Boolean
)

/**
 * Главный экран расписания занятий колледжа ГУО «МГПК».
 *
 * БИЗНЕС-ПРАВИЛА И ТРЕБОВАНИЯ:
 * 1. Строгий расчет дат недели от Понедельника (Calendar.MONDAY, daysFromMonday).
 * 2. 1 курс: шестидневка (ПН-СБ). В пятницу кнопка перехода называется «Завтра (Сб)».
 * 3. 2-4 курсы: пятидневка (ПН-ПТ). В пятницу кнопка перехода называется «Понедельник».
 * 4. В субботу и воскресенье кнопка перехода называется «Понедельник».
 * 5. С понедельника по четверг: видна кнопка «Завтра».
 * 6. Запрет фейковых предметов: при отсутствии пар — аккуратный Empty State.
 * 7. Разделение по подгруппам: ровно 50/50 с микро-бейджами «1» и «2».
 * 8. Ручной выбор файла .doc/.docx с устройства через ActivityResultContracts.GetContent().
 */
@Composable
fun ScheduleScreen(
    groupInfo: GroupInfo,
    scheduleRepository: ScheduleRepository,
    onSyncRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Точный расчет реального сегодняшнего дня
    val realLocale = remember { Locale("ru", "BY") }
    val realNow = remember { Calendar.getInstance(realLocale) }
    val realDayOfWeek = remember {
        when (realNow.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7 // Воскресенье
        }
    }

    val daysCount = if (groupInfo.hasSaturdayClasses) 6 else 5

    // Стартовый выбранный день (если сегодня выходной или воскресенье — открываем понедельник)
    val defaultSelectedDay = remember(groupInfo.canonicalName, realDayOfWeek) {
        if (realDayOfWeek in 1..daysCount) realDayOfWeek else 1
    }

    var selectedDay by remember(groupInfo.canonicalName) { mutableIntStateOf(defaultSelectedDay) }
    var selectedDateString by remember(groupInfo.canonicalName) { mutableStateOf("") }
    var showArchiveDialog by remember { mutableStateOf(false) }

    // 2. Строгий расчет дат текущей недели от ПОНЕДЕЛЬНИКА
    val weekDays = remember(groupInfo.hasSaturdayClasses) {
        val cal = Calendar.getInstance(realLocale).apply {
            firstDayOfWeek = Calendar.MONDAY
        }
        val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (currentDayOfWeek == Calendar.SUNDAY) 6 else currentDayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday) // Точный переход на ПОНЕДЕЛЬНИК текущей недели

        val dayNames = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ")
        val fullDayNames = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")

        (0 until daysCount).map { i ->
            val dayCal = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, i)
            }
            val dayNum = i + 1
            val d = dayCal.get(Calendar.DAY_OF_MONTH)
            val m = dayCal.get(Calendar.MONTH) + 1
            val y = dayCal.get(Calendar.YEAR)
            val dateFormatted = String.format(realLocale, "%02d.%02d", d, m)
            val fullDate = String.format(realLocale, "%02d.%02d.%04d", d, m, y)
            val isToday = (dayNum == realDayOfWeek)

            WeekDayItem(
                dayOfWeek = dayNum,
                shortName = dayNames[i],
                fullName = fullDayNames[i],
                dayOfMonth = d,
                monthNumber = m,
                dateFormatted = dateFormatted,
                fullDateString = fullDate,
                isToday = isToday
            )
        }
    }

    // 3. Кнопка быстрого перехода на следующий учебный день
    val nextDayButtonText = remember(selectedDay, groupInfo.hasSaturdayClasses, realDayOfWeek) {
        when {
            // В субботу и воскресенье: все группы видят «Понедельник»
            selectedDay == 6 || realDayOfWeek == 7 || realDayOfWeek == 6 -> "Понедельник"
            // В пятницу: 1 курс видит «Завтра (Сб)», а 2-4 курсы видят «Понедельник»
            selectedDay == 5 && groupInfo.hasSaturdayClasses -> "Завтра (Сб)"
            selectedDay == 5 && !groupInfo.hasSaturdayClasses -> "Понедельник"
            // С понедельника по четверг: «Завтра»
            else -> "Завтра"
        }
    }

    // 4. Лаунчер для ручного открытия файла .doc/.docx
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val parsedLessons = withContext(Dispatchers.IO) {
                            MpkScheduleParser.parseFile(
                                bytes = bytes,
                                targetGroup = groupInfo.canonicalName
                            )
                        }
                        if (parsedLessons.isNotEmpty()) {
                            scheduleRepository.saveLessons(parsedLessons)
                            Toast.makeText(
                                context,
                                "Импортировано ${parsedLessons.size} пар для группы ${groupInfo.canonicalName}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "В файле не найдено расписания для группы ${groupInfo.canonicalName}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(context, "Не удалось прочитать выбранный файл", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка импорта файла: ${e.localizedMessage ?: "неизвестно"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val lessonsFlow = remember(groupInfo.canonicalName, selectedDay, selectedDateString) {
        if (selectedDateString.isNotBlank()) {
            scheduleRepository.getLessonsForDate(groupInfo.canonicalName, selectedDateString)
        } else {
            scheduleRepository.getLessonsForDay(groupInfo.canonicalName, selectedDay)
        }
    }
    val lessons: List<LessonEntity> by lessonsFlow.collectAsState(initial = emptyList())

    // Архивный диалог календаря
    if (showArchiveDialog) {
        CalendarArchiveDialog(
            groupName = groupInfo.canonicalName,
            scheduleRepository = scheduleRepository,
            onDismiss = { showArchiveDialog = false },
            onDateSelected = { dayOfWeek, dateStr ->
                selectedDay = if (dayOfWeek in 1..daysCount) dayOfWeek else 1
                selectedDateString = dateStr
                showArchiveDialog = false
            }
        )
    }

    val currentWeekDay = weekDays.find { it.dayOfWeek == selectedDay }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Панель выбора дней недели и заголовка
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                // Заголовок текущего дня и кнопки быстрых действий
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (selectedDateString.isNotBlank()) {
                                "${currentWeekDay?.fullName ?: "День"} ($selectedDateString)"
                            } else {
                                "${currentWeekDay?.fullName ?: "День"}, ${currentWeekDay?.dateFormatted ?: ""}"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        if (currentWeekDay?.isToday == true && selectedDateString.isBlank()) {
                            Text(
                                text = "Сегодня",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Кнопка ручного открытия файла расписания .doc/.docx
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(34.dp)
                                .bouncyClickable {
                                    filePickerLauncher.launch("*/*")
                                }
                                .testTag("open_file_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FileOpen,
                                    contentDescription = "Открыть файл .doc/.docx с устройства",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Кнопка открытия архивного календаря
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(34.dp)
                                .bouncyClickable { showArchiveDialog = true }
                                .testTag("calendar_archive_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Архив расписания",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Кнопка быстрого перехода («Завтра» / «Завтра (Сб)» / «Понедельник»)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .bouncyClickable {
                                    selectedDateString = ""
                                    selectedDay = when {
                                        selectedDay == 5 && groupInfo.hasSaturdayClasses -> 6
                                        selectedDay >= daysCount -> 1
                                        else -> selectedDay + 1
                                    }
                                }
                                .testTag("next_day_button")
                        ) {
                            Text(
                                text = nextDayButtonText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Горизонтальная панель дней недели (Пн, Вт, Ср, Чт, Пт, [Сб]) с датами
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (dayItem in weekDays) {
                        val isSelected = (selectedDay == dayItem.dayOfWeek && selectedDateString.isBlank())
                        val isToday = dayItem.isToday

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .bouncyClickable {
                                    selectedDateString = ""
                                    selectedDay = dayItem.dayOfWeek
                                }
                                .testTag("weekday_tab_${dayItem.dayOfWeek}")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = dayItem.shortName,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dayItem.dateFormatted,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Список пар или Empty State
        AnimatedContent(
            targetState = lessons,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScheduleListAnimation"
        ) { currentLessons ->
            if (currentLessons.isEmpty()) {
                // Строгий Empty State без фейковых предметов
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Расписание ещё не опубликовано",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Для группы ${groupInfo.canonicalName} на этот день пар пока нет в базе колледжа.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onSyncRequest,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .bouncyClickable(onClick = onSyncRequest)
                                    .testTag("empty_state_sync_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Синхронизировать",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }

                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .bouncyClickable { filePickerLauncher.launch("*/*") }
                                    .testTag("empty_state_file_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Файл .doc",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentLessons, key = { it.id }) { lesson ->
                        LessonCard(lesson = lesson)
                    }
                }
            }
        }
    }
}

/**
 * Карточка пары (обычная или с делением 50/50 по подгруппам).
 */
@Composable
fun LessonCard(
    lesson: LessonEntity,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("lesson_card_${lesson.lessonNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Заголовок пары: номер, время и акроним-бейдж
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${lesson.lessonNumber}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${lesson.timeStart} – ${lesson.timeEnd}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                // Акроним предмета
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text = lesson.subjectAcronym,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (lesson.isSplit) {
                // Разделение ровно 50/50 с микро-бейджами «1» и «2»
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Подгруппа 1
                    SubgroupPane(
                        subgroupNumber = 1,
                        subject = lesson.shortSubjectName,
                        teacher = lesson.teacherFirst,
                        room = lesson.roomFirst,
                        modifier = Modifier.weight(1f)
                    )

                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxHeight()
                    )

                    // Подгруппа 2
                    SubgroupPane(
                        subgroupNumber = 2,
                        subject = lesson.shortSubjectName,
                        teacher = lesson.teacherSecond,
                        room = lesson.roomSecond,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Одиночная пара для всей группы
                Text(
                    text = lesson.shortSubjectName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (lesson.teacherFirst.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = lesson.teacherFirst,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (lesson.roomFirst.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "каб. ${lesson.roomFirst}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Панель подгруппы (50% ширины карточки) с микро-бейджем «1» или «2».
 */
@Composable
private fun SubgroupPane(
    subgroupNumber: Int,
    subject: String,
    teacher: String,
    room: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Микро-бейдж подгруппы «1» или «2»
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$subgroupNumber",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            if (room.isNotBlank()) {
                Text(
                    text = "каб. $room",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subject,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (teacher.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = teacher,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
