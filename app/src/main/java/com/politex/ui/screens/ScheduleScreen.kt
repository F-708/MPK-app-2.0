package com.politex.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.model.GroupInfo
import com.politex.data.model.TaskType
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.checkingStatusFor
import com.politex.data.repository.emptyStateText
import com.politex.ui.components.AppCard
import com.politex.ui.components.AppDivider
import com.politex.ui.components.CalendarArchiveDialog
import com.politex.ui.components.CenteredLoader
import com.politex.ui.components.DaySlotCell
import com.politex.ui.components.NoLessonCard
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBadgeCourse
import com.politex.ui.theme.ColorBadgeDiploma
import com.politex.ui.theme.ColorBadgeExam
import com.politex.ui.theme.ColorBadgeHW
import com.politex.ui.theme.ColorBadgeLab
import com.politex.ui.theme.ColorBadgePract
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorMenuSubtext
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberIsTeacherKnown
import com.politex.ui.util.rememberNowMinutes
import com.politex.ui.util.rememberToday
import com.politex.util.DateFormats
import com.politex.util.HintFlags
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale

@Composable
fun ScheduleScreen(
    groupInfo: GroupInfo,
    scheduleRepository: ScheduleRepository,

    onSyncRequest: (String) -> Unit,
    isSyncing: Boolean = false,

    hasSyncError: Boolean = false,

    onOpenTasks: () -> Unit = {},
    onTeacherClick: (String) -> Unit = {},
    onRoomClick: (room: String, fromRoom: String?) -> Unit = { _, _ -> },
    listState: LazyListState = rememberLazyListState(),
    scrollToCurrentRequested: Boolean = false,
    onScrollToCurrentConsumed: () -> Unit = {},

    selectedSlot: Int = 0,
    onSlotChange: (Int) -> Unit = {},
    selectedDateString: String = "",
    onDateChange: (String) -> Unit = {},

    groupLessonsFromVm: List<LessonEntity>? = null,
    pendingTasksFromVm: List<com.politex.data.local.entity.StudentTaskEntity>? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var scheduleHintDone by remember { mutableStateOf(HintFlags.isDone(context, HintFlags.KEY_SCHEDULE_TAPS)) }
    fun markScheduleHintDone() {
        if (!scheduleHintDone) {
            HintFlags.markDone(context, HintFlags.KEY_SCHEDULE_TAPS)
            scheduleHintDone = true
        }
    }

    val isTeacherKnown: (String) -> Boolean = rememberIsTeacherKnown()

    val realNow = rememberToday()

    val allGroupLessons: List<com.politex.data.local.entity.LessonEntity>? =
        groupLessonsFromVm
            ?: scheduleRepository
                .getAllLessonsForGroup(groupInfo.canonicalName)
                .collectAsState(initial = null as List<LessonEntity>?)
                .value

    val saturdayLessons: List<com.politex.data.local.entity.LessonEntity> =
        remember(allGroupLessons) { allGroupLessons.orEmpty().filter { it.dayOfWeek == 6 } }
    val saturdayIsSchoolDay = groupInfo.course == 1 || saturdayLessons.isNotEmpty()

    fun calendarDayOfWeek(cal: Calendar): Int = groupScheduleDayOfWeek(cal)

    val daySlots =
        remember(groupInfo.canonicalName, saturdayIsSchoolDay, realNow, allGroupLessons) {
            com.politex.ui.util.daySlots(realNow, saturdayIsSchoolDay) { cal ->
                val ds = groupScheduleDbDate(cal)
                val dow = calendarDayOfWeek(cal)
                allGroupLessons.orEmpty().any { it.dateString == ds || (it.dateString.isBlank() && it.dayOfWeek == dow) }
            }
        }
    val leftCal = daySlots.first
    val rightCal = daySlots.second

    val tomorrow = remember(realNow) { (realNow.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) } }

    fun sameDay(
        a: Calendar,
        b: Calendar,
    ): Boolean = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val leftIsToday = sameDay(leftCal, realNow)
    val rightIsTomorrow = sameDay(rightCal, tomorrow)

    var lastGroup by remember { mutableStateOf(groupInfo.canonicalName) }
    LaunchedEffect(groupInfo.canonicalName) {
        if (lastGroup != groupInfo.canonicalName) {
            lastGroup = groupInfo.canonicalName
            onSlotChange(0)
            onDateChange("")
        }
    }
    var showArchiveDialog by remember { mutableStateOf(false) }

    var pendingSplit by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val parsedSelectedDate =
        remember(selectedDateString) {
            if (selectedDateString.isNotBlank()) {
                com.politex.data.network.MpkScheduleParser
                    .parseDotDate(selectedDateString)
            } else {
                null
            }
        }
    val activeCal = parsedSelectedDate ?: if (selectedSlot == 0) leftCal else rightCal
    val activeDayOfWeek = calendarDayOfWeek(activeCal)

    val activeDateString =
        remember(selectedDateString, parsedSelectedDate, activeCal) {
            when {
                parsedSelectedDate != null -> DateFormats.dotDate(parsedSelectedDate)
                selectedDateString.isNotBlank() -> selectedDateString
                else -> DateFormats.dotDate(activeCal)
            }
        }

    val dayNamesNominative = listOf("", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    val subtitleText =
        if (selectedDateString.isNotBlank()) {
            "${dayNamesNominative[activeDayOfWeek]} • $selectedDateString"
        } else {

            val d = activeCal.get(Calendar.DAY_OF_MONTH)
            val m = activeCal.get(Calendar.MONTH) + 1
            val prefix =
                when {
                    selectedSlot == 0 && leftIsToday -> "Сегодня"
                    selectedSlot == 1 && rightIsTomorrow -> "Завтра"
                    else -> dayNamesNominative[activeDayOfWeek].lowercase()
                }
            String.format(java.util.Locale.ROOT, "%s, %02d.%02d", prefix, d, m)
        }

    val nowMinutes = rememberNowMinutes()

    val currentLessonNumber =
        remember(nowMinutes, activeDayOfWeek, selectedSlot, activeDateString, leftIsToday) {
            if (selectedSlot == 0 && leftIsToday) {
                val bells = CollegeBellSchedule.getBellsForDay(activeDayOfWeek)
                bells
                    .firstOrNull { nowMinutes in it.startMinutes..it.endMinutes }
                    ?.lessonNumber
                    ?.takeIf { it > 0 }
            } else {
                null
            }
        }

    val isInitialLoad = allGroupLessons == null

    val lessons: List<com.politex.data.local.entity.LessonEntity> =
        remember(allGroupLessons, activeDateString, activeDayOfWeek) {
            lessonsForDay(allGroupLessons.orEmpty(), activeDateString, activeDayOfWeek)
        }

    val roomClick: (String, String?) -> Unit = { room, fromRoom -> markScheduleHintDone(); onRoomClick(room, fromRoom) }
    val teacherClick: (String) -> Unit = { name -> markScheduleHintDone(); onTeacherClick(name) }

    val pulseLessonNumber =
        remember(lessons) {
            lessons.firstOrNull { it.roomFirst.isNotBlank() || it.teacherFirst.isNotBlank() }?.lessonNumber
        }

    val pendingTasks: List<com.politex.data.local.entity.StudentTaskEntity> =
        pendingTasksFromVm ?: remember(context, groupInfo.canonicalName) {
            com.politex.data.local.MpkDatabase
                .getInstance(context)
                .let { db ->
                    com.politex.data.repository
                        .TaskRepository(db.taskDao(), db.taskReminderDao())
                }.getPendingTasksForGroup(groupInfo.canonicalName)
        }.collectAsState(initial = emptyList()).value

    val weeklyLessonsByDow: Map<Int, List<com.politex.data.local.entity.LessonEntity>> =
        remember(allGroupLessons) {
            allGroupLessons
                .orEmpty()
                .groupBy { it.dayOfWeek }
                .mapValues { (_, list) ->
                    com.politex.util.HomeworkMarkers
                        .dedupeByLatestDate(list)
                }
        }

    val homeworkByLesson: Map<Int, List<TaskType>> =

        remember(lessons, pendingTasks, activeDayOfWeek, selectedDateString, nowMinutes) {
            val now = System.currentTimeMillis()
            val dayIso =
                com.politex.util.HomeworkMarkers
                    .dayIsoOf(activeCal)
            val isToday =
                com.politex.util.HomeworkMarkers
                    .dayIsoOf(realNow) == dayIso
            val isPast =
                com.politex.util.HomeworkMarkers.dayIsoOf(
                    (realNow.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) },
                ) > dayIso
            com.politex.util.HomeworkMarkers.markerLessonTypes(
                dayLessons = lessons,
                day =
                com.politex.util.HomeworkMarkers
                    .DayContext(dayIso, isToday, isPast),
                nowMillis = now,
                weeklyLessonsByDow = weeklyLessonsByDow,
                pendingTasks = pendingTasks,
            )
        }
    val homeworkMarkerNumbers: Set<Int> = homeworkByLesson.keys

    val isViewingToday = selectedSlot == 0 && selectedDateString.isBlank() && leftIsToday
    val lessonNumbers = remember(lessons) { lessons.map { it.lessonNumber } }

    var didColdStartScroll by rememberSaveable { mutableStateOf(false) }

    suspend fun awaitFirstLayout() {
        androidx.compose.runtime
            .snapshotFlow {
                listState.layoutInfo.totalItemsCount > 0 && listState.layoutInfo.viewportEndOffset > 0
            }.first { it }
    }

    suspend fun scrollToLessonFullyVisible(idx: Int) {
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == idx }
        val fullyVisible =
            visible != null &&
                visible.offset >= info.viewportStartOffset &&
                visible.offset + visible.size <= info.viewportEndOffset
        if (!fullyVisible) listState.scrollToItem(idx)
    }

    LaunchedEffect(isInitialLoad, currentLessonNumber, isViewingToday) {
        if (isInitialLoad || didColdStartScroll) return@LaunchedEffect
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            didColdStartScroll = true
            return@LaunchedEffect
        }
        val idx = autoScrollLessonIndex(lessonNumbers, currentLessonNumber, isViewingToday)
        if (idx != null) {
            awaitFirstLayout()
            scrollToLessonFullyVisible(idx)
        }
        didColdStartScroll = true
    }

    val schoolDayEnded: Boolean =
        remember(nowMinutes, activeDayOfWeek, selectedSlot, activeDateString, lessons, leftIsToday) {
            if (selectedSlot != 0 || !leftIsToday) {
                false
            } else {
                val bells = CollegeBellSchedule.getBellsForDay(activeDayOfWeek)
                val lastEnd =
                    lessons
                        .mapNotNull { l -> bells.firstOrNull { it.lessonNumber == l.lessonNumber }?.endMinutes }
                        .maxOrNull()
                        ?: bells.lastOrNull()?.endMinutes
                lastEnd != null && nowMinutes > lastEnd
            }
        }

    var highlightLesson by remember { mutableStateOf<Int?>(null) }
    val highlightAlpha =
        remember {
            androidx.compose.animation.core
                .Animatable(0f)
        }
    LaunchedEffect(highlightLesson) {
        val target = highlightLesson ?: return@LaunchedEffect
        highlightAlpha.snapTo(0f)
        highlightAlpha.animateTo(0.85f, tween(220))
        highlightAlpha.animateTo(0f, tween(1100))

        if (highlightLesson == target) highlightLesson = null
    }

    LaunchedEffect(scrollToCurrentRequested, isInitialLoad) {
        if (!scrollToCurrentRequested || isInitialLoad) return@LaunchedEffect
        if (!isViewingToday) {
            onScrollToCurrentConsumed()
            return@LaunchedEffect
        }
        awaitFirstLayout()
        if (schoolDayEnded) {
            onSlotChange(1)
            onDateChange("")
            listState.scrollToItem(0)
            onScrollToCurrentConsumed()
            return@LaunchedEffect
        }
        val idx = autoScrollLessonIndex(lessonNumbers, currentLessonNumber, true)
        if (idx != null) {

            scrollToLessonFullyVisible(idx)
        }

        highlightLesson = currentLessonNumber
            ?: (idx ?: 0).let { lessonNumbers.getOrNull(it) }
        onScrollToCurrentConsumed()
    }

    if (showArchiveDialog) {
        CalendarArchiveDialog(
            groupName = groupInfo.canonicalName,
            scheduleRepository = scheduleRepository,
            onDismiss = { showArchiveDialog = false },
            onDateSelected = { _, dateStr ->

                onDateChange(
                    com.politex.data.network.MpkScheduleParser
                        .parseDotDate(dateStr)
                        ?.let { cal -> DateFormats.dotDate(cal) }
                        ?: dateStr,
                )
            },

            onOpenRoom = { room ->
                showArchiveDialog = false
                roomClick(room, null)
            },
            onOpenTeacher = { name ->
                showArchiveDialog = false
                teacherClick(name)
            },
            isTeacherKnown = isTeacherKnown,
        )
    }

    pendingSplit?.let { (tappedRoom, fromFirst, fromSecond) ->
        AlertDialog(
            onDismissRequest = { pendingSplit = null },
            shape = RoundedCornerShape(2.dp),
            containerColor = ColorBgMain,
            title = {
                Text(
                    text = "Откуда вести маршрут?",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ColorTextTitle,
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SplitRouteOption("каб. $fromFirst (1 подгруппа)") {
                        pendingSplit = null
                        roomClick(tappedRoom, fromFirst)
                    }
                    SplitRouteOption("каб. $fromSecond (2 подгруппа)") {
                        pendingSplit = null
                        roomClick(tappedRoom, fromSecond)
                    }
                    SplitRouteOption("Главный вход") {
                        pendingSplit = null
                        roomClick(tappedRoom, "")
                    }
                }
            },
            confirmButton = {},
        )
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Расписание",
                    style = TextStylePageTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleText,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = ColorBrandBlue,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val infiniteTransition = rememberInfiniteTransition(label = "SyncRotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 900)),
                label = "SyncRotationAngle",
            )

            Box(
                modifier =
                Modifier
                    .sizeIn(minWidth = Tokens.HitTarget, minHeight = Tokens.HitTarget)
                    .bouncyClickable(onClick = { onSyncRequest(activeDateString) })
                    .testTag("sync_button"),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Синхронизировать с сайтом",
                            tint = Color.White,
                            modifier =
                            Modifier
                                .size(18.dp)
                                .then(if (isSyncing) Modifier.rotate(rotation) else Modifier),
                        )
                    }
                }
            }
        }

        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DaySlotCell(
                title = if (leftIsToday) "СЕГОДНЯ" else dayNamesNominative[calendarDayOfWeek(leftCal)].uppercase(),
                subtitle =
                String.format(
                    java.util.Locale.ROOT,
                    "%02d.%02d",
                    leftCal.get(Calendar.DAY_OF_MONTH),
                    leftCal.get(Calendar.MONTH) + 1,
                ),
                selected = selectedSlot == 0 && selectedDateString.isBlank(),
                onClick = {
                    onSlotChange(0)
                    onDateChange("")
                },
                modifier = Modifier.weight(1f),
            )
            DaySlotCell(
                title = if (rightIsTomorrow) "ЗАВТРА" else dayNamesNominative[calendarDayOfWeek(rightCal)].uppercase(),
                subtitle =
                String.format(
                    java.util.Locale.ROOT,
                    "%02d.%02d",
                    rightCal.get(Calendar.DAY_OF_MONTH),
                    rightCal.get(Calendar.MONTH) + 1,
                ),
                selected = selectedSlot == 1 && selectedDateString.isBlank(),
                onClick = {
                    onSlotChange(1)
                    onDateChange("")
                },
                modifier = Modifier.weight(1f),
            )

            AppCard(
                modifier = Modifier
                    .size(Tokens.HitTarget)
                    .bouncyClickable { showArchiveDialog = true }
                    .testTag("calendar_archive_button"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Архив расписания",
                        tint = ColorBrandBlue,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        AppDivider(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        AnimatedContent(

            targetState = selectedSlot to selectedDateString,

            transitionSpec = { fadeIn(tween(110)) togetherWith fadeOut(tween(90)) },
            label = "ScheduleListAnimation",
            modifier = Modifier.weight(1f),
        ) { _ ->
            if (isInitialLoad) {
                CenteredLoader(modifier = Modifier.fillMaxWidth().weight(1f))
            } else if (lessons.isEmpty()) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(ColorSurfaceHighlight),
                            contentAlignment = Alignment.Center,
                        ) {

                            if (isSyncing) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = ColorBrandBlue,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(28.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = ColorBrandBlue,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(

                            text =
                            when {
                                isSyncing -> checkingStatusFor(activeDateString)
                                hasSyncError -> "Не удалось обновить расписание"
                                else -> emptyStateText(activeDateString)
                            },
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ColorTextTitle,
                            ),
                            textAlign = TextAlign.Center,
                        )

                        val emptyStateSubtitle =
                            when {
                                isSyncing -> "Проверяем расписание на сайте guo-mpk.by…"
                                hasSyncError -> "Проверьте подключение к интернету и попробуйте ещё раз."
                                selectedDateString.isNotBlank() ->
                                    "Нет данных за $selectedDateString. Расписание за прошедшие дни хранится в архиве только после синхронизации."
                                else -> ""
                            }
                        if (emptyStateSubtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = emptyStateSubtitle,
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    color = ColorTextMuted,
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(1.dp, ColorBrandBlue),
                                color = ColorBrandFill,
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .bouncyClickable(onClick = { onSyncRequest(activeDateString) })
                                    .testTag("empty_state_sync_btn"),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(

                                        text = if (hasSyncError) "ПОВТОРИТЬ" else "СИНХРОНИЗИРОВАТЬ С САЙТОМ",
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {

                val slots = remember(lessons) { lessonSlots(lessons) }

                LazyColumn(
                    state = listState,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(slots, key = { it.first }) { (number, lesson) ->
                        if (lesson != null) {
                            LessonCard(
                                lesson = lesson,
                                isCurrent = lesson.lessonNumber == currentLessonNumber,
                                hasHomework = lesson.lessonNumber in homeworkMarkerNumbers,

                                homeworkTypes = homeworkByLesson[lesson.lessonNumber] ?: emptyList(),
                                onHomeworkClick = onOpenTasks,
                                onTeacherClick = teacherClick,
                                isTeacherKnown = isTeacherKnown,

                                pulse = showPulse(scheduleHintDone, lesson.lessonNumber == pulseLessonNumber),

                                onRoomClick = { room ->

                                    val routesOn =
                                        com.politex.widget.WidgetUpdateHelper
                                            .getPrefs(context)
                                            .getBoolean("map_routes_enabled", true)
                                    if (!routesOn) {
                                        roomClick(room, null)
                                    } else {

                                        when (
                                            val start =
                                                com.politex.ui.util.RouteStartResolver.startForLesson(
                                                    lessons,
                                                    lesson,
                                                    com.politex.util.SubgroupSettings.get(context),
                                                )
                                        ) {
                                            is com.politex.ui.util.RouteStartResolver.RouteStart.Ask ->
                                                pendingSplit = Triple(room, start.roomFirst, start.roomSecond)
                                            is com.politex.ui.util.RouteStartResolver.RouteStart.From ->
                                                roomClick(room, start.room)
                                        }
                                    }
                                },

                                focusAlpha =
                                if (highlightLesson == lesson.lessonNumber) {
                                    highlightAlpha.value
                                } else {
                                    0f
                                },
                            )
                        } else {
                            NoLessonCard(number)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitRouteOption(
    label: String,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .bouncyClickable(onClick = onClick),
        color = ColorSurfaceHighlight,
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = label,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = ColorBrandBlue,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun LessonCard(
    lesson: LessonEntity,
    isCurrent: Boolean = false,
    hasHomework: Boolean = false,

    homeworkTypes: List<TaskType> = emptyList(),

    onHomeworkClick: () -> Unit = {},
    onTeacherClick: (String) -> Unit = {},
    isTeacherKnown: (String) -> Boolean = { true },
    onRoomClick: (String) -> Unit = {},
    pulse: Boolean = false,

    focusAlpha: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, if (isCurrent) ColorActiveBlue else ColorBorderLight),
        shadowElevation = 0.dp,
        modifier =
        modifier
            .fillMaxWidth()
            .testTag("lesson_card_${lesson.lessonNumber}"),
    ) {

        Column(
            modifier =
            Modifier
                .drawBehind {
                    if (focusAlpha > 0f) {
                        drawRect(ColorBrandBlue.copy(alpha = focusAlpha * 0.18f))
                    }
                }.padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBrandFill,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${lesson.lessonNumber}",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                ),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${lesson.timeStart} – ${lesson.timeEnd}",
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ColorTextBody,
                        ),
                    )
                }

                if (hasHomework) {

                    HomeworkBadge(
                        lessonNumber = lesson.lessonNumber,
                        types = homeworkTypes,
                        onClick = onHomeworkClick,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (lesson.isSplit) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SubgroupPane(
                        subgroupNumber = 1,
                        subject = lesson.shortSubjectName,
                        teacher = lesson.teacherFirst,
                        room = lesson.roomFirst,
                        onTeacherClick = onTeacherClick,
                        isTeacherKnown = isTeacherKnown,
                        onRoomClick = onRoomClick,
                        pulse = pulse,
                        modifier = Modifier.weight(1f),
                    )

                    VerticalDivider(
                        color = ColorBorderLight,
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxHeight(),
                    )

                    SubgroupPane(
                        subgroupNumber = 2,
                        subject = lesson.shortSubjectName,
                        teacher = lesson.teacherSecond,
                        room = lesson.roomSecond,
                        onTeacherClick = onTeacherClick,
                        isTeacherKnown = isTeacherKnown,
                        onRoomClick = onRoomClick,
                        pulse = pulse,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {

                val subject = lesson.shortSubjectName
                val noSubject = subject.isBlank()
                Text(
                    text = if (noSubject) "Предмет уточняется" else subject,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = if (noSubject) FontWeight.Normal else FontWeight.Bold,
                        fontSize = if (noSubject) 11.sp else 15.sp,
                        color = if (noSubject) ColorTextMuted else ColorTextTitle,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (lesson.teacherFirst.isNotBlank()) {
                        TeacherChip(
                            name = lesson.teacherFirst,
                            onClick =
                            if (isTeacherKnown(lesson.teacherFirst)) {
                                { onTeacherClick(lesson.teacherFirst) }
                            } else {
                                null
                            },
                            pulse = pulse,
                        )
                    }

                    if (lesson.roomFirst.isNotBlank()) {

                        Box(
                            modifier =
                            Modifier
                                .heightIn(min = 44.dp)
                                .bouncyClickable(scaleDown = 0.97f) { onRoomClick(lesson.roomFirst) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AppCard(
                                modifier = Modifier
                                    .pulseHalo(pulse)
                                    .wrapContentWidth(),
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
                                        text = "каб. ${lesson.roomFirst}",
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = ColorBrandBlue,
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

@Composable
private fun TeacherChip(
    name: String,
    onClick: (() -> Unit)?,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    pulse: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (onClick == null) {
        Text(
            text = name,
            style =
            androidx.compose.ui.text.TextStyle(
                fontSize = fontSize,
                color = ColorTextMuted,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.pulseHalo(pulse),
        )
        return
    }

    Box(
        modifier =
        modifier
            .heightIn(min = 44.dp)
            .bouncyClickable(
                scaleDown = 0.97f,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
            color = ColorSurfaceHighlight,
            modifier = Modifier.pulseHalo(pulse),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 5.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
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
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = fontSize,
                        color = ColorBrandBlue,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Открыть карточку преподавателя",
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SubgroupPane(
    subgroupNumber: Int,
    subject: String,
    teacher: String,
    room: String,
    onTeacherClick: (String) -> Unit,
    isTeacherKnown: (String) -> Boolean,
    onRoomClick: (String) -> Unit,
    pulse: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                shape = CircleShape,
                color = ColorBrandFill,
                modifier = Modifier.size(18.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$subgroupNumber",
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }

            if (room.isNotBlank()) {

                Box(
                    modifier =
                    Modifier
                        .heightIn(min = 44.dp)
                        .bouncyClickable(scaleDown = 0.97f) { onRoomClick(room) },
                    contentAlignment = Alignment.Center,
                ) {
                    AppCard(
                        modifier = Modifier
                            .pulseHalo(pulse)
                            .wrapContentWidth(),
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
                                text = "каб. $room",
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

        Spacer(modifier = Modifier.height(4.dp))

        run {
            val noSubject = subject.isBlank()
            Text(
                text = if (noSubject) "Предмет уточняется" else subject,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = if (noSubject) FontWeight.Normal else FontWeight.SemiBold,
                    fontSize = if (noSubject) 11.sp else 13.sp,
                    color = if (noSubject) ColorTextMuted else ColorTextTitle,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (teacher.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            TeacherChip(
                name = teacher,
                onClick =
                if (isTeacherKnown(teacher)) {
                    { onTeacherClick(teacher) }
                } else {
                    null
                },
                fontSize = 11.sp,
                pulse = pulse,
            )
        }
    }
}

fun autoScrollLessonIndex(
    lessonNumbers: List<Int>,
    currentLessonNumber: Int?,
    isToday: Boolean,
): Int? {
    if (lessonNumbers.isEmpty()) return null
    if (!isToday) return null
    return when {
        currentLessonNumber != null && currentLessonNumber in lessonNumbers -> lessonNumbers.indexOf(currentLessonNumber)
        (currentLessonNumber ?: 0) > (lessonNumbers.maxOrNull() ?: 0) -> lessonNumbers.lastIndex
        else -> 0
    }
}

fun lessonsForDay(
    allGroupLessons: List<LessonEntity>,
    activeDateString: String,
    activeDayOfWeek: Int,
): List<LessonEntity> {
    val exact = allGroupLessons.filter { it.dateString == activeDateString }
    return if (exact.isNotEmpty()) {
        exact
    } else {
        allGroupLessons.filter { it.dateString.isBlank() && it.dayOfWeek == activeDayOfWeek }
    }
}

fun lessonSlots(lessons: List<LessonEntity>): List<Pair<Int, LessonEntity?>> {
    if (lessons.isEmpty()) return emptyList()
    val byNumber = lessons.associateBy { it.lessonNumber }
    return (1..lessons.maxOf { it.lessonNumber }).map { number -> number to byNumber[number] }
}

internal fun showPulse(
    flagDone: Boolean,
    isTargetLesson: Boolean,
): Boolean = !flagDone && isTargetLesson

@Composable
internal fun Modifier.pulseHalo(active: Boolean): Modifier {
    if (!active) return this
    val t by
        rememberInfiniteTransition(label = "HintPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1000), RepeatMode.Reverse),
            label = "HintPulseT",
        )
    return drawBehind {

        val inflate = 3.dp.toPx()
        val scale = 1f + 0.04f * t
        val w = size.width * scale + inflate * 2
        val h = size.height * scale + inflate * 2
        drawRoundRect(
            color = ColorActiveBlue.copy(alpha = 0.12f + 0.18f * t),
            topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f),
            size = Size(w, h),
            cornerRadius = CornerRadius(2.dp.toPx() + inflate),
            style = androidx.compose.ui.graphics.drawscope.Fill,
        )
    }
}

fun taskTypeBadgeColor(type: TaskType): Color = when (type) {
    TaskType.HOMEWORK -> ColorBadgeHW
    TaskType.LAB_WORK -> ColorBadgeLab
    TaskType.PRACTICAL_WORK -> ColorBadgePract
    TaskType.CONTROL_WORK -> ColorBadgeExam
    TaskType.COURSE_WORK -> ColorBadgeCourse
    TaskType.DIPLOMA -> ColorBadgeDiploma
}

@Composable
private fun HomeworkBadge(
    lessonNumber: Int,
    types: List<TaskType>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val distinctTypes = remember(types) { types.distinct().take(3) }
    val dominant = if (distinctTypes.isNotEmpty()) taskTypeBadgeColor(distinctTypes.first()) else ColorBadgeHW

    Box(
        modifier =
        modifier
            .size(44.dp)
            .bouncyClickable(onClick = onClick)
            .testTag("homework_badge_$lessonNumber"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = dominant,
                modifier = Modifier.size(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AssignmentLate,
                        contentDescription = "Открыть задания",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            if (distinctTypes.size > 1) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    distinctTypes.forEach { type ->
                        Box(
                            modifier =
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(taskTypeBadgeColor(type)),
                        )
                    }
                }
            }
        }
    }
}
