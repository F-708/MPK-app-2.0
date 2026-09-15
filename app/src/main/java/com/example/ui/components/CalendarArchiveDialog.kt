package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.LessonEntity
import com.example.data.repository.ScheduleRepository
import com.example.ui.screens.LessonCard
import com.example.ui.util.bouncyClickable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val MONTH_NAMES_RU = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)

private val WEEKDAY_HEADERS = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")

/**
 * Диалог архивного календаря расписания ГУО «МГПК».
 *
 * Функциональность:
 * 1. Месячный интерактивный календарь с подсветкой дней, имеющих сохраненные пары
 * 2. Поиск по всему архиву (предмет, преподаватель, аудитория)
 * 3. Детальный просмотр пар за выбранную архивную дату
 */
@Composable
fun CalendarArchiveDialog(
    groupName: String,
    scheduleRepository: ScheduleRepository,
    onDismiss: () -> Unit,
    onDateSelected: (dayOfWeek: Int, dateString: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val calendar = remember { Calendar.getInstance() }
    var displayedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) } // 0..11

    var selectedDateString by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    // Получаем сохраненные даты из БД
    val savedDates by scheduleRepository.getDistinctDates(groupName).collectAsState(initial = emptyList())
    val allGroupLessons by scheduleRepository.getAllLessonsForGroup(groupName).collectAsState(initial = emptyList())

    // Уроки за выбранную дату (или по дню недели)
    val selectedDateLessons = remember(selectedDateString, allGroupLessons) {
        if (selectedDateString.isBlank()) {
            emptyList()
        } else {
            val byDate = allGroupLessons.filter {
                it.dateString == selectedDateString ||
                it.dateString.replace("-", ".") == selectedDateString.replace("-", ".")
            }
            if (byDate.isNotEmpty()) {
                byDate
            } else {
                val cal = parseDateSafely(selectedDateString)
                if (cal != null) {
                    val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> 1
                        Calendar.TUESDAY -> 2
                        Calendar.WEDNESDAY -> 3
                        Calendar.THURSDAY -> 4
                        Calendar.FRIDAY -> 5
                        Calendar.SATURDAY -> 6
                        else -> 7
                    }
                    allGroupLessons.filter { it.dayOfWeek == dayOfWeek }
                } else emptyList()
            }
        }
    }

    // Результаты глобального поиска
    val searchResults = remember(searchQuery, allGroupLessons) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            allGroupLessons.filter {
                it.subjectRaw.lowercase(Locale.ROOT).contains(q) ||
                        it.teacherFirst.lowercase(Locale.ROOT).contains(q) ||
                        it.teacherSecond.lowercase(Locale.ROOT).contains(q) ||
                        it.roomFirst.lowercase(Locale.ROOT).contains(q) ||
                        it.roomSecond.lowercase(Locale.ROOT).contains(q) ||
                        it.shortSubjectName.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .testTag("calendar_archive_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Заголовок диалога и кнопка закрытия
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Архив расписания",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Группа $groupName",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .bouncyClickable(onClick = onDismiss)
                            .testTag("close_archive_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Поисковая строка
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Поиск: предмет, преподаватель, каб.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Очистить",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("archive_search_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (searchQuery.isNotBlank()) {
                    // Режим отображения результатов поиска
                    Text(
                        text = "Найдено занятий: ${searchResults.size}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Ничего не найдено по запросу «$searchQuery»",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults, key = { it.id }) { lesson ->
                                LessonCard(lesson = lesson)
                            }
                        }
                    }
                } else {
                    // Режим отображения календаря
                    // Панель переключения месяцев
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (displayedMonth == 0) {
                                    displayedMonth = 11
                                    displayedYear -= 1
                                } else {
                                    displayedMonth -= 1
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .bouncyClickable {
                                    if (displayedMonth == 0) {
                                        displayedMonth = 11
                                        displayedYear -= 1
                                    } else {
                                        displayedMonth -= 1
                                    }
                                }
                                .testTag("calendar_prev_month")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Предыдущий месяц",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "${MONTH_NAMES_RU[displayedMonth]} $displayedYear",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        IconButton(
                            onClick = {
                                if (displayedMonth == 11) {
                                    displayedMonth = 0
                                    displayedYear += 1
                                } else {
                                    displayedMonth += 1
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .bouncyClickable {
                                    if (displayedMonth == 11) {
                                        displayedMonth = 0
                                        displayedYear += 1
                                    } else {
                                        displayedMonth += 1
                                    }
                                }
                                .testTag("calendar_next_month")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Следующий месяц",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Заголовки дней недели
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        WEEKDAY_HEADERS.forEach { dayName ->
                            Text(
                                text = dayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Сетка дней месяца
                    val daysInMonth = remember(displayedYear, displayedMonth) {
                        calculateMonthDays(displayedYear, displayedMonth)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 210.dp),
                        userScrollEnabled = false
                    ) {
                        items(daysInMonth) { calendarDay ->
                            if (calendarDay == null) {
                                Box(modifier = Modifier.aspectRatio(1.2f))
                            } else {
                                val dateStr = String.format(
                                    Locale.ROOT,
                                    "%04d-%02d-%02d",
                                    displayedYear,
                                    displayedMonth + 1,
                                    calendarDay
                                )
                                val isSelected = selectedDateString == dateStr
                                val hasData = savedDates.contains(dateStr) || allGroupLessons.isNotEmpty()

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (hasData) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    } else {
                                        Color.Transparent
                                    },
                                    contentColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier
                                        .aspectRatio(1.2f)
                                        .padding(2.dp)
                                        .bouncyClickable {
                                            selectedDateString = dateStr
                                        }
                                        .testTag("calendar_day_$calendarDay")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(
                                            text = "$calendarDay",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected || hasData) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.sp
                                            )
                                        )
                                        if (hasData && !isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Панель выбранного дня
                    if (selectedDateString.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Пары на $selectedDateString (${selectedDateLessons.size}):",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            TextButton(
                                onClick = {
                                    val cal = parseDateSafely(selectedDateString)
                                    if (cal != null) {
                                        val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                                            Calendar.MONDAY -> 1
                                            Calendar.TUESDAY -> 2
                                            Calendar.WEDNESDAY -> 3
                                            Calendar.THURSDAY -> 4
                                            Calendar.FRIDAY -> 5
                                            Calendar.SATURDAY -> 6
                                            else -> 1
                                        }
                                        onDateSelected(dayOfWeek, selectedDateString)
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.bouncyClickable { }
                            ) {
                                Text(
                                    text = "Открыть день",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (selectedDateLessons.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.EventBusy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "На этот день в архиве нет занятий",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(selectedDateLessons, key = { it.id }) { lesson ->
                                    LessonCard(lesson = lesson)
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Выберите день в календаре для просмотра архива",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Рассчитывает список ячеек для отображения сетки месяца (с учетом смещения дня недели понедельника).
 */
private fun calculateMonthDays(year: Int, month: Int): List<Int?> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }

    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday...
    val offset = when (firstDayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }

    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val list = mutableListOf<Int?>()

    for (i in 0 until offset) {
        list.add(null)
    }
    for (d in 1..maxDays) {
        list.add(d)
    }

    return list
}

/**
 * Безопасный парсер строк дат различных форматов (yyyy-MM-dd, dd.MM.yyyy, dd-MM-yyyy).
 */
private fun parseDateSafely(dateStr: String): Calendar? {
    val formats = listOf("yyyy-MM-dd", "dd.MM.yyyy", "dd-MM-yyyy")
    for (format in formats) {
        try {
            val sdf = SimpleDateFormat(format, Locale.ROOT)
            val d = sdf.parse(dateStr)
            if (d != null) {
                return Calendar.getInstance().apply { time = d }
            }
        } catch (_: Exception) {}
    }
    return null
}

