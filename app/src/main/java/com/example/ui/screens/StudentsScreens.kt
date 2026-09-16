package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MpkDatabase
import com.example.data.model.CollegeBellSchedule
import com.example.data.model.GroupInfo
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.Student
import com.example.data.repository.StudentsRepository
import com.example.data.repository.Teacher
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorDividerLight
import com.example.ui.theme.ColorSurfaceVariantLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.theme.ColorTopBar
import com.example.ui.theme.TextStylePageTitle
import com.example.data.repository.TeachersRepository
import com.example.data.repository.TeacherInsights
import androidx.compose.material3.CircularProgressIndicator
import com.example.ui.util.bouncyClickable
import com.example.util.DebugClock
import java.util.Calendar
import kotlinx.coroutines.launch

private val ADMIN_ACCENT = Color(0xFF7A5C00)
private val ADMIN_BG = Color(0xFF2A2313)
private val ADMIN_GOLD = Color(0xFFE8C55A)

/**
 * База данных учащихся (только admin-версия): поиск и фильтры
 * по всем 1703 учащимся колледжа.
 */
@Composable
fun StudentsDatabaseScreen(
    onBack: () -> Unit,
    onOpenStudent: (Student) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val students = remember { StudentsRepository(context).loadStudents() }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var groupFilter by rememberSaveable { mutableStateOf("") }
    var courseFilter by rememberSaveable { mutableStateOf("") }

    val groups = remember(students) { students.map { it.group }.distinct().sorted() }
    val courses = remember(students) { students.map { it.course }.distinct().sorted() }

    val filtered = remember(students, searchQuery, groupFilter, courseFilter) {
        val q = searchQuery.trim().lowercase()
        students.filter { s ->
            (q.isBlank() || s.fullName.lowercase().contains(q) ||
                s.curator.lowercase().contains(q)) &&
                (groupFilter.isBlank() || s.group == groupFilter) &&
                (courseFilter.isBlank() || s.course == courseFilter)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        // Шапка с кнопкой назад
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = ADMIN_ACCENT,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "База данных учащихся",
                        style = TextStylePageTitle.copy(fontSize = 22.sp),
                        maxLines = 1
                    )
                }
                Text(
                    text = "Найдено: ${filtered.size} из ${students.size}",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = ADMIN_ACCENT
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorDividerLight)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ФИО или куратор...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = ADMIN_ACCENT, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(2.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ColorSurfaceVariantLight,
                unfocusedContainerColor = ColorSurfaceVariantLight,
                focusedIndicatorColor = ADMIN_ACCENT,
                unfocusedIndicatorColor = ColorBorderLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Фильтры: группы и курсы
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterDropdown(
                label = if (groupFilter.isBlank()) "Группа" else groupFilter,
                options = groups,
                selected = groupFilter,
                onSelect = { groupFilter = it },
                modifier = Modifier.weight(1f)
            )
            FilterDropdown(
                label = if (courseFilter.isBlank()) "Курс" else courseFilter,
                options = courses,
                selected = courseFilter,
                onSelect = { courseFilter = it },
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered) { student ->
                StudentRow(student) { onOpenStudent(student) }
            }
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, if (selected.isNotBlank()) ADMIN_ACCENT else ColorBorderLight),
            color = ColorBgMain,
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable { expanded = true }
        ) {
            Text(
                text = label,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = ColorTextBody
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Все") },
                onClick = { onSelect(""); expanded = false }
            )
            options.forEach { opt ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun StudentRow(student: Student, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(ADMIN_BG, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = student.group,
                    color = Color(0xFFE8C55A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.fullName,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ColorTextTitle
                    )
                )
                Text(
                    text = listOf(student.course + " курс", student.funding).filter { it.isNotBlank() }.joinToString(" • "),
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        color = ColorTextMuted
                    )
                )
            }
            Icon(
                imageVector = Icons.Default.PersonSearch,
                contentDescription = null,
                tint = ADMIN_ACCENT,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Карточка учащегося (admin): сведения, «где сейчас» и расписание его группы.
 *
 * Расписание группы подгружается с сайта при первом открытии карточки —
 * иначе для чужих групп (не выбранных в приложении) данных в кэше нет.
 */
@Composable
fun StudentCardScreen(
    student: Student,
    scheduleRepository: ScheduleRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val now = remember { DebugClock.now() }
    val dow = remember {
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
    val minutes = remember {
        now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }

    val lessons by scheduleRepository
        .getLessonsForDay(student.group, dow)
        .collectAsState(initial = emptyList())

    val allGroupLessons by scheduleRepository
        .getAllLessonsForGroup(student.group)
        .collectAsState(initial = emptyList())

    // --- Автозагрузка расписания группы с сайта ---
    var isLoading by remember(student.group) { mutableStateOf(false) }
    var loadMessage by remember(student.group) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadSchedule() {
        if (isLoading) return
        isLoading = true
        loadMessage = ""
        scope.launch {
            val result = scheduleRepository.syncScheduleFromWeb(student.group)
            loadMessage = result.fold(
                onSuccess = { count ->
                    if (count > 0) "Загружено уроков: $count" else "Расписание группы пока не опубликовано"
                },
                onFailure = { "Не удалось загрузить (проверьте интернет)" }
            )
            isLoading = false
        }
    }

    LaunchedEffect(student.group) {
        // Если для группы ещё нет данных — тихо подтягиваем с сайта
        if (!scheduleRepository.hasSchedule(student.group)) {
            loadSchedule()
        }
    }

    // Преподаватели, которые ведут у этой группы (сопоставление с официальной базой)
    val teachers = remember { TeachersRepository(context).loadTeachers() }
    val insights = remember(teachers, allGroupLessons) {
        TeacherInsights(teachers, allGroupLessons)
    }

    val currentLesson = remember(lessons, minutes) {
        val bells = CollegeBellSchedule.getBellsForDay(dow)
        val currentNum = bells.firstOrNull { minutes in it.startMinutes..it.endMinutes }?.lessonNumber
        if (currentNum != null && currentNum > 0) {
            lessons.firstOrNull { it.lessonNumber == currentNum }
        } else null
    }
    val nextLesson = remember(lessons, minutes, currentLesson) {
        lessons.filter { it.lessonNumber > (currentLesson?.lessonNumber ?: 0) }
            .minByOrNull { it.lessonNumber }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.fullName,
                    style = TextStylePageTitle.copy(fontSize = 20.sp),
                    maxLines = 1
                )
                Text(
                    text = "Группа ${student.group} • ${student.course} курс",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = ADMIN_ACCENT
                    ),
                    maxLines = 1
                )
            }
            // Ручное обновление расписания группы
            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, ADMIN_ACCENT),
                color = ColorBgMain,
                modifier = Modifier
                    .size(36.dp)
                    .bouncyClickable { loadSchedule() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = ADMIN_ACCENT,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Обновить расписание группы",
                            tint = ADMIN_ACCENT,
                            modifier = Modifier.size(18.dp)
                        )
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

        if (loadMessage.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = loadMessage,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Личные и учебные сведения
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        InfoLine("Специальность", student.specialty)
                        if (student.specialtyCode.isNotBlank()) {
                            InfoLine("Код специальности", student.specialtyCode)
                        }
                        if (student.funding.isNotBlank()) InfoLine("Основа обучения", student.funding)
                        if (student.dormitory.isNotBlank()) InfoLine("Общежитие", student.dormitory)
                        if (student.curator.isNotBlank()) InfoLine("Куратор", student.curator)
                    }
                }
            }

            // Где сейчас
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ADMIN_BG,
                    border = BorderStroke(1.dp, ADMIN_ACCENT),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_where_now")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ГДЕ СЕЙЧАС",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = ADMIN_GOLD
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        when {
                            currentLesson != null -> {
                                Text(
                                    text = currentLesson.subjectRaw,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = buildString {
                                        append("Урок ${currentLesson.lessonNumber} • ")
                                        append("${currentLesson.timeStart}–${currentLesson.timeEnd}")
                                        val rooms = listOf(currentLesson.roomFirst, currentLesson.roomSecond)
                                            .filter { it.isNotBlank() }
                                        if (rooms.isNotEmpty()) append(" • каб. ${rooms.joinToString(" / ")}")
                                    },
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        color = Color(0xFFD9CBA8)
                                    )
                                )
                                val teacher = listOf(currentLesson.teacherFirst, currentLesson.teacherSecond)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" / ")
                                if (teacher.isNotBlank()) {
                                    Text(
                                        text = teacher,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 12.sp,
                                            color = Color(0xFFB8A87F)
                                        )
                                    )
                                }
                            }
                            isLoading -> {
                                Text(
                                    text = "Загружаем расписание…",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                )
                            }
                            else -> {
                                val bells = CollegeBellSchedule.getBellsForDay(dow)
                                val breakNow = bells.firstOrNull {
                                    it.breakAfterMinutes > 0 && minutes > it.endMinutes &&
                                        minutes < it.endMinutes + it.breakAfterMinutes
                                }
                                Text(
                                    text = when {
                                        allGroupLessons.isEmpty() ->
                                            "Расписание группы не загружено"
                                        breakNow != null -> "Перемена (${breakNow.breakAfterMinutes} мин)"
                                        nextLesson != null -> "Сейчас нет урока"
                                        else -> "Уроков на сегодня нет"
                                    },
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                )
                                if (nextLesson != null) {
                                    Text(
                                        text = "Следующий: урок ${nextLesson.lessonNumber} в ${nextLesson.timeStart} — ${nextLesson.subjectRaw}",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 13.sp,
                                            color = Color(0xFFD9CBA8)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Преподаватели, которые ведут у группы
            if (insights.activeNames.isNotEmpty()) {
                item {
                    Text(
                        text = "ПРЕПОДАВАТЕЛИ ГРУППЫ (${insights.activeNames.size})",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ADMIN_ACCENT
                        )
                    )
                }
                items(insights.activeNames.sorted()) { teacherName ->
                    val rooms = insights.rooms(teacherName)
                    val subjects = insights.subjects(teacherName)
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBgMain,
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                text = teacherName,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ColorTextTitle
                                )
                            )
                            if (subjects.isNotEmpty()) {
                                Text(
                                    text = subjects.take(3).joinToString("; "),
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorTextMuted
                                    ),
                                    maxLines = 2
                                )
                            }
                            if (rooms.isNotEmpty()) {
                                Text(
                                    text = "каб. ${rooms.take(4).joinToString(", ")}",
                                    style = androidx.compose.ui.text.TextStyle(
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

            // Предметы группы
            val subjects = allGroupLessons.map { it.subjectRaw }.filter { it.isNotBlank() }.distinct().sorted()
            if (subjects.isNotEmpty()) {
                item {
                    Text(
                        text = "ПРЕДМЕТЫ В РАСПИСАНИИ (${subjects.size})",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ADMIN_ACCENT
                        )
                    )
                }
                items(subjects) { subject ->
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorSurfaceVariantLight,
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = subject,
                            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ColorTextBody),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Расписание группы на сегодня
            item {
                Text(
                    text = "РАСПИСАНИЕ ГРУППЫ ${student.group} НА СЕГОДНЯ",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ColorBrandBlue
                    )
                )
            }
            if (lessons.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorSurfaceVariantLight,
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isLoading) "Загружаем расписание…" else "На сегодня уроков нет",
                            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                items(lessons) { lesson ->
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorSurfaceVariantLight,
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${lesson.lessonNumber}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ColorBrandBlue
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lesson.subjectRaw,
                                    style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ColorTextBody)
                                )
                                Text(
                                    text = listOf(
                                        "${lesson.timeStart}–${lesson.timeEnd}",
                                        lesson.teacherFirst,
                                        lesson.roomFirst.takeIf { it.isNotBlank() }?.let { "каб. $it" } ?: ""
                                    ).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = ColorTextMuted)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier
            .size(36.dp)
            .bouncyClickable(onClick = onBack)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = ColorBrandBlue,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label.uppercase(),
            style = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = ColorTextMuted
            )
        )
        Text(
            text = value,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp,
                color = ColorTextBody
            )
        )
    }
}
