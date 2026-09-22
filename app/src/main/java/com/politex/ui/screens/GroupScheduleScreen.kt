package com.politex.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.TeacherInsights
import com.politex.data.repository.TeachersRepository
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
import com.politex.ui.util.bouncyClickable
import com.politex.util.GroupParser
import com.politex.util.MpkCurriculum
import kotlinx.coroutines.launch
import java.util.Calendar

// ---------- Чистая логика экрана (юнит-тесты без Compose) ----------

/** Calendar.DAY_OF_WEEK -> 1..7 (пн..вс), как в ScheduleScreen. */
internal fun groupScheduleDayOfWeek(cal: Calendar): Int = when (cal.get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> 1
    Calendar.TUESDAY -> 2
    Calendar.WEDNESDAY -> 3
    Calendar.THURSDAY -> 4
    Calendar.FRIDAY -> 5
    Calendar.SATURDAY -> 6
    else -> 7
}

internal fun groupScheduleIsSchoolDay(dayOfWeek: Int, saturdayIsSchoolDay: Boolean): Boolean =
    dayOfWeek in 1..5 || (dayOfWeek == 6 && saturdayIsSchoolDay)

/** Следующий учебный день после base (клон base не мутируется). */
internal fun groupScheduleNextSchoolDay(base: Calendar, saturdayIsSchoolDay: Boolean): Calendar {
    val cal = (base.clone() as Calendar)
    do {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    } while (!groupScheduleIsSchoolDay(groupScheduleDayOfWeek(cal), saturdayIsSchoolDay))
    return cal
}

internal fun groupScheduleDbDate(cal: Calendar): String =
    "%02d.%02d.%04d".format(
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.YEAR)
    )

/** Строки с точной датой приоритетны; пустой dateString (шаблон дня недели) — фолбэк. */
internal fun pickForeignGroupLessons(rows: List<LessonEntity>, dateString: String): List<LessonEntity> {
    val exact = rows.filter { it.dateString == dateString }
    if (exact.isNotEmpty()) return exact
    return rows.filter { it.dateString.isBlank() }
}

// ---------- Экран «Расписание группы» ----------

/**
 * Просмотр расписания ЛЮБОЙ группы колледжа (курс -> группа из учебного плана,
 * не из базы учащихся). Синхронизация пишет строки в общую БД с ключом groupName
 * и НЕ меняет собственную группу пользователя (selected_group не трогается).
 */
@Composable
fun GroupScheduleScreen(
    scheduleRepository: ScheduleRepository,
    onBack: () -> Unit,
    ownGroupName: String = "",
    onTeacherClick: (String) -> Unit = {},
    onRoomClick: (room: String, fromRoom: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedGroupName by rememberSaveable { mutableStateOf("") }
    var selectedCourse by rememberSaveable {
        mutableIntStateOf(GroupParser.parse(ownGroupName)?.course ?: 1)
    }
    var selectedSlot by rememberSaveable(selectedGroupName) { mutableIntStateOf(0) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncNotice by remember(selectedGroupName) { mutableStateOf<String?>(null) }

    val foreignInfo = remember(selectedGroupName) { GroupParser.parse(selectedGroupName) }

    // Синхронизация чужой группы: те же строки БД (ключ groupName), своя группа не меняется
    fun syncForeignSchedule(force: Boolean) {
        if (selectedGroupName.isBlank() || isSyncing) return
        isSyncing = true
        syncNotice = null
        scope.launch {
            val result = scheduleRepository.syncScheduleFromWeb(selectedGroupName, force = force)
            isSyncing = false
            syncNotice = result.fold(
                onSuccess = { count ->
                    if (count == 0) "Группы $selectedGroupName нет в опубликованном расписании на ближайшие даты"
                    else null
                },
                onFailure = { "Не удалось загрузить расписание: проверьте интернет" }
            )
        }
    }

    LaunchedEffect(selectedGroupName) {
        // force=false: при наличии кэша на сегодня/завтра сеть не трогается
        if (selectedGroupName.isNotBlank()) syncForeignSchedule(force = false)
    }

    val isTeacherKnown: (String) -> Boolean = remember(context) {
        val known = TeachersRepository(context).loadTeachers()
            .map { TeacherInsights.surnameOf(it.name) }
            .toHashSet()
        val check: (String) -> Boolean = { name ->
            TeacherInsights.surnameOf(name) in known
        }
        check
    }

    val realNow = remember { Calendar.getInstance() }
    val realDayOfWeek = remember { groupScheduleDayOfWeek(realNow) }
    val saturdayLessons by scheduleRepository
        .getLessonsForDay(selectedGroupName, 6)
        .collectAsState(initial = emptyList<LessonEntity>())
    val saturdayIsSchoolDay = foreignInfo?.hasSaturdayClasses == true || saturdayLessons.isNotEmpty()

    val daySlots = remember(selectedGroupName, saturdayIsSchoolDay, realDayOfWeek) {
        val left = Calendar.getInstance().apply {
            when (realDayOfWeek) {
                6 -> if (!saturdayIsSchoolDay) add(Calendar.DAY_OF_YEAR, 2)
                7 -> add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        left to groupScheduleNextSchoolDay(left, saturdayIsSchoolDay)
    }
    val leftCal = daySlots.first
    val rightCal = daySlots.second

    val tomorrow = remember { (Calendar.getInstance() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) } }
    fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val leftIsToday = sameDay(leftCal, realNow)
    val rightIsTomorrow = sameDay(rightCal, tomorrow)

    val activeCal = if (selectedSlot == 0) leftCal else rightCal
    val activeDayOfWeek = groupScheduleDayOfWeek(activeCal)
    val activeDateString = groupScheduleDbDate(activeCal)

    val currentLessonNumber = remember(activeDayOfWeek, selectedSlot, leftIsToday) {
        if (selectedSlot == 0 && leftIsToday) {
            val cal = Calendar.getInstance()
            val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            CollegeBellSchedule.getBellsForDay(activeDayOfWeek)
                .firstOrNull { minutes in it.startMinutes..it.endMinutes }?.lessonNumber
                ?.takeIf { it > 0 }
        } else null
    }

    // Запрос по реальной дате + фолбэк на день недели
    val lessonsState: List<LessonEntity>? by scheduleRepository
        .getLessonsForDateOrDay(selectedGroupName, activeDateString, activeDayOfWeek)
        .collectAsState(initial = null)
    val isInitialLoad = lessonsState == null
    val lessons = remember(lessonsState, activeDateString) {
        pickForeignGroupLessons(lessonsState ?: emptyList(), activeDateString)
    }

    val fromRoom = remember(lessons, currentLessonNumber) {
        currentLessonNumber?.let { n ->
            lessons.firstOrNull { it.lessonNumber == n }
                ?.let { listOf(it.roomFirst, it.roomSecond).firstOrNull { r -> r.isNotBlank() } }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        // Шапка подстраницы: назад + заголовок + обновление
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Расписание группы",
                    style = TextStylePageTitle.copy(fontSize = 24.sp),
                    maxLines = 1
                )
                Text(
                    text = if (selectedGroupName.isBlank()) "Найдите расписание любой группы колледжа"
                    else "Сегодня и завтра • дата $activeDateString",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = ColorBrandBlue
                    ),
                    maxLines = 1
                )
            }
            if (selectedGroupName.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier
                        .size(36.dp)
                        .bouncyClickable { syncForeignSchedule(force = true) }
                        .testTag("group_schedule_sync_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить расписание группы",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
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

        if (selectedGroupName.isBlank()) {
            GroupSchedulePicker(
                selectedCourse = selectedCourse,
                onCourseSelected = { selectedCourse = it },
                ownGroupName = ownGroupName,
                onGroupSelected = { selectedGroupName = it }
            )
        } else {
            // Отличительная плашка «чужой» группы — тёмный тон из палитры (ColorTopBar)
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorTopBar,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("group_schedule_caption")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ГРУППА: $selectedGroupName",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            ),
                            maxLines = 1
                        )
                        val spec = foreignInfo?.let { MpkCurriculum.getSpecialty(it.specialtyCode) }
                        val isOwn = selectedGroupName.equals(ownGroupName, ignoreCase = true)
                        Text(
                            text = listOf(
                                spec?.shortName ?: "",
                                if (isOwn) "ваша группа" else "не ваша группа"
                            ).filter { it.isNotBlank() }.joinToString(" • "),
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .bouncyClickable { selectedGroupName = "" }
                            .testTag("group_schedule_change_button")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "СМЕНИТЬ",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Панель дня «Сегодня / Завтра»
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GroupDaySlotCell(
                    title = if (leftIsToday) "СЕГОДНЯ" else dayTitle(leftCal),
                    subtitle = String.format(
                        java.util.Locale.ROOT, "%02d.%02d",
                        leftCal.get(Calendar.DAY_OF_MONTH), leftCal.get(Calendar.MONTH) + 1
                    ),
                    selected = selectedSlot == 0,
                    onClick = { selectedSlot = 0 },
                    modifier = Modifier.weight(1f)
                )
                GroupDaySlotCell(
                    title = if (rightIsTomorrow) "ЗАВТРА" else dayTitle(rightCal),
                    subtitle = String.format(
                        java.util.Locale.ROOT, "%02d.%02d",
                        rightCal.get(Calendar.DAY_OF_MONTH), rightCal.get(Calendar.MONTH) + 1
                    ),
                    selected = selectedSlot == 1,
                    onClick = { selectedSlot = 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(1.dp)
                    .background(ColorDividerLight)
            )

            if (syncNotice != null && lessons.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorSurfaceVariantLight,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = syncNotice ?: "",
                        style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (isInitialLoad) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = ColorBrandBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (lessons.isEmpty()) {
                // Честное пустое состояние: причина + повторная попытка
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
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
                                .background(ColorSurfaceHighlight),
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
                            text = when {
                                isSyncing -> "Загружаем расписание…"
                                syncNotice != null -> syncNotice ?: ""
                                else -> "Нет данных — расписание ещё не синхронизировано"
                            },
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ColorTextTitle
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Расписание группы $selectedGroupName появится после синхронизации с сайтом guo-mpk.by",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = ColorTextMuted
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (!isSyncing) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(1.dp, ColorBrandBlue),
                                color = ColorBrandFill,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .bouncyClickable { syncForeignSchedule(force = true) }
                                    .testTag("group_schedule_retry_button")
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
                                        text = "ОБНОВИТЬ",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val slots = remember(lessons) {
                    if (lessons.isEmpty()) emptyList()
                    else {
                        val byNumber = lessons.associateBy { it.lessonNumber }
                        (1..lessons.maxOf { it.lessonNumber })
                            .map { number -> number to byNumber[number] }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(slots, key = { it.first }) { (number, lesson) ->
                        if (lesson != null) {
                            LessonCard(
                                lesson = lesson,
                                isCurrent = lesson.lessonNumber == currentLessonNumber,
                                onTeacherClick = onTeacherClick,
                                isTeacherKnown = isTeacherKnown,
                                onRoomClick = { room -> onRoomClick(room, fromRoom) }
                            )
                        } else {
                            GroupNoLessonCard(number)
                        }
                    }
                }
            }
        }
    }
}

private val groupDayNames = listOf("", "ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА", "ВОСКРЕСЕНЬЕ")

private fun dayTitle(cal: Calendar): String = groupDayNames[groupScheduleDayOfWeek(cal)]

/** Выбор курса и группы (учебный план, не база учащихся). */
@Composable
private fun GroupSchedulePicker(
    selectedCourse: Int,
    onCourseSelected: (Int) -> Unit,
    ownGroupName: String,
    onGroupSelected: (String) -> Unit
) {
    val allPresetGroups = remember { GroupParser.generateCommonGroups() }
    val courseGroups = remember(selectedCourse) {
        allPresetGroups.filter { it.course == selectedCourse }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..4).forEach { course ->
                val isSelected = selectedCourse == course
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                    color = if (isSelected) ColorTopBar else ColorBgMain,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .bouncyClickable { onCourseSelected(course) }
                        .testTag("group_schedule_course_$course")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$course КУРС",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White else ColorTextBody
                            )
                        )
                    }
                }
            }
        }

        Text(
            text = "Группы $selectedCourse курса:",
            style = androidx.compose.ui.text.TextStyle(
                color = ColorTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp)
        ) {
            gridItems(courseGroups) { group ->
                val isOwn = group.canonicalName.equals(ownGroupName, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = if (isOwn) ColorSurfaceHighlight else ColorBgMain,
                    border = BorderStroke(
                        1.dp,
                        if (isOwn) ColorBrandBlue else ColorBorderLight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .bouncyClickable { onGroupSelected(group.canonicalName) }
                        .testTag("group_schedule_cell_${group.canonicalName}")
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = group.canonicalName,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isOwn) ColorBrandBlue else ColorTextTitle
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val spec = MpkCurriculum.getSpecialty(group.specialtyCode)
                        Text(
                            text = if (isOwn) "ваша группа" else (spec?.shortName ?: ""),
                            style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = ColorTextMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupDaySlotCell(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        color = if (selected) ColorTopBar else ColorBgMain,
        modifier = modifier
            .height(52.dp)
            .bouncyClickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = title,
                softWrap = false,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (selected) Color.White else ColorTextBody
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                softWrap = false,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (selected) Color.White else ColorTextMuted
                )
            )
        }
    }
}

@Composable
private fun GroupNoLessonCard(lessonNumber: Int) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorSurfaceVariantLight,
        border = BorderStroke(1.dp, ColorDividerLight),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorSurfaceHighlight,
                border = BorderStroke(1.dp, ColorDividerLight),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$lessonNumber",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ColorTextMuted
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Урока нет",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    color = ColorTextMuted
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
