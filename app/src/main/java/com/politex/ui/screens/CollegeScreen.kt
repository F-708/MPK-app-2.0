package com.politex.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.model.GroupInfo
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.Student
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherInsights
import com.politex.ui.components.AppCard
import com.politex.ui.components.AppDivider
import com.politex.ui.components.BackButton
import com.politex.ui.components.SearchBar
import com.politex.ui.components.SectionCaption
import com.politex.ui.components.SearchEmptyState
import com.politex.ui.components.SubPageHeader
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberToday
import com.politex.util.MpkCurriculum
import java.util.Calendar

private val ADMIN_ACCENT = Color(0xFF7A5C00)
private val ADMIN_BG = Color(0xFF2A2313)
private val ADMIN_GOLD = Color(0xFFE8C55A)

@Composable
fun CollegeScreen(
    groupInfo: GroupInfo,
    onOpenSpecialty: () -> Unit = {},
    onOpenTeachers: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenStudents: () -> Unit = {},
    onOpenGroupSchedule: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        OtherMenu(
            groupInfo = groupInfo,
            onOpenSpecialty = onOpenSpecialty,
            onOpenTeachers = onOpenTeachers,
            onOpenSettings = onOpenSettings,
            onOpenStudents = onOpenStudents,
            onOpenGroupSchedule = onOpenGroupSchedule,
            onOpenMap = onOpenMap,
        )
    }
}

@Composable
internal fun SpecialtyRoute(
    groupInfo: GroupInfo,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(ColorBgMain)) {
        MySpecialtyPage(groupInfo, onBack)
    }
}

@Composable
internal fun TeachersRoute(
    groupInfo: GroupInfo,
    scheduleRepository: ScheduleRepository,
    onOpenTeacher: (Teacher) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(ColorBgMain)) {
        TeachersPage(
            groupInfo = groupInfo,
            scheduleRepository = scheduleRepository,
            onBack = onBack,
            onOpenTeacher = onOpenTeacher,
        )
    }
}

@Composable
internal fun TeacherCardRoute(
    teacherName: String,
    groupInfo: GroupInfo,
    scheduleRepository: ScheduleRepository,
    onOpenRoom: (String) -> Unit,
    onNotFound: () -> Unit,
    onBack: () -> Unit,
) {
    var teacher by remember(teacherName) { mutableStateOf<Teacher?>(null) }
    LaunchedEffect(teacherName) {
        // R2#8: общий кэш + загрузка вне main thread
        teacher =
            com.politex.ui.util.TeachersCache
                .load()
                .firstOrNull {
                    TeacherInsights.surnameOf(it.name) == TeacherInsights.surnameOf(teacherName)
                }
        if (teacher == null) onNotFound()
    }
    Column(modifier = Modifier.fillMaxSize().background(ColorBgMain)) {
        val found = teacher
        if (found == null) {
            // R2#19: пока карточка грузится (и до popBackStack при onNotFound) — лоадер, не пустой экран
            com.politex.ui.components.CenteredLoader(modifier = Modifier.fillMaxSize())
        } else {
            TeacherCardPageWrapper(
                teacher = found,
                groupInfo = groupInfo,
                scheduleRepository = scheduleRepository,
                onOpenRoom = onOpenRoom,
                onBack = onBack,
            )
        }
    }
}

@Composable
internal fun SettingsRoute(
    groupInfo: GroupInfo,
    onGroupChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(ColorBgMain)) {
        SettingsPage(
            groupInfo = groupInfo,
            onGroupChanged = onGroupChanged,
            onBack = onBack,
        )
    }
}

@Composable
internal fun StudentsRoute(
    scheduleRepository: ScheduleRepository,
    onBack: () -> Unit,
) {
    var selectedStudent by remember { mutableStateOf<Student?>(null) }
    BackHandler(enabled = selectedStudent != null) { selectedStudent = null }
    Column(modifier = Modifier.fillMaxSize().background(ColorBgMain)) {
        val student = selectedStudent
        if (student == null) {
            StudentsDatabaseScreen(
                onBack = onBack,
                onOpenStudent = { selectedStudent = it },
            )
        } else {
            StudentCardScreen(
                student = student,
                scheduleRepository = scheduleRepository,
                onBack = { selectedStudent = null },
            )
        }
    }
}

@Composable
private fun OtherMenu(
    groupInfo: GroupInfo,
    onOpenSpecialty: () -> Unit,
    onOpenTeachers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStudents: () -> Unit,
    onOpenGroupSchedule: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val context = LocalContext.current
    val isAdmin = com.politex.BuildConfig.FLAVOR == "admin"

    Column(modifier = Modifier.fillMaxSize()) {
        Header(title = "Другое", subtitle = "Специальность, преподаватели и настройки")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isAdmin) {
                item {
                    AdminMenuCard(
                        icon = { Icon(Icons.Default.AdminPanelSettings, null, tint = ADMIN_GOLD, modifier = Modifier.size(20.dp)) },
                        title = "База данных учащихся",
                        subtitle = "Все учащиеся колледжа: фильтры по группам и курсам",
                        badge = "ADMIN",
                        onClick = onOpenStudents,
                    )
                }
                item {
                    AppDivider()
                }
            }

            item {
                MenuCard(
                    icon = { Icon(Icons.Default.School, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Моя специальность",
                    subtitle = "Группа ${groupInfo.canonicalName}: код, квалификация, предметы по курсам",
                    onClick = onOpenSpecialty,
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.DateRange, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Расписание группы",
                    subtitle = "Найти расписание любой группы колледжа",
                    onClick = onOpenGroupSchedule,
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Карта колледжа",
                    subtitle = "Планы этажей и поиск кабинета",
                    onClick = onOpenMap,
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Преподаватели",
                    subtitle = "Поиск по базе, избранное и фотографии",
                    onClick = onOpenTeachers,
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Настройки",
                    subtitle = "Группа, уведомления и диагностика",
                    onClick = onOpenSettings,
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
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                Modifier
                    .size(36.dp)
                    .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ColorTextTitle,
                    ),
                )
                Text(
                    text = subtitle,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = ColorTextMuted,
                    ),
                    maxLines = 2,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ColorTextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AdminMenuCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ADMIN_BG,
        border = BorderStroke(1.dp, ADMIN_ACCENT),
        modifier =
        Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                Modifier
                    .size(36.dp)
                    .background(ADMIN_ACCENT.copy(alpha = 0.25f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ADMIN_GOLD,
                        ),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ADMIN_ACCENT,
                        modifier = Modifier.padding(start = 2.dp),
                    ) {
                        Text(
                            text = badge,
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                color = ADMIN_GOLD,
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = Color(0xFFB8A87F),
                    ),
                    maxLines = 2,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ADMIN_GOLD,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsPage(
    groupInfo: GroupInfo,
    onGroupChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(
            title = "Настройки",
            subtitle = "Параметры и диагностика приложения",
            onBack = onBack,
        )

        SettingsScreen(
            groupInfo = groupInfo,
            onGroupChanged = onGroupChanged,
        )
    }
}

@Composable
private fun MySpecialtyPage(
    groupInfo: GroupInfo,
    onBack: () -> Unit,
) {
    val specialty =
        remember(groupInfo.specialtyCode) {
            MpkCurriculum.getSpecialty(groupInfo.specialtyCode)
        }
    val courses = specialty?.subjectsByCourse?.keys?.sorted() ?: emptyList()
    var selectedCourse by rememberSaveable(groupInfo.specialtyCode) {
        mutableIntStateOf(groupInfo.course.coerceIn(courses.minOrNull() ?: 1, courses.maxOrNull() ?: 1))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(
            title = "Моя специальность",
            subtitle = "Группа ${groupInfo.canonicalName}",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (specialty == null) {
                item {
                    EmptyCard(text = "Специальность для группы ${groupInfo.canonicalName} не найдена в справочнике.")
                }
                return@LazyColumn
            }

            item {
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("specialty_card"),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "СПЕЦИАЛЬНОСТЬ",
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = ColorTextMuted,
                            ),
                        )
                        Text(
                            text = specialty.fullName,
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ColorTextTitle,
                            ),
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorSurfaceHighlight,
                            border = BorderStroke(1.dp, ColorBrandBlue),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                Text(
                                    text = "КОД СПЕЦИАЛЬНОСТИ",
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = ColorTextMuted,
                                    ),
                                )
                                Text(
                                    text = specialty.cipher,
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = ColorBrandBlue,
                                    ),
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

            if (specialty.workerProfessions.isNotEmpty()) {
                item { SectionCaption("РАБОЧИЕ ПРОФЕССИИ") }
                items(specialty.workerProfessions) { profession ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        color = ColorSurfaceVariantLight,
                    ) {
                        Text(
                            text = profession,
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = ColorTextBody,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            item { SectionCaption("ПРЕДМЕТЫ ПО КУРСАМ") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    courses.forEach { course ->
                        val isSelected = course == selectedCourse
                        val isCurrent = course == groupInfo.course
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border =
                            BorderStroke(
                                1.dp,
                                if (isSelected) {
                                    ColorTopBar
                                } else if (isCurrent) {
                                    ColorBrandBlue
                                } else {
                                    ColorBorderLight
                                },
                            ),
                            color = if (isSelected) ColorTopBar else ColorBgMain,
                            modifier =
                            Modifier
                                .weight(1f)
                                .bouncyClickable { selectedCourse = course },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                Text(
                                    text = "$course КУРС",
                                    softWrap = false,
                                    maxLines = 1,
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color.White else ColorTextBody,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            val subjects = specialty.subjectsByCourse[selectedCourse].orEmpty()

            val isIncomplete = selectedCourse in specialty.incompleteCourses
            if (isIncomplete) {
                item {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        color = ColorSurfaceVariantLight,
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "ПРЕДМЕТЫ $selectedCourse-ГО КУРСА",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = ColorBrandBlue,
                                ),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(

                                text =
                                "Список предметов для этого курса неполный — " +
                                    "уточните в деканате.",
                                style =
                                androidx.compose.ui.text
                                    .TextStyle(fontSize = 13.sp, color = ColorTextBody),
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "ПРЕДМЕТЫ $selectedCourse-ГО КУРСА (${subjects.size})",
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ColorBrandBlue,
                        ),
                    )
                }
                items(subjects) { subject ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        color = ColorSurfaceVariantLight,
                    ) {
                        Text(
                            text = subject,
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = ColorTextBody,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

private sealed interface TeacherRow {
    data class Header(
        val title: String,
    ) : TeacherRow

    data class Item(
        val teacher: Teacher,
    ) : TeacherRow
}

@Composable
private fun TeachersPage(
    groupInfo: GroupInfo,
    scheduleRepository: ScheduleRepository,
    onBack: () -> Unit,
    onOpenTeacher: (Teacher) -> Unit,
) {
    val context = LocalContext.current

    // R2#8: асинхронная загрузка базы преподавателей (была синхронно в композиции)
    val teachers = com.politex.ui.util.rememberTeachers()

    val allLessons by scheduleRepository
        .getAllLessonsForGroup(groupInfo.canonicalName)
        .collectAsState(initial = emptyList())

    val insights =
        remember(teachers, allLessons) {
            TeacherInsights(teachers, allLessons)
        }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val rows =
        remember(teachers, searchQuery, insights) {
            val q = searchQuery.trim().lowercase()
            val filtered =
                if (q.isBlank()) {
                    teachers
                } else {
                    teachers.filter {
                        it.name.lowercase().contains(q) ||
                            it.subjects.lowercase().contains(q) ||
                            it.position.lowercase().contains(q) ||
                            it.department.lowercase().contains(q) ||
                            insights.rooms(it.name).any { room -> room.contains(q) }
                    }
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
        SubPageHeader(
            title = "Преподаватели",
            subtitle = "${teachers.size} сотрудников • ${insights.activeNames.size} ведут у ${groupInfo.canonicalName}",
            onBack = onBack,
        )

        SearchBar(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Поиск: ФИО, дисциплина...",
            testTag = "teachers_search_input",
            modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (rows.isEmpty()) {
            SearchEmptyState(
                query = searchQuery,
                modifier = Modifier.fillMaxSize(),
                onReset = { searchQuery = "" },
                resetTestTag = "teachers_reset_btn",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(rows) { row ->
                    when (row) {
                        is TeacherRow.Header ->
                            Text(
                                text = row.title,
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = ColorBrandBlue,
                                ),
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        is TeacherRow.Item ->
                            TeacherCard(
                                teacher = row.teacher,
                                isActive = insights.isActive(row.teacher.name),
                                roomsFromSchedule = insights.rooms(row.teacher.name),
                                onClick = { onOpenTeacher(row.teacher) },
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherCard(
    teacher: Teacher,
    isActive: Boolean,
    roomsFromSchedule: List<String>,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, if (isActive) ColorActiveBlue else ColorBorderLight),
        modifier =
        Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeacherAvatar(teacher = teacher, size = 46.dp, highlight = isActive)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorActiveBlue,
                        modifier = Modifier.padding(bottom = 3.dp),
                    ) {
                        Text(
                            text = "ВЕДЁТ У ВАС",
                            softWrap = false,
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = Color.White,
                            ),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }

                Text(
                    text = teacher.name,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ColorTextTitle,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val details =
                    listOf(teacher.position, teacher.subjects)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = ColorTextMuted,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val roomText =
                    when {
                        teacher.room.isNotBlank() -> "каб. ${teacher.room}"
                        roomsFromSchedule.isNotEmpty() -> "каб. ${roomsFromSchedule.take(3).joinToString(", ")}"
                        else -> ""
                    }
                if (roomText.isNotBlank()) {
                    Text(
                        text = roomText,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            color = ColorBrandBlue,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ColorTextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TeacherAvatar(
    teacher: Teacher,
    size: androidx.compose.ui.unit.Dp,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val border = if (highlight) ColorActiveBlue else ColorBorderLight
    if (teacher.photo.isNotBlank()) {
        coil.compose.AsyncImage(
            model = "file:///android_asset/teachers_photos/" + teacher.photo,
            contentDescription = teacher.name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier =
            modifier
                .size(size)
                .border(1.dp, border, RoundedCornerShape(2.dp))
                .background(ColorSurfaceHighlight, RoundedCornerShape(2.dp)),
        )
    } else {
        Box(
            modifier =
            modifier
                .size(size)
                .background(ColorBrandFill, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                teacher.name
                    .split(" ")
                    .firstOrNull()
                    ?.take(1) ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}

@Composable
private fun TeacherCardPageWrapper(
    teacher: Teacher,
    groupInfo: GroupInfo,
    scheduleRepository: ScheduleRepository,
    onOpenRoom: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // R2#8: асинхронная загрузка базы преподавателей (была синхронно в композиции)
    val teachers = com.politex.ui.util.rememberTeachers()
    val allLessons by scheduleRepository
        .getAllLessonsForGroup(groupInfo.canonicalName)
        .collectAsState(initial = emptyList())

    var leadsTick by remember { mutableIntStateOf(0) }

    val insights =
        remember(teachers, allLessons, leadsTick) {
            TeacherInsights(
                teachers,
                allLessons,

                leadsOffTs = { LeadsMarks.offTs(context, it) },
                leadsOn = { LeadsMarks.isOn(context, it) },
            )
        }

    TeacherCardPage(
        teacher = teacher,
        roomsFromSchedule = insights.rooms(teacher.name),
        subjectsFromSchedule = insights.subjects(teacher.name),
        isActive = insights.isLeadsNow(teacher.name),
        autoActive = insights.isActive(teacher.name),
        lessonsWithGroup = insights.lessonCount(teacher.name),
        onLeadsChipClick = {

            val name = teacher.name
            when {
                insights.isLeadsNow(name) -> LeadsMarks.markOff(context, name, System.currentTimeMillis())
                insights.isActive(name) -> LeadsMarks.clear(context, name)
                else -> LeadsMarks.markOn(context, name)
            }
            leadsTick++
        },
        onOpenRoom = onOpenRoom,
        onBack = onBack,
    )
}

private object LeadsMarks {
    private const val PREFS = "mpk_prefs"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(
        name: String,
        prefix: String,
    ) = "${prefix}_${TeacherInsights.surnameOf(name)}"

    fun offTs(
        ctx: Context,
        name: String,
    ): Long? {
        val p = prefs(ctx)
        val k = key(name, "leads_off")
        return if (p.contains(k)) p.getLong(k, 0L) else null
    }

    fun isOn(
        ctx: Context,
        name: String,
    ): Boolean = prefs(ctx).getBoolean(key(name, "leads_on"), false)

    fun markOff(
        ctx: Context,
        name: String,
        ts: Long,
    ) {
        prefs(ctx)
            .edit()
            .putLong(key(name, "leads_off"), ts)
            .remove(key(name, "leads_on"))
            .apply()
    }

    fun markOn(
        ctx: Context,
        name: String,
    ) {
        prefs(ctx)
            .edit()
            .putBoolean(key(name, "leads_on"), true)
            .remove(key(name, "leads_off"))
            .apply()
    }

    fun clear(
        ctx: Context,
        name: String,
    ) {
        prefs(ctx)
            .edit()
            .remove(key(name, "leads_off"))
            .remove(key(name, "leads_on"))
            .apply()
    }
}

@Composable
private fun TeacherCardPage(
    teacher: Teacher,
    roomsFromSchedule: List<String>,
    subjectsFromSchedule: List<String>,
    isActive: Boolean,
    autoActive: Boolean = false,
    lessonsWithGroup: Int,
    onLeadsChipClick: () -> Unit = {},
    onOpenRoom: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("О преподавателе", "Расписание")

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Spacer(modifier = Modifier.width(10.dp))
            TeacherAvatar(teacher = teacher, size = 52.dp, highlight = isActive)
            Spacer(modifier = Modifier.width(10.dp))
            // EE5: шильд «Ведёт у вас» перенесён в ряд кнопок-секций ниже —
            // ФИО занимает всю ширину шапки без перекрытия.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = teacher.name,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ColorTextTitle,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (teacher.position.isNotBlank()) {
                    Text(
                        text = teacher.position,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            color = ColorBrandBlue,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        AppDivider()

        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(tabs) { title ->
                val index = tabs.indexOf(title)
                val selected = tab == index
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
                    color = if (selected) ColorTopBar else ColorBgMain,
                    modifier =
                    Modifier

                        .heightIn(min = 44.dp)
                        .bouncyClickable { tab = index },
                ) {

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title.uppercase(),
                            softWrap = false,
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp,
                                color = if (selected) Color.White else ColorTextBody,
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            item {
                LeadsChip(
                    leadsNow = isActive,
                    autoActive = autoActive,
                    onClick = onLeadsChipClick,
                )
            }
        }

        AppDivider()

        when (tab) {
            0 ->
                AboutTeacherTab(
                    teacher,
                    isActive,
                    lessonsWithGroup,
                    roomsFromSchedule,
                    subjectsFromSchedule,
                    onOpenRoom,
                )
            1 -> TeacherScheduleTab(teacher, onOpenRoom)
        }
    }
}

@Composable
private fun LeadsChip(
    leadsNow: Boolean,
    autoActive: Boolean,
    onClick: () -> Unit,
) {
    // AA4: авто-факт активен и пользователь его не снимал — шильд не кликабельен
    // («скрипт уверен, что ведёт»); возврат/установка отметки — только в остальных состояниях.
    val enabled = canToggleLeads(autoActive, manualOff = autoActive && !leadsNow)
    val text =
        when {
            leadsNow -> "ВЕДЁТ У ВАС"
            autoActive -> "ОТМЕТКА СНЯТА"
            else -> "+ ВЕДЁТ У ВАС"
        }
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (leadsNow) ColorActiveBlue else ColorSurfaceVariantLight,
        border = BorderStroke(1.dp, if (leadsNow) ColorActiveBlue else ColorBorderLight),
        modifier =
        Modifier
            // EE5: в ряду кнопок-секций — та же высота и отступы, что у соседних кнопок.
            .heightIn(min = 44.dp)
            .then(
                if (enabled) {
                    Modifier.bouncyClickable(scaleDown = 0.97f, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .testTag("teacher_leads_chip"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(
                text = text,
                softWrap = false,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (leadsNow) Color.White else ColorTextMuted,
                ),
            )
        }
    }
}

@Composable
private fun AboutTeacherTab(
    teacher: Teacher,
    isActive: Boolean,
    lessonsWithGroup: Int,
    roomsFromSchedule: List<String>,
    subjectsFromSchedule: List<String>,
    onOpenRoom: (String) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    if (teacher.photo.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = "file:///android_asset/teachers_photos/" + teacher.photo,
                            contentDescription = teacher.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier =
                            Modifier
                                .width(180.dp)
                                .heightIn(max = 260.dp)
                                .border(1.dp, ColorBorderLight, RoundedCornerShape(2.dp))
                                .background(ColorSurfaceHighlight, RoundedCornerShape(2.dp)),
                        )
                    } else {
                        Box(
                            modifier =
                            Modifier
                                .width(180.dp)
                                .height(230.dp)
                                .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text =
                                teacher.name
                                    .split(" ")
                                    .firstOrNull()
                                    ?.take(1) ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 60.sp,
                            )
                        }
                    }
                }
            }
        }

        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SectionCaption("О ПРЕПОДАВАТЕЛЕ")
                    Spacer(modifier = Modifier.height(6.dp))
                    if (teacher.position.isNotBlank()) InfoRow("Должность", teacher.position)
                    if (teacher.department.isNotBlank()) InfoRow("Подразделение", teacher.department)
                    if (teacher.experience.isNotBlank()) InfoRow("Педагогический стаж", teacher.experience)
                    if (teacher.category.isNotBlank()) InfoRow("Квалификационная категория", teacher.category)

                    val nothing =
                        teacher.position.isBlank() &&
                            teacher.department.isBlank() &&
                            teacher.experience.isBlank() &&
                            teacher.category.isBlank()
                    if (nothing) {
                        Text(
                            text = "Сведения в базе не заполнены",
                            style =
                            androidx.compose.ui.text
                                .TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                        )
                    }
                }
            }
        }

        item { SectionCaption("ПРЕПОДАВАЕМЫЕ ДИСЦИПЛИНЫ") }
        if (teacher.subjects.isBlank()) {
            item { EmptyCard(text = "Перечень дисциплин в базе не заполнен") }
        } else {
            val list =
                teacher.subjects
                    .split(';', ',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            items(list) { subject ->
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = ColorSurfaceVariantLight,
                ) {
                    Text(
                        text = subject,
                        style =
                        androidx.compose.ui.text
                            .TextStyle(fontSize = 13.sp, color = ColorTextBody),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

        val hasContacts = teacher.room.isNotBlank() || teacher.phone.isNotBlank() || teacher.email.isNotBlank()
        if (hasContacts) {
            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        SectionCaption("КОНТАКТЫ")
                        Spacer(modifier = Modifier.height(6.dp))
                        if (teacher.room.isNotBlank()) {
                            InfoRow(
                                label = "Кабинет",
                                value = teacher.room,
                                onClick = { onOpenRoom(teacher.room) },
                            )
                        }
                        if (teacher.phone.isNotBlank()) InfoRow("Телефон", teacher.phone)
                        if (teacher.email.isNotBlank()) InfoRow("E-mail", teacher.email)
                    }
                }
            }
        }

        if (!isActive) {
            item {
                EmptyCard(
                    text =
                    "По расписанию вашей группы этот преподаватель уроков не ведёт.\n\n" +
                        "Откройте вкладку «Расписание», чтобы посмотреть его полное расписание.",
                )
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorActiveBlue),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        SectionCaption("ВЕДЁТ У ВАШЕЙ ГРУППЫ")
                        Spacer(modifier = Modifier.height(6.dp))
                        if (lessonsWithGroup > 0) InfoRow("Уроков в базе", "$lessonsWithGroup")
                        if (roomsFromSchedule.isNotEmpty()) {
                            InfoRow("Ведёт в кабинетах", roomsFromSchedule.joinToString(", "))
                        }
                        if (subjectsFromSchedule.isNotEmpty()) {
                            InfoRow("Предметы", subjectsFromSchedule.take(6).joinToString("; "))
                        }
                    }
                }
            }
        }
    }
}

// AA4: авто-факт уверен (weekly/recent) и пользователь его не снимал — шильд не кликабельен.
internal fun canToggleLeads(
    autoActive: Boolean,
    manualOff: Boolean,
): Boolean = !autoActive || manualOff

@Composable
private fun TeacherScheduleTab(
    teacher: Teacher,
    onOpenRoom: (String) -> Unit = {},
) {
    val repository = com.politex.AppGraph.scheduleRepository
    val context = LocalContext.current

    val today = rememberToday()
    var dayOffset by rememberSaveable { mutableIntStateOf(com.politex.data.repository.defaultTeacherDayOffset(com.politex.util.Dow.of(today))) }
    var showCalendar by remember { mutableStateOf(false) }
    var customDate by remember { mutableStateOf<Calendar?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var slots by remember { mutableStateOf<List<com.politex.data.network.TeacherSlot>>(emptyList()) }
    var ambiguous by remember { mutableStateOf(false) }
    val allTeachers = com.politex.ui.util.rememberTeachers()

    val targetCalendar =
        remember(dayOffset, customDate, today) {
            com.politex.data.repository.teacherTargetCalendar(dayOffset, customDate)
        }

    val surname =
        remember(teacher.name) {
            com.politex.data.repository.TeacherInsights
                .surnameOf(teacher.name)
        }

    val selectedDateDow =
        remember(targetCalendar) {
            com.politex.data.network.MpkScheduleParser
                .dowOfCalendar(targetCalendar)
        }

    // R2#18: ключ today — после полуночи (rememberToday обновляется) расписание перечитывается,
    // «СЕГОДНЯ» не показывает вчерашний день; R2-дубль7: dd.MM.yyyy через общий DateFormats
    LaunchedEffect(teacher.name, dayOffset, customDate, today, allTeachers) {
        val dateKey = com.politex.util.DateFormats.dotDate(targetCalendar)

        // AA5: loading под try/finally — ни отмена эффекта (смена дня), ни исключение
        // больше не оставляют вечный спиннер; ошибка сети становится честным текстом.
        // T2-A: загрузка вынесена в TeacherDayLoader (общий со студенческим экраном путь).
        loading = true
        try {
            val day =
                com.politex.data.repository.TeacherDayLoader.loadDay(
                    context = context,
                    repository = repository,
                    profile = com.politex.ui.teacher.TeacherProfile(teacher.name, surname),
                    dateString = dateKey,
                    allTeachers = allTeachers,
                )
            slots =
                day.lessons.map {
                    com.politex.data.network.TeacherSlot(
                        lessonNumber = it.lessonNumber,
                        groups = it.groups,
                        subject = it.subject,
                        room = it.room,
                        dayOfWeek = selectedDateDow,
                    )
                }
            ambiguous = day.ambiguous
            errorMessage = day.error
        } finally {
            loading = false
        }
    }

    if (showCalendar) {
        com.politex.ui.components.CalendarArchiveDialog(
            groupName = "",
            scheduleRepository = com.politex.AppGraph.scheduleRepository,
            onDismiss = { showCalendar = false },
            onDateSelected = { _, dateStr ->

                val parsed =
                    com.politex.data.network.MpkScheduleParser
                        .parseDotDate(dateStr)
                parsed?.let {
                    customDate = (it.clone() as Calendar)
                    dayOffset = 0
                }
                showCalendar = false
            },
        )
    }

    val dateTitle =
        remember(targetCalendar) {
            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy (EEEE)", java.util.Locale.forLanguageTag("ru"))
            sdf.format(targetCalendar.time).replaceFirstChar { it.uppercase() }
        }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DayChip(
                title = "СЕГОДНЯ",
                selected = dayOffset == 0 && customDate == null,
                onClick = {
                    dayOffset = 0
                    customDate = null
                },
                modifier = Modifier.weight(1f),
                tag = "teacher_day_today",
            )
            DayChip(
                title = "ЗАВТРА",
                selected = dayOffset == 1 && customDate == null,
                onClick = {
                    dayOffset = 1
                    customDate = null
                },
                modifier = Modifier.weight(1f),
                tag = "teacher_day_tomorrow",
            )
            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, if (customDate != null) ColorTopBar else ColorBorderLight),
                color = if (customDate != null) ColorTopBar else ColorBgMain,
                modifier =
                Modifier
                    .size(Tokens.IconHit)
                    .bouncyClickable { showCalendar = true },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Выбрать дату",
                        tint = if (customDate != null) Color.White else ColorBrandBlue,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Text(
            text = dateTitle,
            style =
            androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = ColorTextMuted,
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )

        Spacer(modifier = Modifier.height(6.dp))

        when {
            loading ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = ColorBrandBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp),
                    )
                }

            errorMessage.isNotBlank() ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = errorMessage,
                        style =
                        androidx.compose.ui.text
                            .TextStyle(fontSize = 14.sp, color = ColorTextMuted),
                        textAlign = TextAlign.Center,
                    )
                }

            slots.isEmpty() ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "В этот день у преподавателя уроков нет",
                        style =
                        androidx.compose.ui.text
                            .TextStyle(fontSize = 14.sp, color = ColorTextMuted),
                        textAlign = TextAlign.Center,
                    )
                }

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (ambiguous) {
                        item {
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Не удалось точно определить (однофамильцы) — проверьте список",
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = ColorTextMuted,
                                    ),
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    }
                    items(slots) { slot ->
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = ColorBrandFill,
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${slot.lessonNumber}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = slot.subject.ifBlank { "Урок" },
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = ColorTextTitle,
                                        ),
                                        maxLines = 1,
                                    )
                                    Text(
                                        text =
                                        buildString {
                                            val bells =
                                                com.politex.data.model.CollegeBellSchedule
                                                    .getBellsForDay(selectedDateDow)
                                            val time = bells.firstOrNull { it.lessonNumber == slot.lessonNumber }
                                            if (time != null) append("${time.start}–${time.end}")
                                            if (slot.groups.isNotEmpty()) {
                                                if (isNotEmpty()) append(" • ")
                                                append(slot.groups.joinToString(", "))
                                            }
                                        },
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontSize = 11.sp,
                                            color = ColorTextMuted,
                                        ),
                                        maxLines = 2,
                                    )
                                }
                                if (slot.room.isNotBlank()) {
                                    // CC3: узкий комнат-чип как в LessonCard расписания
                                    // (Surface 2dp + h6/v2 + 11sp bold + иконка 12dp);
                                    // hitbox 44dp остаётся на внешнем Box
                                    Box(
                                        modifier =
                                        Modifier
                                            .heightIn(min = 44.dp)
                                            .bouncyClickable(scaleDown = 0.97f) { onOpenRoom(slot.room) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AppCard(
                                            color = ColorSurfaceHighlight,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = ColorBrandBlue,
                                                    modifier = Modifier.size(12.dp),
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "каб. ${slot.room}",
                                                    style =
                                                    androidx.compose.ui.text.TextStyle(
                                                        fontWeight = FontWeight.Bold,
                                                        color = ColorBrandBlue,
                                                        fontSize = 11.sp,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun DayChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "",
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        color = if (selected) ColorTopBar else ColorBgMain,
        modifier =
        modifier
            .height(Tokens.ChipScreen)
            .bouncyClickable(onClick = onClick)
            .testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                softWrap = false,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (selected) Color.White else ColorTextBody,
                ),
            )
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = title,
            style = TextStylePageTitle,
            maxLines = 1,
        )
        Text(
            text = subtitle,
            style =
            androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = ColorBrandBlue,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {

    val clickModifier =
        if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .bouncyClickable(scaleDown = 0.97f, onClick = onClick)
        } else {
            Modifier
        }
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label.uppercase(),
            style =
            androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = ColorTextMuted,
            ),
        )
        Box(modifier = clickModifier, contentAlignment = Alignment.CenterStart) {
            Text(
                text = value,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    fontWeight = if (onClick != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (onClick != null) ColorBrandBlue else ColorTextBody,
                ),
            )
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        color = ColorSurfaceVariantLight,
    ) {
        Text(
            text = text,
            style =
            androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp,
                color = ColorTextMuted,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
    }
}
