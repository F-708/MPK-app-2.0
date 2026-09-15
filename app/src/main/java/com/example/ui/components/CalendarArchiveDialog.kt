package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.repository.ScheduleRepository
import com.example.ui.screens.LessonCard
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorDividerLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextDisabled
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.theme.ColorTextWeekdays
import com.example.ui.theme.ColorTopBar
import com.example.ui.theme.TextStyleCalendarMonth
import com.example.ui.theme.TextStyleCalendarWeekdays
import com.example.ui.util.bouncyClickable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val MONTH_NAMES_CAPS = listOf(
    "ЯНВАРЬ", "ФЕВРАЛЬ", "МАРТ", "АПРЕЛЬ", "МАЙ", "ИЮНЬ",
    "ИЮЛЬ", "АВГУСТ", "СЕНТЯБРЬ", "ОКТЯБРЬ", "НОЯБРЬ", "ДЕКАБРЬ"
)

private val WEEKDAY_HEADERS = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")

/**
 * Диалог архивного календаря расписания ГУО «МГПК» по строгому Style Guide.
 *
 * Спецификация:
 * 1. Кнопки переключения месяцев: < ПРЕД. / СЛЕД. > в рамке 1px solid #E2E8F0, скругление 2px, цвет #0B3564 Bold.
 * 2. По центру: Месяц в формате «СЕНТЯБРЬ 2026» (#111827, Bold 15px, ALL CAPS).
 * 3. Заголовки дней: ПН ВТ СР ЧТ ПТ СБ ВС (#4B5563, Bold 13px, ALL CAPS).
 * 4. Выбранный день: фоновый круг 32px цвета #001737, белый жирный текст.
 * 5. Индикатор наличия пар: точка диаметром 3px цвета #0B3564 строго под цифрой (отступ 2px).
 * 6. Никаких теней (elevation 0dp), строгая прямоугольная геометрия 2px.
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

    // Сохраненные даты и пары
    val savedDates by scheduleRepository.getDistinctDates(groupName).collectAsState(initial = emptyList())
    val allGroupLessons by scheduleRepository.getAllLessonsForGroup(groupName).collectAsState(initial = emptyList())

    // Уроки за выбранную дату
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

    // Результаты поиска
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

    val prevMonthIndex = if (displayedMonth == 0) 11 else displayedMonth - 1
    val nextMonthIndex = if (displayedMonth == 11) 0 else displayedMonth + 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = ColorBgMain,
            border = BorderStroke(1.dp, ColorBorderLight),
            shadowElevation = 0.dp,
            modifier = modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .testTag("calendar_archive_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Шапка диалога
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "АРХИВ РАСПИСАНИЯ",
                            style = TextStyleCalendarMonth.copy(fontSize = 16.sp, color = ColorBrandBlue)
                        )
                        Text(
                            text = "ГРУППА $groupName",
                            style = TextStyleCalendarWeekdays.copy(fontSize = 12.sp, color = ColorTextMuted)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier = Modifier
                            .size(32.dp)
                            .bouncyClickable(onClick = onDismiss)
                            .testTag("close_archive_dialog_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = ColorTextBody,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Поисковая строка
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Поиск: предмет, преподаватель, аудитория...",
                            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ColorTextMuted)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(18.dp)
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
                                    tint = ColorTextMuted
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                        focusedIndicatorColor = ColorBrandBlue,
                        unfocusedIndicatorColor = ColorBorderLight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("archive_search_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (searchQuery.isNotBlank()) {
                    // Режим поиска
                    Text(
                        text = "НАЙДЕНО ЗАНЯТИЙ: ${searchResults.size}",
                        style = TextStyleCalendarWeekdays.copy(color = ColorBrandBlue),
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
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
                                style = androidx.compose.ui.text.TextStyle(color = ColorTextMuted, fontSize = 14.sp),
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
                    // =========================================================================
                    // Сетка календаря по официальной спецификации
                    // =========================================================================

                    // Кнопки переключения месяцев: [< ПРЕД.] [МЕСЯЦ ГОД] [СЛЕД. >]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопка предыдущего месяца
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, ColorBorderLight),
                            color = ColorBgMain,
                            modifier = Modifier
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
                            Text(
                                text = "< ${MONTH_NAMES_CAPS[prevMonthIndex]}",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = ColorBrandBlue
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        // Заголовок месяца
                        Text(
                            text = "${MONTH_NAMES_CAPS[displayedMonth]} $displayedYear",
                            style = TextStyleCalendarMonth,
                            textAlign = TextAlign.Center
                        )

                        // Кнопка следующего месяца
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, ColorBorderLight),
                            color = ColorBgMain,
                            modifier = Modifier
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
                            Text(
                                text = "${MONTH_NAMES_CAPS[nextMonthIndex]} >",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = ColorBrandBlue
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Заголовки дней недели (ПН ВТ СР ЧТ ПТ СБ ВС)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        WEEKDAY_HEADERS.forEach { dayName ->
                            Text(
                                text = dayName,
                                style = TextStyleCalendarWeekdays,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ColorDividerLight)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Сетка чисел месяца
                    val daysInMonth = remember(displayedYear, displayedMonth) {
                        calculateMonthDays(displayedYear, displayedMonth)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        userScrollEnabled = false
                    ) {
                        items(daysInMonth) { calendarDay ->
                            if (calendarDay == null) {
                                Box(modifier = Modifier.aspectRatio(1.1f))
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

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1.1f)
                                        .clickable { selectedDateString = dateStr }
                                        .testTag("calendar_day_$calendarDay"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        // Выбранный день: фоновый круг 32px цвета #001737
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(ColorTopBar)
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$calendarDay",
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                color = if (isSelected) Color.White else ColorTextBody
                                            )
                                        )

                                        // Индикатор наличия расписания: точка диаметром 3px цвета #0B3564
                                        if (hasData && !isSelected) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(3.dp)
                                                    .clip(CircleShape)
                                                    .background(ColorBrandBlue)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(5.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ColorDividerLight)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Панель выбранного дня
                    if (selectedDateString.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Пары на $selectedDateString (${selectedDateLessons.size}):",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ColorTextTitle
                                )
                            )

                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(1.dp, ColorBrandBlue),
                                color = ColorBrandBlue,
                                modifier = Modifier
                                    .bouncyClickable {
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
                                    }
                            ) {
                                Text(
                                    text = "ОТКРЫТЬ ДЕНЬ",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

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
                                        tint = ColorTextMuted,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "На этот день в архиве нет занятий",
                                        style = androidx.compose.ui.text.TextStyle(
                                            color = ColorTextMuted,
                                            fontSize = 13.sp
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
                                style = androidx.compose.ui.text.TextStyle(
                                    color = ColorTextMuted,
                                    fontSize = 13.sp
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
 * Безопасный парсер строк дат различных форматов.
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
