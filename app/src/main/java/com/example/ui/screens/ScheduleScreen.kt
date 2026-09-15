package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.ColorActiveBlue
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorDividerLight
import com.example.ui.theme.ColorMenuSubtext
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.theme.ColorTopBar
import com.example.ui.theme.TextStylePageTitle
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
 * СООТВЕТСТВИЕ ДИЗАЙН-СИСТЕМЕ И БИЗНЕС-ПРАВИЛАМ:
 * 1. Крупный заголовок страницы «Расписание» (32–34px Bold, Sentence case, #111827).
 * 2. Строгий расчет дат недели от Понедельника (Calendar.MONDAY).
 * 3. 1 курс: шестидневка (ПН-СБ). В пятницу кнопка перехода называется «Завтра (Сб)».
 * 4. 2-4 курсы: пятидневка (ПН-ПТ). В пятницу кнопка перехода называется «Понедельник».
 * 5. В субботу и воскресенье кнопка перехода называется «Понедельник».
 * 6. Запрет фейковых предметов: при отсутствии пар — аккуратный Empty State.
 * 7. Разделение по подгруппам: ровно 50/50 с микро-бейджами «1» и «2».
 * 8. Ручной выбор файла .doc/.docx с устройства и быстрая вставка текста расписания.
 * 9. Геометрия 0-2px, отсутствие теней (elevation 0dp), 1px рамки #E2E8F0.
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
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteInputText by remember { mutableStateOf("") }

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

    val currentWeekDay = weekDays.find { it.dayOfWeek == selectedDay }

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

    // 4. Поток расписания из Room Database
    val lessonsFromDb by scheduleRepository
        .getLessonsForDay(groupInfo.canonicalName, selectedDay)
        .collectAsState(initial = emptyList<com.example.data.local.entity.LessonEntity>())

    // 5. Фильтрация по архивной дате или по дню недели
    val lessons: List<com.example.data.local.entity.LessonEntity> = remember(lessonsFromDb, selectedDateString) {
        if (selectedDateString.isBlank()) {
            // Обычный просмотр дня недели: общие уроки (без даты) + только ПОСЛЕДНИЙ
            // датированный снапшот этого дня, чтобы архивные даты не дублировали карточки
            val latestDated = lessonsFromDb
                .filter { it.dateString.isNotBlank() }
                .maxOfOrNull { it.dateString.replace("-", ".") }
            lessonsFromDb.filter { it.dateString.isBlank() || it.dateString.replace("-", ".") == latestDated }
        } else {
            val bySpecificDate = lessonsFromDb.filter {
                it.dateString == selectedDateString ||
                it.dateString.replace("-", ".") == selectedDateString.replace("-", ".")
            }
            if (bySpecificDate.isNotEmpty()) bySpecificDate else lessonsFromDb
        }
    }

    // Лаунчер для выбора локального .doc/.docx файла расписания
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val bytes = inputStream.readBytes()
                        val parsedLessons = withContext(Dispatchers.IO) {
                            com.example.data.network.MpkScheduleParser.parseFile(
                                bytes = bytes,
                                targetGroup = groupInfo.canonicalName,
                                targetDate = currentWeekDay?.fullDateString ?: ""
                            )
                        }
                        if (parsedLessons.isNotEmpty()) {
                            scheduleRepository.saveLessons(parsedLessons)
                            Toast.makeText(
                                context,
                                "Импортировано ${parsedLessons.size} пар для группы ${groupInfo.canonicalName}!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Пар для группы ${groupInfo.canonicalName} в файле не обнаружено",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Ошибка чтения файла: ${e.localizedMessage ?: "неизвестно"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Диалог архива
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

    // Диалог вставки текста расписания
    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            shape = RoundedCornerShape(2.dp),
            containerColor = ColorBgMain,
            title = {
                Text(
                    text = "Вставить текст расписания",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ColorTextTitle
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Вставьте скопированный текст таблицы расписания (или из Telegram) для группы ${groupInfo.canonicalName}:",
                        style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ColorTextMuted)
                    )
                    OutlinedTextField(
                        value = pasteInputText,
                        onValueChange = { pasteInputText = it },
                        placeholder = { Text("Вставьте текст расписания...") },
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandBlue,
                    modifier = Modifier.bouncyClickable {
                        if (pasteInputText.isNotBlank()) {
                            coroutineScope.launch {
                                val parsed = withContext(Dispatchers.IO) {
                                    MpkScheduleParser.parsePlainTextSchedule(
                                        text = pasteInputText,
                                        targetGroup = groupInfo.canonicalName,
                                        targetDate = currentWeekDay?.fullDateString ?: ""
                                    )
                                }
                                if (parsed.isNotEmpty()) {
                                    scheduleRepository.saveLessons(parsed)
                                    Toast.makeText(
                                        context,
                                        "Импортировано ${parsed.size} пар для группы ${groupInfo.canonicalName}!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showPasteDialog = false
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Не удалось найти пары для группы ${groupInfo.canonicalName} в тексте",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = "ИМПОРТИРОВАТЬ",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            },
            dismissButton = {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = Color.Transparent,
                    modifier = Modifier.bouncyClickable { showPasteDialog = false }
                ) {
                    Text(
                        text = "ОТМЕНА",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ColorTextBody
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        // =========================================================================
        // Заголовок страницы «Расписание» (32-34px Bold, Sentence case, #111827)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Расписание",
                    style = TextStylePageTitle,
                    maxLines = 1
                )
                Text(
                    text = if (selectedDateString.isNotBlank()) {
                        "${currentWeekDay?.fullName ?: "День"} • $selectedDateString"
                    } else {
                        "${currentWeekDay?.fullName ?: "День"}, ${currentWeekDay?.dateFormatted ?: ""}"
                    },
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = ColorBrandBlue
                    )
                )
            }

            // Быстрые кнопки управления: [Вставить] [Файл] [Архив] [Завтра]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Кнопка вставки текста
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = ColorBgMain,
                    modifier = Modifier
                        .size(32.dp)
                        .bouncyClickable {
                            pasteInputText = ""
                            showPasteDialog = true
                        }
                        .testTag("paste_text_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Вставить текст",
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Кнопка открытия файла
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = ColorBgMain,
                    modifier = Modifier
                        .size(32.dp)
                        .bouncyClickable { filePickerLauncher.launch("*/*") }
                        .testTag("open_file_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = "Открыть .doc",
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Кнопка архивного календаря
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = ColorBgMain,
                    modifier = Modifier
                        .size(32.dp)
                        .bouncyClickable { showArchiveDialog = true }
                        .testTag("calendar_archive_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Архив",
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Кнопка перехода («Завтра» / «Понедельник»)
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBrandBlue),
                    color = ColorBrandBlue,
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
                        text = nextDayButtonText.uppercase(Locale.ROOT),
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // =========================================================================
        // Панель дней недели (ПН, ВТ, СР, ЧТ, ПТ, СБ)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (dayItem in weekDays) {
                val isSelected = (selectedDay == dayItem.dayOfWeek && selectedDateString.isBlank())
                val isToday = dayItem.isToday

                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) ColorTopBar else if (isToday) ColorBrandBlue else ColorBorderLight
                    ),
                    color = when {
                        isSelected -> ColorTopBar
                        isToday -> Color(0xFFEDF2F7)
                        else -> ColorBgMain
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
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
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White else ColorTextBody
                            )
                        )
                        Text(
                            text = dayItem.dateFormatted,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 10.sp,
                                color = if (isSelected) ColorMenuSubtext else ColorTextMuted
                            )
                        )
                    }
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

        // =========================================================================
        // Список пар или чистый Empty State
        // =========================================================================
        AnimatedContent(
            targetState = lessons,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScheduleListAnimation"
        ) { currentLessons ->
            if (currentLessons.isEmpty()) {
                // Empty State строго без фейковых уроков
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
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = ColorBrandBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Расписание ещё не опубликовано",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ColorTextTitle
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Для группы ${groupInfo.canonicalName} на этот день пар пока нет в базе колледжа.",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = ColorTextMuted
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(1.dp, ColorBrandBlue),
                                color = ColorBrandBlue,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bouncyClickable(onClick = onSyncRequest)
                                    .testTag("empty_state_sync_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "СИНХРОНИЗИРОВАТЬ С САЙТОМ",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    border = BorderStroke(1.dp, ColorBorderLight),
                                    color = ColorBgMain,
                                    modifier = Modifier
                                        .weight(1f)
                                        .bouncyClickable {
                                            pasteInputText = ""
                                            showPasteDialog = true
                                        }
                                        .testTag("empty_state_paste_btn")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = null,
                                            tint = ColorBrandBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "ВСТАВИТЬ ТЕКСТ",
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = ColorBrandBlue
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    border = BorderStroke(1.dp, ColorBorderLight),
                                    color = ColorBgMain,
                                    modifier = Modifier
                                        .weight(1f)
                                        .bouncyClickable { filePickerLauncher.launch("*/*") }
                                        .testTag("empty_state_file_btn")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.UploadFile,
                                            contentDescription = null,
                                            tint = ColorBrandBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "ФАЙЛ .DOC",
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = ColorBrandBlue
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
 * Карточка пары (обычная или с делением 50/50 по подгруппам) по Design System МПК:
 * - Скругление: 2px (строгая геометрия).
 * - Рамка: 1px solid #E2E8F0.
 * - Отсутствие теней (elevation 0dp).
 */
@Composable
fun LessonCard(
    lesson: LessonEntity,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("lesson_card_${lesson.lessonNumber}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Заголовок пары: номер пары (плашка 2px), время, акроним
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBrandBlue,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${lesson.lessonNumber}",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${lesson.timeStart} – ${lesson.timeEnd}",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ColorTextBody
                        )
                    )
                }

                // Акроним предмета
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text = lesson.subjectAcronym,
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ColorBrandBlue
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                        color = ColorBorderLight,
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
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ColorTextTitle
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

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
                                tint = ColorTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = lesson.teacherFirst,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    color = ColorTextBody
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (lesson.roomFirst.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, ColorBorderLight),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = ColorBrandBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "каб. ${lesson.roomFirst}",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = ColorBrandBlue
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
                color = ColorBrandBlue,
                modifier = Modifier.size(18.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$subgroupNumber",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            if (room.isNotBlank()) {
                Text(
                    text = "каб. $room",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = ColorBrandBlue,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subject,
            style = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = ColorTextTitle
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (teacher.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = teacher,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 11.sp,
                    color = ColorTextMuted
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
