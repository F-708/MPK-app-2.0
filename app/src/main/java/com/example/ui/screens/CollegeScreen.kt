package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GroupInfo
import com.example.data.model.SyncDiagnosticInfo
import com.example.data.repository.ScheduleRepository
import com.example.data.repository.Student
import com.example.data.repository.StudentsRepository
import com.example.data.repository.TeacherInsights
import com.example.data.repository.Teacher
import com.example.data.repository.TeachersRepository
import com.example.ui.theme.ColorActiveBlue
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorDividerLight
import com.example.ui.theme.ColorSurfaceHighlight
import com.example.ui.theme.ColorSurfaceVariantLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.theme.ColorTopBar
import com.example.ui.theme.TextStylePageTitle
import com.example.ui.util.bouncyClickable
import com.example.util.MpkCurriculum

/** Тёмно-золотая палитра админ-разделов. */
private val ADMIN_ACCENT = Color(0xFF7A5C00)
private val ADMIN_BG = Color(0xFF2A2313)
private val ADMIN_GOLD = Color(0xFFE8C55A)

private enum class OtherPage { MENU, SPECIALTY, TEACHERS, TEACHER_CARD, SETTINGS, STUDENTS, STUDENT_CARD }

/**
 * Вкладка «Другое»: специальность, преподаватели, настройки, сайт колледжа.
 * В admin-версии дополнительно: база данных учащихся и поиск ученика.
 */
@Composable
fun CollegeScreen(
    groupInfo: GroupInfo,
    scheduleRepository: ScheduleRepository,
    diagnosticInfo: SyncDiagnosticInfo,
    onGroupChanged: (String) -> Unit,
    onRunConnectionTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var page by rememberSaveable { mutableStateOf(OtherPage.MENU) }
    var selectedStudent by remember { mutableStateOf<Student?>(null) }
    var selectedTeacher by remember { mutableStateOf<Teacher?>(null) }
    BackHandler(enabled = page != OtherPage.MENU) { page = OtherPage.MENU }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        when (page) {
            OtherPage.MENU -> OtherMenu(
                groupInfo = groupInfo,
                onOpenSpecialty = { page = OtherPage.SPECIALTY },
                onOpenTeachers = { page = OtherPage.TEACHERS },
                onOpenSettings = { page = OtherPage.SETTINGS },
                onOpenStudents = { page = OtherPage.STUDENTS }
            )
            OtherPage.SPECIALTY -> MySpecialtyPage(groupInfo) { page = OtherPage.MENU }
            OtherPage.TEACHERS -> TeachersPage(
                groupInfo = groupInfo,
                scheduleRepository = scheduleRepository,
                onBack = { page = OtherPage.MENU },
                onOpenTeacher = { teacher ->
                    selectedTeacher = teacher
                    page = OtherPage.TEACHER_CARD
                }
            )
            OtherPage.TEACHER_CARD -> {
                val teacher = selectedTeacher
                if (teacher != null) {
                    TeacherCardPageWrapper(
                        teacher = teacher,
                        groupInfo = groupInfo,
                        scheduleRepository = scheduleRepository,
                        onBack = { page = OtherPage.TEACHERS }
                    )
                } else {
                    page = OtherPage.TEACHERS
                }
            }
            OtherPage.SETTINGS -> SettingsPage(
                groupInfo = groupInfo,
                diagnosticInfo = diagnosticInfo,
                onGroupChanged = onGroupChanged,
                onRunConnectionTest = onRunConnectionTest,
                onBack = { page = OtherPage.MENU }
            )
            OtherPage.STUDENTS -> StudentsDatabaseScreen(
                onBack = { page = OtherPage.MENU },
                onOpenStudent = { student ->
                    selectedStudent = student
                    page = OtherPage.STUDENT_CARD
                }
            )
            OtherPage.STUDENT_CARD -> {
                val student = selectedStudent
                if (student != null) {
                    StudentCardScreen(
                        student = student,
                        scheduleRepository = scheduleRepository,
                        onBack = { page = OtherPage.MENU }
                    )
                } else {
                    OtherMenu(
                        groupInfo = groupInfo,
                        onOpenSpecialty = { page = OtherPage.SPECIALTY },
                        onOpenTeachers = { page = OtherPage.TEACHERS },
                        onOpenSettings = { page = OtherPage.SETTINGS },
                        onOpenStudents = { page = OtherPage.STUDENTS }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Список разделов
// ---------------------------------------------------------------------------

@Composable
private fun OtherMenu(
    groupInfo: GroupInfo,
    onOpenSpecialty: () -> Unit,
    onOpenTeachers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStudents: () -> Unit
) {
    val context = LocalContext.current
    val isAdmin = com.example.BuildConfig.FLAVOR == "admin"

    Column(modifier = Modifier.fillMaxSize()) {
        Header(title = "Другое", subtitle = "Специальность, преподаватели и настройки")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isAdmin) {
                // === Админ-разделы (тёмно-золотой стиль) ===
                item {
                    AdminMenuCard(
                        icon = { Icon(Icons.Default.AdminPanelSettings, null, tint = ADMIN_GOLD, modifier = Modifier.size(20.dp)) },
                        title = "База данных учащихся",
                        subtitle = "Все учащиеся колледжа: фильтры по группам и курсам",
                        badge = "ADMIN",
                        onClick = onOpenStudents
                    )
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ColorDividerLight)
                    )
                }
            }

            item {
                MenuCard(
                    icon = { Icon(Icons.Default.School, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Моя специальность",
                    subtitle = "Группа ${groupInfo.canonicalName}: код, квалификация, предметы по курсам",
                    onClick = onOpenSpecialty
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Преподаватели",
                    subtitle = "Поиск по базе, избранное и фотографии",
                    onClick = onOpenTeachers
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Настройки",
                    subtitle = "Группа, уведомления и диагностика",
                    onClick = onOpenSettings
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.Language, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Сайт колледжа",
                    subtitle = "guo-mpk.by — официальная информация",
                    onClick = {
                        try {
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://guo-mpk.by/")
                            ).let { context.startActivity(it) }
                        } catch (_: Exception) {
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MenuCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ColorTextTitle
                    )
                )
                Text(
                    text = subtitle,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = ColorTextMuted
                    ),
                    maxLines = 2
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ColorTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Админская карточка: тёмно-золотой стиль с бейджем ADMIN. */
@Composable
private fun AdminMenuCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ADMIN_BG,
        border = BorderStroke(1.dp, ADMIN_ACCENT),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(ADMIN_ACCENT.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ADMIN_GOLD
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ADMIN_ACCENT,
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                color = ADMIN_GOLD
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = Color(0xFFB8A87F)
                    ),
                    maxLines = 2
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ADMIN_GOLD,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Страница настроек (встроенный SettingsScreen с шапкой «Назад»)
// ---------------------------------------------------------------------------

@Composable
private fun SettingsPage(
    groupInfo: GroupInfo,
    diagnosticInfo: SyncDiagnosticInfo,
    onGroupChanged: (String) -> Unit,
    onRunConnectionTest: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubPageBackButton(onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Настройки",
                style = TextStylePageTitle.copy(fontSize = 24.sp),
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorDividerLight)
        )

        SettingsScreen(
            groupInfo = groupInfo,
            diagnosticInfo = diagnosticInfo,
            onGroupChanged = onGroupChanged,
            onRunConnectionTest = onRunConnectionTest
        )
    }
}

// ---------------------------------------------------------------------------
// Под-экран «Моя специальность»
// ---------------------------------------------------------------------------

@Composable
private fun MySpecialtyPage(groupInfo: GroupInfo, onBack: () -> Unit) {
    val specialty = remember(groupInfo.specialtyCode) {
        MpkCurriculum.getSpecialty(groupInfo.specialtyCode)
    }
    val courses = specialty?.subjectsByCourse?.keys?.sorted() ?: emptyList()
    var selectedCourse by rememberSaveable(groupInfo.specialtyCode) {
        mutableIntStateOf(groupInfo.course.coerceIn(courses.minOrNull() ?: 1, courses.maxOrNull() ?: 1))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubPageBackButton(onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Моя специальность",
                    style = TextStylePageTitle.copy(fontSize = 24.sp),
                    maxLines = 1
                )
                Text(
                    text = "Группа ${groupInfo.canonicalName}",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = ColorBrandBlue
                    ),
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorDividerLight)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (specialty == null) {
                item {
                    EmptyCard(text = "Специальность для группы ${groupInfo.canonicalName} не найдена в справочнике.")
                }
                return@LazyColumn
            }

            // Отсек 1: специальность, код, квалификация
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("specialty_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "СПЕЦИАЛЬНОСТЬ",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = ColorTextMuted
                            )
                        )
                        Text(
                            text = specialty.fullName,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ColorTextTitle
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorSurfaceHighlight,
                            border = BorderStroke(1.dp, ColorBrandBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "КОД СПЕЦИАЛЬНОСТИ",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = ColorTextMuted
                                    )
                                )
                                Text(
                                    text = specialty.cipher,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = ColorBrandBlue
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (specialty.qualification.isNotBlank()) {
                            InfoRow("Квалификация", specialty.qualification)
                        }
                    }
                }
            }

            // Отсек 2: рабочие профессии
            if (specialty.workerProfessions.isNotEmpty()) {
                item { SectionLabel("РАБОЧИЕ ПРОФЕССИИ") }
                items(specialty.workerProfessions) { profession ->
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorSurfaceVariantLight,
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = profession,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = ColorTextBody
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Отсек 3: предметы по курсам
            item { SectionLabel("ПРЕДМЕТЫ ПО КУРСАМ") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    courses.forEach { course ->
                        val isSelected = course == selectedCourse
                        val isCurrent = course == groupInfo.course
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) ColorTopBar else if (isCurrent) ColorBrandBlue else ColorBorderLight
                            ),
                            color = if (isSelected) ColorTopBar else ColorBgMain,
                            modifier = Modifier
                                .weight(1f)
                                .bouncyClickable { selectedCourse = course }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "$course КУРС",
                                    softWrap = false,
                                    maxLines = 1,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color.White else ColorTextBody
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val subjects = specialty.subjectsByCourse[selectedCourse].orEmpty()
            item {
                Text(
                    text = "ПРЕДМЕТЫ ${selectedCourse}-ГО КУРСА (${subjects.size})",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ColorBrandBlue
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
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = ColorTextBody
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Под-экран «Преподаватели» (101 сотрудник + активные из расписания группы)
// ---------------------------------------------------------------------------

/** Строка списка преподавателей: пустая = заголовок-разделитель. */
private sealed interface TeacherRow {
    data class Header(val title: String) : TeacherRow
    data class Item(val teacher: Teacher) : TeacherRow
}

@Composable
private fun TeachersPage(
    groupInfo: GroupInfo,
    scheduleRepository: ScheduleRepository,
    onBack: () -> Unit,
    onOpenTeacher: (Teacher) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { TeachersRepository(context) }
    val teachers = remember { repository.loadTeachers() }

    // Все известные уроки группы (все даты в кэше) — для активных преподавателей
    val allLessons by scheduleRepository
        .getAllLessonsForGroup(groupInfo.canonicalName)
        .collectAsState(initial = emptyList())

    val insights = remember(teachers, allLessons) {
        TeacherInsights(teachers, allLessons)
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val rows = remember(teachers, searchQuery, insights) {
        val q = searchQuery.trim().lowercase()
        val filtered = if (q.isBlank()) teachers else teachers.filter {
            it.name.lowercase().contains(q) ||
                it.subjects.lowercase().contains(q) ||
                it.position.lowercase().contains(q) ||
                it.department.lowercase().contains(q) ||
                insights.rooms(it.name).any { room -> room.contains(q) }
        }

        val active = filtered.filter { insights.isActive(it.name) }.sortedBy { it.name }
        val rest = filtered.filterNot { insights.isActive(it.name) }.sortedBy { it.name }

        buildList {
            if (active.isNotEmpty()) {
                add(TeacherRow.Header("ВЕДУТ У ГРУППЫ ${groupInfo.canonicalName} (${active.size})"))
                active.forEach { add(TeacherRow.Item(it)) }
            }
            if (rest.isNotEmpty()) {
                if (active.isNotEmpty()) {
                    add(TeacherRow.Header("ВСЕ ПРЕПОДАВАТЕЛИ (${rest.size})"))
                }
                rest.forEach { add(TeacherRow.Item(it)) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubPageBackButton(onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Преподаватели",
                    style = TextStylePageTitle.copy(fontSize = 22.sp),
                    maxLines = 1
                )
                Text(
                    text = "${teachers.size} сотрудников • ${insights.activeNames.size} ведут у ${groupInfo.canonicalName}",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = ColorBrandBlue
                    ),
                    maxLines = 1
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
            placeholder = { Text("Поиск: ФИО, дисциплина, кабинет...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = ColorBrandBlue, modifier = Modifier.size(18.dp))
            },
            singleLine = true,
            shape = RoundedCornerShape(2.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ColorSurfaceVariantLight,
                unfocusedContainerColor = ColorSurfaceVariantLight,
                focusedIndicatorColor = ColorBrandBlue,
                unfocusedIndicatorColor = ColorBorderLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("teachers_search_input")
        )

        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ничего не найдено по запросу «$searchQuery»",
                    style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = ColorTextMuted)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(rows) { row ->
                    when (row) {
                        is TeacherRow.Header -> Text(
                            text = row.title,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = ColorBrandBlue
                            ),
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                        is TeacherRow.Item -> TeacherCard(
                            teacher = row.teacher,
                            isActive = insights.isActive(row.teacher.name),
                            roomsFromSchedule = insights.rooms(row.teacher.name),
                            onClick = { onOpenTeacher(row.teacher) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Карточка преподавателя в списке.
 * Если кабинета нет ни в базе, ни в расписании — строка про кабинет не выводится.
 */
@Composable
private fun TeacherCard(
    teacher: Teacher,
    isActive: Boolean,
    roomsFromSchedule: List<String>,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, if (isActive) ColorActiveBlue else ColorBorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeacherAvatar(teacher = teacher, size = 46.dp, highlight = isActive)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorActiveBlue,
                        modifier = Modifier.padding(bottom = 3.dp)
                    ) {
                        Text(
                            text = "ВЕДЁТ У ВАС",
                            softWrap = false,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = Color.White
                            ),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                Text(
                    text = teacher.name,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ColorTextTitle
                    ),
                    maxLines = 1
                )

                val details = listOf(teacher.position, teacher.subjects)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = ColorTextMuted
                        ),
                        maxLines = 2
                    )
                }

                // Кабинет: личный из базы, иначе — из расписания; нет данных — строку не показываем
                val roomText = when {
                    teacher.room.isNotBlank() -> "каб. ${teacher.room}"
                    roomsFromSchedule.isNotEmpty() -> "каб. ${roomsFromSchedule.take(3).joinToString(", ")}"
                    else -> ""
                }
                if (roomText.isNotBlank()) {
                    Text(
                        text = roomText,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            color = ColorBrandBlue
                        ),
                        maxLines = 1
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ColorTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Фото преподавателя (или инициалы, если снимка нет). */
@Composable
private fun TeacherAvatar(
    teacher: Teacher,
    size: androidx.compose.ui.unit.Dp,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    val border = if (highlight) ColorActiveBlue else ColorBorderLight
    if (teacher.photo.isNotBlank()) {
        coil.compose.AsyncImage(
            model = "file:///android_asset/teachers_photos/" + teacher.photo,
            contentDescription = teacher.name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier
                .size(size)
                .border(1.dp, border, RoundedCornerShape(2.dp))
                .background(ColorSurfaceHighlight, RoundedCornerShape(2.dp))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(ColorBrandFill, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = teacher.name.split(" ").firstOrNull()?.take(1) ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp
            )
        }
    }
}

/**
 * Обёртка карточки преподавателя: собирает производные данные
 * (кабинеты, дисциплины, активность) из расписания группы.
 */
@Composable
private fun TeacherCardPageWrapper(
    teacher: Teacher,
    groupInfo: GroupInfo,
    scheduleRepository: ScheduleRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { TeachersRepository(context) }
    val teachers = remember { repository.loadTeachers() }
    val allLessons by scheduleRepository
        .getAllLessonsForGroup(groupInfo.canonicalName)
        .collectAsState(initial = emptyList())

    val insights = remember(teachers, allLessons) { TeacherInsights(teachers, allLessons) }

    TeacherCardPage(
        teacher = teacher,
        roomsFromSchedule = insights.rooms(teacher.name),
        subjectsFromSchedule = insights.subjects(teacher.name),
        isActive = insights.isActive(teacher.name),
        lessonsWithGroup = insights.lessonCount(teacher.name),
        onBack = onBack
    )
}

/**
 * Полноэкранная карточка преподавателя: крупное фото и полная информация.
 */
@Composable
private fun TeacherCardPage(
    teacher: Teacher,
    roomsFromSchedule: List<String>,
    subjectsFromSchedule: List<String>,
    isActive: Boolean,
    lessonsWithGroup: Int,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubPageBackButton(onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Преподаватель",
                style = TextStylePageTitle.copy(fontSize = 22.sp),
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorDividerLight)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Фото + ФИО + должность
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, if (isActive) ColorActiveBlue else ColorBorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("teacher_card")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Крупный портрет
                        if (teacher.photo.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = "file:///android_asset/teachers_photos/" + teacher.photo,
                                contentDescription = teacher.name,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(180.dp)
                                    .border(1.dp, ColorBorderLight, RoundedCornerShape(2.dp))
                                    .background(ColorSurfaceHighlight, RoundedCornerShape(2.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = teacher.name.split(" ").firstOrNull()?.take(1) ?: "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 64.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = ColorActiveBlue,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = "ВЕДЁТ У ВАШЕЙ ГРУППЫ",
                                    softWrap = false,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = Color.White
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = teacher.name,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = ColorTextTitle
                            ),
                            textAlign = TextAlign.Center
                        )

                        if (teacher.position.isNotBlank()) {
                            Text(
                                text = teacher.position,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    color = ColorBrandBlue,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Контакты и служебные сведения
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val hasContacts = teacher.room.isNotBlank() ||
                            teacher.phone.isNotBlank() || teacher.email.isNotBlank()
                        if (hasContacts) {
                            SectionLabel("КОНТАКТЫ И КАБИНЕТ")
                            if (teacher.room.isNotBlank()) InfoRow("Кабинет", teacher.room)
                            if (teacher.phone.isNotBlank()) InfoRow("Телефон", teacher.phone)
                            if (teacher.email.isNotBlank()) InfoRow("E-mail", teacher.email)
                        }

                        val hasService = teacher.department.isNotBlank() ||
                            teacher.experience.isNotBlank() || teacher.category.isNotBlank()
                        if (hasService) {
                            if (hasContacts) Spacer(modifier = Modifier.height(10.dp))
                            SectionLabel("СЛУЖЕБНЫЕ СВЕДЕНИЯ")
                            if (teacher.department.isNotBlank()) InfoRow("Подразделение", teacher.department)
                            if (teacher.experience.isNotBlank()) InfoRow("Педагогический стаж", teacher.experience)
                            if (teacher.category.isNotBlank()) InfoRow("Квалификационная категория", teacher.category)
                        }
                    }
                }
            }

            // Дисциплины из официальной базы
            if (teacher.subjects.isNotBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorSurfaceVariantLight,
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SectionLabel("ПРЕПОДАВАЕМЫЕ ДИСЦИПЛИНЫ")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = teacher.subjects,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    color = ColorTextBody
                                )
                            )
                        }
                    }
                }
            }

            // Связь с расписанием группы: предметы и кабинеты
            if (isActive && (subjectsFromSchedule.isNotEmpty() || roomsFromSchedule.isNotEmpty() || lessonsWithGroup > 0)) {
                item {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBgMain,
                        border = BorderStroke(1.dp, ColorActiveBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SectionLabel("У ВАШЕЙ ГРУППЫ")
                            Spacer(modifier = Modifier.height(6.dp))

                            if (lessonsWithGroup > 0) {
                                InfoRow("Уроков в базе", "$lessonsWithGroup")
                            }
                            if (roomsFromSchedule.isNotEmpty()) {
                                InfoRow("Ведёт в кабинетах", roomsFromSchedule.joinToString(", "))
                            }
                            if (subjectsFromSchedule.isNotEmpty()) {
                                InfoRow("Предметы", subjectsFromSchedule.take(5).joinToString("; "))
                            }
                        }
                    }
                }
            }
        }
    }
}
// ---------------------------------------------------------------------------
// Общие элементы
// ---------------------------------------------------------------------------

@Composable
private fun SubPageBackButton(onBack: () -> Unit) {
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
private fun Header(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = title,
            style = TextStylePageTitle,
            maxLines = 1
        )
        Text(
            text = subtitle,
            style = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = ColorBrandBlue
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = androidx.compose.ui.text.TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = ColorBrandBlue
        )
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
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

@Composable
private fun EmptyCard(text: String) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorSurfaceVariantLight,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp,
                color = ColorTextMuted
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}
