package com.politex.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
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
import com.politex.data.model.GroupInfo
import com.politex.data.model.TaskType
import com.politex.data.repository.ScheduleRepository
import com.politex.ui.components.CalendarArchiveDialog
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
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorMenuSubtext
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberNowMinutes
import com.politex.ui.util.rememberToday
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight

// O1/G: CSV учителей парсился на главном потоке при каждом входе на вкладку.
// Теперь — ленивый синглтон процесса: один разбор на всё приложение.
private var knownTeacherSurnamesCache: Set<String>? = null

private fun knownTeacherSurnames(context: android.content.Context): Set<String> =
    knownTeacherSurnamesCache ?: java.util.Collections.synchronizedSet(
        com.politex.AppGraph.teachersRepository
            .loadTeachers()
            .map { com.politex.data.repository.TeacherInsights.surnameOf(it.name) }
            .toHashSet()
    ).also { knownTeacherSurnamesCache = it }

@Composable
fun ScheduleScreen(
    groupInfo: GroupInfo,
    scheduleRepository: ScheduleRepository,
    onSyncRequest: () -> Unit,
    isSyncing: Boolean = false,

    // SUI-02: сбой синхронизации из uiState — пустое состояние не должно выдавать
    // сетевую ошибку за «расписание ещё не опубликовано»
    hasSyncError: Boolean = false,

    // N18: тап по значку домашки ведёт на вкладку заданий (плумбится из MainScreen)
    onOpenTasks: () -> Unit = {},

    onTeacherClick: (String) -> Unit = {},

    onRoomClick: (room: String, fromRoom: String?) -> Unit = { _, _ -> },

    listState: LazyListState = rememberLazyListState(),

    scrollToCurrentRequested: Boolean = false,
    onScrollToCurrentConsumed: () -> Unit = {},

    // O1/D: выбранный день живёт над when() в MainScreen (rememberSaveable), поэтому
    // смена вкладки больше не сбрасывает состояние. Раньше — локальные remember(key).
    selectedSlot: Int = 0,
    onSlotChange: (Int) -> Unit = {},
    selectedDateString: String = "",
    onDateChange: (String) -> Unit = {},

    // C3: данные группы приходят из AppViewModel (переживают пересоздание поддерева вкладки).
    // Фолбэк на null — для тестов и вызовов без VM: тогда экран подписывается сам, как раньше.
    groupLessonsFromVm: List<LessonEntity>? = null,
    pendingTasksFromVm: List<com.politex.data.local.entity.StudentTaskEntity>? = null,

    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // N1: живой статус синхронизации («Проверяю сайт…» → «Файл найден, загружаю…») —
    // показываем в пустом состоянии, чтобы заказчик видел «рукопожатие» с сайтом.
    val diagnosticInfo by scheduleRepository.diagnosticInfo.collectAsState()

    val isTeacherKnown: (String) -> Boolean = remember(context) {
        val known = knownTeacherSurnames(context)
        val check: (String) -> Boolean = { name ->
            com.politex.data.repository.TeacherInsights.surnameOf(name) in known
        }
        check
    }

    // SUI-01: «сегодня» с ключом дня + пересчёт на ON_RESUME. Прежний remember{} залипал
    // на вчерашней дате, если композиция переживала полночь в фоне.
    val realNow = rememberToday()
    val realDayOfWeek = groupScheduleDayOfWeek(realNow)

    // O1/A+F: единственный Flow по группе — источник и для списка дня, и для weekly-разметки,
    // и для субботы. Раньше их было 7 (день+5 дней+суббота), каждая инвалидация гнала 7 перезапросов.
    // C3: когда данные приходят из VM — подписки здесь нет вообще. Она умирала вместе с
    // поддеревом при смене вкладки, и на возврате первый эмит Room приходил только после
    // нового запроса к БД → спиннер → «расписание ещё не опубликовано» на ~секунду.
    val allGroupLessons: List<com.politex.data.local.entity.LessonEntity> =
        groupLessonsFromVm ?: scheduleRepository
            .getAllLessonsForGroup(groupInfo.canonicalName)
            .collectAsState(initial = emptyList())
            .value

    val saturdayLessons: List<com.politex.data.local.entity.LessonEntity> =
        remember(allGroupLessons) { allGroupLessons.filter { it.dayOfWeek == 6 } }
    val saturdayIsSchoolDay = groupInfo.course == 1 || saturdayLessons.isNotEmpty()

    // SUI-05: calendarDayOfWeek был локальным близнецом groupScheduleDayOfWeek — используем общий
    fun calendarDayOfWeek(cal: Calendar): Int = groupScheduleDayOfWeek(cal)

    fun isSchoolDay(dow: Int): Boolean = dow in 1..5 || (dow == 6 && saturdayIsSchoolDay)

    // SUI-01: ключ realNow — слоты пересчитываются после полуночи/возврата из фона
    val daySlots = remember(groupInfo.canonicalName, saturdayIsSchoolDay, realNow) {
        val left = (realNow.clone() as Calendar).apply {
            when (realDayOfWeek) {
                6 -> if (!saturdayIsSchoolDay) add(Calendar.DAY_OF_YEAR, 2)
                7 -> add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val right = (left.clone() as Calendar).apply {
            do {
                add(Calendar.DAY_OF_YEAR, 1)
            } while (!isSchoolDay(calendarDayOfWeek(this)))
        }
        left to right
    }
    val leftCal = daySlots.first
    val rightCal = daySlots.second

    // SUI-01: «завтра» — от того же наблюдаемого «сегодня»
    val tomorrow = remember(realNow) { (realNow.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) } }
    fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val leftIsToday = sameDay(leftCal, realNow)
    val rightIsTomorrow = sameDay(rightCal, tomorrow)

    // O1/D: ранее локальные remember(groupInfo.canonicalName) — смена вкладки убивала их.
    // Смена группы по-прежнему сбрасывает слот и архивную дату. lastGroup — обычный
    // remember: при возврате на вкладку поддерево пересоздаётся, он инициализируется
    // ТЕКУЩЕЙ группой и сброса не происходит (иначе фикс D затирал бы восстановленный день).
    var lastGroup by remember { mutableStateOf(groupInfo.canonicalName) }
    LaunchedEffect(groupInfo.canonicalName) {
        if (lastGroup != groupInfo.canonicalName) {
            lastGroup = groupInfo.canonicalName
            onSlotChange(0)
            onDateChange("")
        }
    }
    var showArchiveDialog by remember { mutableStateOf(false) }

    val activeDayOfWeek = if (selectedSlot == 0) calendarDayOfWeek(leftCal) else calendarDayOfWeek(rightCal)
    val activeCal = if (selectedSlot == 0) leftCal else rightCal
    // Дата активного дня в формате БД: выбранная в архиве либо календарная ячейка «сегодня/завтра»
    val activeDateString = remember(selectedDateString, activeCal) {
        if (selectedDateString.isNotBlank()) {
            com.politex.data.network.MpkScheduleParser.parseDotDate(selectedDateString)?.let {
                "%02d.%02d.%04d".format(
                    it.get(Calendar.DAY_OF_MONTH), it.get(Calendar.MONTH) + 1, it.get(Calendar.YEAR)
                )
            } ?: selectedDateString
        } else {
            "%02d.%02d.%04d".format(
                activeCal.get(Calendar.DAY_OF_MONTH),
                activeCal.get(Calendar.MONTH) + 1,
                activeCal.get(Calendar.YEAR)
            )
        }
    }

    val dayNamesNominative = listOf("", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    val subtitleText = if (selectedDateString.isNotBlank()) {
        "${dayNamesNominative[activeDayOfWeek]} • $selectedDateString"
    } else {
        // Календарная ячейка (сегодня/завтра) — показываем её дату
        val d = activeCal.get(Calendar.DAY_OF_MONTH)
        val m = activeCal.get(Calendar.MONTH) + 1
        val prefix = when {
            selectedSlot == 0 && leftIsToday -> "Сегодня"
            selectedSlot == 1 && rightIsTomorrow -> "Завтра"
            else -> dayNamesNominative[activeDayOfWeek].lowercase()
        }
        String.format(java.util.Locale.ROOT, "%s, %02d.%02d", prefix, d, m)
    }

    // SUI-03: тикающая минута в ключе — иначе «текущая пара» залипала на уже закончившемся
    // уроке, пока композиция жива (время читалось внутри remember, но в ключ не входило).
    val nowMinutes = rememberNowMinutes()

    val currentLessonNumber = remember(nowMinutes, activeDayOfWeek, selectedSlot, activeDateString, leftIsToday) {
        if (selectedSlot == 0 && leftIsToday) {
            val bells = CollegeBellSchedule.getBellsForDay(activeDayOfWeek)
            bells.firstOrNull { nowMinutes in it.startMinutes..it.endMinutes }?.lessonNumber
                ?.takeIf { it > 0 }
        } else null
    }

    // O1/A: ОДИН Flow по группе вместо пересоздания на каждый тап «Сегодня/Завтра».
    // Раньше ключ Flow включал activeDateString/activeDayOfWeek → tap = resubscribe
    // → initial=null → спиннер → «расписание ещё не опубликовано».
    // O1/B: спиннер только до первого эмита Room, а не пока стейт null.
    // C3: при данных из VM прогревать нечего — VM уже отдала последнее значение,
    // поэтому спиннер при возврате на вкладку не мигает.
    var firstEmit by remember(groupInfo.canonicalName) {
        mutableStateOf(groupLessonsFromVm != null)
    }
    LaunchedEffect(allGroupLessons) { firstEmit = true }
    val isInitialLoad = !firstEmit

    // Строки с точной датой приоритетны; шаблон дня недели (пустой dateString) — дополнение
    val lessons: List<com.politex.data.local.entity.LessonEntity> =
        remember(allGroupLessons, activeDateString, activeDayOfWeek) {
            lessonsForDay(allGroupLessons, activeDateString, activeDayOfWeek)
        }

    // C3: как и allGroupLessons — подписка внутри экрана умирает со сменой вкладки.
    // Если VM передала значение, берём его; иначе (тесты) — старый путь через репозиторий.
    // Репозиторий создаётся лениво: в проде эта ветка не исполняется.
    val pendingTasks: List<com.politex.data.local.entity.StudentTaskEntity> =
        pendingTasksFromVm ?: remember(context, groupInfo.canonicalName) {
            com.politex.data.local.MpkDatabase.getInstance(context).taskDao()
                .let { com.politex.data.repository.TaskRepository(it) }
                .getPendingTasksForGroup(groupInfo.canonicalName)
        }.collectAsState(initial = emptyList()).value

    // O1/F: 5 дневных Flow схлопнуты в один allGroupLessons + groupBy в памяти.
    // Инвалидация таблицы lessons гнала 7 перезапросов, теперь 2 (allGroupLessons + суббота).
    val weeklyLessonsByDow: Map<Int, List<com.politex.data.local.entity.LessonEntity>> =
        remember(allGroupLessons) {
            allGroupLessons
                .groupBy { it.dayOfWeek }
                .mapValues { (_, list) -> com.politex.util.HomeworkMarkers.dedupeByLatestDate(list) }
        }

    // N18: урок → типы заданий на день (для цветных бейджей); ключи = уроки с домашкой.
    // Одна и та же разметка, что и раньше (markerLessonNumbers), но с типами заданий.
    val homeworkByLesson: Map<Int, List<TaskType>> = remember(lessons, pendingTasks, activeDayOfWeek, selectedSlot, selectedDateString) {
        val now = System.currentTimeMillis()
        val dayIso = com.politex.util.HomeworkMarkers.dayIsoOf(activeCal)
        val isToday = com.politex.util.HomeworkMarkers.dayIsoOf(realNow) == dayIso
        val isPast = com.politex.util.HomeworkMarkers.dayIsoOf(
            (realNow.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        ) > dayIso
        com.politex.util.HomeworkMarkers.markerLessonTypes(
            dayLessons = lessons,
            day = com.politex.util.HomeworkMarkers.DayContext(dayIso, isToday, isPast),
            nowMillis = now,
            weeklyLessonsByDow = weeklyLessonsByDow,
            pendingTasks = pendingTasks
        )
    }
    val homeworkMarkerNumbers: Set<Int> = homeworkByLesson.keys

    // --- Автопрокрутка списка к текущему уроку ---

    val isViewingToday = selectedSlot == 0 && selectedDateString.isBlank() && leftIsToday
    val lessonNumbers = remember(lessons) { lessons.map { it.lessonNumber } }
    // O1/E: rememberSaveable — иначе автопрокрутка к «сейчас» повторялась при каждом
    // возврате на вкладку (заметный «прыжок»), т.к. поддерево пересоздаётся.
    // ponytail: индексом «пользователь уже скроллил» остаётся listState (он тоже saveable).
    var didColdStartScroll by rememberSaveable { mutableStateOf(false) }

    // Ждём первого лэйаута списка — иначе вьюпорт ещё 0 и центрирование невозможно.
    suspend fun awaitFirstLayout() {
        androidx.compose.runtime.snapshotFlow {
            listState.layoutInfo.totalItemsCount > 0 && listState.layoutInfo.viewportEndOffset > 0
        }.first { it }
    }

    // 8.2: холодный старт — один раз после загрузки урока скроллим к «сейчас».
    // Пользователь уже скроллил (позиция > 0) или возврат на вкладку — не мешаем:
    // поднятый listState хранит позицию, а свежий заход с позиции 0 либо уже
    // был проскроллен (индекс > 0), либо idx == 0 и скроллить не нужно.
    LaunchedEffect(isInitialLoad, currentLessonNumber, isViewingToday) {
        if (isInitialLoad || didColdStartScroll) return@LaunchedEffect
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            didColdStartScroll = true
            return@LaunchedEffect
        }
        val idx = autoScrollLessonIndex(lessonNumbers, currentLessonNumber, isViewingToday)
        if (idx != null && idx > 0) {
            awaitFirstLayout()
            // Примерно по центру: подтягиваем карточку на треть вьюпорта выше
            listState.animateScrollToItem(idx, -listState.layoutInfo.viewportEndOffset / 3)
        }
        didColdStartScroll = true
    }

    // Уроки просматриваемого дня уже закончились (сейчас позже конца последнего урока).
    // Считаем по звонкам: конец последнего урока, который есть у группы в этот день.
    // SUI-03: тот же тикающий ключ — иначе после конца занятий экран не переключался на «завтра»
    val schoolDayEnded: Boolean = remember(nowMinutes, activeDayOfWeek, selectedSlot, activeDateString, lessons, leftIsToday) {
        if (selectedSlot != 0 || !leftIsToday) false
        else {
            val bells = CollegeBellSchedule.getBellsForDay(activeDayOfWeek)
            val lastEnd = lessons
                .mapNotNull { l -> bells.firstOrNull { it.lessonNumber == l.lessonNumber }?.endMinutes }
                .maxOrNull()
                ?: bells.lastOrNull()?.endMinutes
            lastEnd != null && nowMinutes > lastEnd
        }
    }

    // Одноразовая подсветка «где я» при входе с виджета: вспыхивает и гаснет.
    var highlightLesson by remember { mutableStateOf<Int?>(null) }
    val highlightAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(highlightLesson) {
        val target = highlightLesson ?: return@LaunchedEffect
        highlightAlpha.snapTo(0f)
        highlightAlpha.animateTo(0.85f, tween(220))
        highlightAlpha.animateTo(0f, tween(1100))
        // Гасим только если за время анимации не запросили другой урок
        if (highlightLesson == target) highlightLesson = null
    }

    // 8.3: тап по виджету «Сейчас/дальше»/«Расписание сегодня» — одноразовый
    // запрос прокрутки к текущему уроку. Уроки кончились → листаем на «завтра»
    // в начало списка (заказчик: «веди в начало списка на завтрашний день»).
    // Потребляем запрос ПОСЛЕ прокрутки: иначе смена ключа перезапустит эффект
    // и отменит анимацию на полпути.
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
        if (idx != null && idx > 0) {
            listState.animateScrollToItem(idx, -listState.layoutInfo.viewportEndOffset / 3)
        }
        // N17: урок уже виден (idx == 0 либо скроллить некуда) — подсветка всё равно нужна.
        // На перемене/до уроков currentLessonNumber == null — подсвечиваем ближайший урок.
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
                // ISO из календаря нормализуем в dd.MM.yyyy на границе
                onDateChange(
                    com.politex.data.network.MpkScheduleParser.parseDotDate(dateStr)
                    ?.let { cal ->
                        "%02d.%02d.%04d".format(
                            cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)
                        )
                    } ?: dateStr
                )
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Расписание",
                    style = TextStylePageTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = ColorBrandBlue
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val infiniteTransition = rememberInfiniteTransition(label = "SyncRotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 900)),
                label = "SyncRotationAngle"
            )
            // S2: визуально кнопка 34dp, зона нажатия 48×48 (кнопка центрируется)
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .bouncyClickable(onClick = onSyncRequest)
                    .testTag("sync_button"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Синхронизировать с сайтом",
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DaySlotCell(
                title = if (leftIsToday) "СЕГОДНЯ" else dayNamesNominative[calendarDayOfWeek(leftCal)].uppercase(),
                subtitle = String.format(
                    java.util.Locale.ROOT, "%02d.%02d",
                    leftCal.get(Calendar.DAY_OF_MONTH), leftCal.get(Calendar.MONTH) + 1
                ),
                selected = selectedSlot == 0 && selectedDateString.isBlank(),
                onClick = {
                    onSlotChange(0)
                    onDateChange("")
                },
                modifier = Modifier.weight(1f)
            )
            DaySlotCell(
                title = if (rightIsTomorrow) "ЗАВТРА" else dayNamesNominative[calendarDayOfWeek(rightCal)].uppercase(),
                subtitle = String.format(
                    java.util.Locale.ROOT, "%02d.%02d",
                    rightCal.get(Calendar.DAY_OF_MONTH), rightCal.get(Calendar.MONTH) + 1
                ),
                selected = selectedSlot == 1 && selectedDateString.isBlank(),
                onClick = {
                    onSlotChange(1)
                    onDateChange("")
                },
                modifier = Modifier.weight(1f)
            )

            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, ColorBorderLight),
                color = ColorBgMain,
                modifier = Modifier
                    .size(width = 56.dp, height = 52.dp)
                    .bouncyClickable { showArchiveDialog = true }
                    .testTag("calendar_archive_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Архив расписания",
                        tint = ColorBrandBlue,
                        modifier = Modifier.size(20.dp)
                    )
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

        AnimatedContent(
            // Ключ анимации — просматриваемый ДЕНЬ, а не данные:
            // эмиссии Room (синхронизация, домашка, отметки) не перезапускают
            // fade — список обновляется на месте. Fade только при смене дня/архива.
            targetState = selectedSlot to selectedDateString,
            // Данные обоих дней уже в памяти (один Flow группы), поэтому дни
            // переключаются мгновенно — дефолтные 220 мс кроссфейда читались как
            // «зависание»: список появлялся через долю секунды после тапа.
            transitionSpec = { fadeIn(tween(110)) togetherWith fadeOut(tween(90)) },
            label = "ScheduleListAnimation",
            modifier = Modifier.weight(1f)
        ) { _ ->
            if (isInitialLoad) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = ColorBrandBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (lessons.isEmpty()) {

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
                            // N1: во время синка — спиннер, иначе «нет занятий»
                            if (isSyncing) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = ColorBrandBlue,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = ColorBrandBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // SUI-02: при сетевом сбое честный текст — раньше он маскировался
                        // под «не опубликовано» и уводил диагностику не туда.
                        // N1: во время синка показываем ЖИВОЙ статус «рукопожатия» с сайтом.
                        Text(
                            text = when {
                                isSyncing -> diagnosticInfo.statusMessage.ifBlank { "Проверяю сайт…" }
                                hasSyncError -> "Не удалось обновить расписание"
                                else -> "Расписание ещё не опубликовано"
                            },
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ColorTextTitle
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = when {
                                isSyncing -> "Обновляем расписание с сайта guo-mpk.by…"
                                hasSyncError -> "Проверьте подключение к интернету и попробуйте ещё раз."
                                selectedDateString.isNotBlank() ->
                                    "Нет данных за $selectedDateString. Расписание за прошедшие дни хранится в архиве только после синхронизации."
                                else -> "Для группы ${groupInfo.canonicalName} на этот день пар пока нет в базе колледжа."
                            },
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
                                color = ColorBrandFill,
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
                                        // SUI-02: при сбое — «ПОВТОРИТЬ», иначе прежний текст
                                        text = if (hasSyncError) "ПОВТОРИТЬ" else "СИНХРОНИЗИРОВАТЬ С САЙТОМ",
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

                // R2: старт маршрута — кабинет текущей/предыдущей пары (наибольший lessonNumber
                // среди начавшихся сегодня). "" (НЕ null) = ничего не началось → маршрут от
                // главного входа: непустой fromRoom — маркер «запрос из расписания, с маршрутом».
                // Не сегодня (завтра/архив) → null: физическая позиция неизвестна, без маршрута.
                val fromRoom = remember(lessons, nowMinutes, isViewingToday) {
                    if (isViewingToday) {
                        com.politex.ui.util.RouteStartResolver.previousRoomNow(lessons, nowMinutes) ?: ""
                    } else null
                }

                val slots = remember(lessons) {
                    if (lessons.isEmpty()) emptyList()
                    else {
                        val byNumber = lessons.associateBy { it.lessonNumber }
                        (1..lessons.maxOf { it.lessonNumber })
                            .map { number -> number to byNumber[number] }
                    }
                }

                LazyColumn(
                    state = listState,
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
                                hasHomework = lesson.lessonNumber in homeworkMarkerNumbers,
                                // N18: типы заданий урока → цветные бейджи; тап → вкладка заданий
                                homeworkTypes = homeworkByLesson[lesson.lessonNumber] ?: emptyList(),
                                onHomeworkClick = onOpenTasks,
                                onTeacherClick = onTeacherClick,
                                isTeacherKnown = isTeacherKnown,
                                onRoomClick = { room -> onRoomClick(room, fromRoom) },
                                // Б8: волна-подсветка «вы здесь» — только при входе с виджета
                                focusAlpha = if (highlightLesson == lesson.lessonNumber) {
                                    highlightAlpha.value
                                } else 0f
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
private fun NoLessonCard(lessonNumber: Int) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorSurfaceVariantLight,
        border = BorderStroke(1.dp, ColorDividerLight),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("no_lesson_card_$lessonNumber")
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
                    fontSize = 14.sp,
                    color = ColorTextBody
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LessonCard(
    lesson: LessonEntity,
    isCurrent: Boolean = false,
    hasHomework: Boolean = false,
    // N18: типы заданий урока — для цветных точек-бейджей (пусто → доминирующий цвет дня)
    homeworkTypes: List<TaskType> = emptyList(),
    // N18: тап по значку домашки → вкладка заданий
    onHomeworkClick: () -> Unit = {},
    onTeacherClick: (String) -> Unit = {},

    isTeacherKnown: (String) -> Boolean = { true },
    onRoomClick: (String) -> Unit = {},
    // Б8: 0..1 — одноразовая «волна» подсветки при входе с виджета (не активна в обычном открытии)
    focusAlpha: Float = 0f,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, if (isCurrent) ColorActiveBlue else ColorBorderLight),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("lesson_card_${lesson.lessonNumber}")
    ) {
        // drawBehind на Surface оказался бы ПОД его же фоном — рисуем на внутреннем Column
        Column(modifier = Modifier
            .drawBehind {
                if (focusAlpha > 0f) {
                    drawRect(ColorBrandBlue.copy(alpha = focusAlpha * 0.18f))
                }
            }
            .padding(12.dp)) {

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

                if (hasHomework) {
                    // N18: выразительный кликабельный бейдж задания (тап → вкладка заданий),
                    // мультицвет по типам заданий вместо одного серого «редактировать»
                    HomeworkBadge(
                        lessonNumber = lesson.lessonNumber,
                        types = homeworkTypes,
                        onClick = onHomeworkClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (lesson.isSplit) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    SubgroupPane(
                        subgroupNumber = 1,
                        subject = lesson.shortSubjectName,
                        teacher = lesson.teacherFirst,
                        room = lesson.roomFirst,
                        onTeacherClick = onTeacherClick,
                        isTeacherKnown = isTeacherKnown,
                        onRoomClick = onRoomClick,
                        modifier = Modifier.weight(1f)
                    )

                    VerticalDivider(
                        color = ColorBorderLight,
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxHeight()
                    )

                    SubgroupPane(
                        subgroupNumber = 2,
                        subject = lesson.shortSubjectName,
                        teacher = lesson.teacherSecond,
                        room = lesson.roomSecond,
                        onTeacherClick = onTeacherClick,
                        isTeacherKnown = isTeacherKnown,
                        onRoomClick = onRoomClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {

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
                        TeacherChip(
                            name = lesson.teacherFirst,
                            onClick = if (isTeacherKnown(lesson.teacherFirst)) {
                                { onTeacherClick(lesson.teacherFirst) }
                            } else null
                        )
                    }

                    if (lesson.roomFirst.isNotBlank()) {
                        // S4: чип кабинета — визуал прежний, зона нажатия 44dp
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .bouncyClickable(scaleDown = 0.97f) { onRoomClick(lesson.roomFirst) },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(1.dp, ColorBorderLight),
                                color = ColorSurfaceHighlight,
                                modifier = Modifier
                                    .wrapContentWidth()
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
}

@Composable
private fun TeacherChip(
    name: String,
    onClick: (() -> Unit)?,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    modifier: Modifier = Modifier
) {
    if (onClick == null) {
        Text(
            text = name,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = fontSize,
                color = ColorTextMuted
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        return
    }

    // S4: визуальный чип прежний, зона нажатия 44dp (чип центрируется)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .bouncyClickable(
                scaleDown = 0.97f,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
            color = ColorSurfaceHighlight
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 5.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
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
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = fontSize,
                        color = ColorBrandBlue,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Открыть карточку преподавателя",
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(14.dp)
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

            Surface(
                shape = CircleShape,
                color = ColorBrandFill,
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
                // S5: чип кабинета в подгрупповой панели — зона нажатия 44dp
                Box(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .bouncyClickable(scaleDown = 0.97f) { onRoomClick(room) },
                    contentAlignment = Alignment.Center
                ) {
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
            TeacherChip(
                name = teacher,
                onClick = if (isTeacherKnown(teacher)) {
                    { onTeacherClick(teacher) }
                } else null,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DaySlotCell(
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
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 10.sp,
                    color = if (selected) ColorMenuSubtext else ColorTextMuted
                )
            )
        }
    }
}

/**
 * Индекс слота (номер урока), к которому автопрокручивается список при открытии.
 * Идёт урок — текущий; уроки кончились — последний; не начались — первый.
 * null → не скроллить (данных нет). Чистая функция ради юнит-тестов.
 */
fun autoScrollLessonIndex(
    lessonNumbers: List<Int>,
    currentLessonNumber: Int?,
    isToday: Boolean
): Int? {
    if (lessonNumbers.isEmpty()) return null
    if (!isToday) return null
    return when {
        currentLessonNumber != null && currentLessonNumber in lessonNumbers -> lessonNumbers.indexOf(currentLessonNumber)
        (currentLessonNumber ?: 0) > (lessonNumbers.maxOrNull() ?: 0) -> lessonNumbers.lastIndex
        else -> 0
    }
}

/**
 * C3: уроки просматриваемого дня. Строки с точной датой приоритетны; шаблон недели
 * (пустая dateString) — дополнение. Вынесено из remember ради юнит-теста: раньше логику
 * нельзя было зафиксировать, не поднимая весь экран.
 */
fun lessonsForDay(
    allGroupLessons: List<LessonEntity>,
    activeDateString: String,
    activeDayOfWeek: Int
): List<LessonEntity> {
    val exact = allGroupLessons.filter { it.dateString == activeDateString }
    return if (exact.isNotEmpty()) exact
    else allGroupLessons.filter { it.dateString.isBlank() && it.dayOfWeek == activeDayOfWeek }
}

/**
 * N18: TaskType → цвет бейджа (та же палитра, что в TasksScreen). Чистая функция ради
 * юнит-теста маппинга типов→цвета. ponytail: дубль одной `when`-ветки из TasksScreen —
 * выносить в общий маппер ради 6 строк не стоит, но если палитра бейджей разъедется,
 * заменить на единый `TaskType.badgeColor` в ui.theme.
 */
fun taskTypeBadgeColor(type: TaskType): Color = when (type) {
    TaskType.HOMEWORK -> ColorBadgeHW
    TaskType.LAB_WORK -> ColorBadgeLab
    TaskType.PRACTICAL_WORK -> ColorBadgePract
    TaskType.CONTROL_WORK -> ColorBadgeExam
    TaskType.COURSE_WORK -> ColorBadgeCourse
    TaskType.DIPLOMA -> ColorBadgeDiploma
}

/**
 * N18: значок задания на уроке — выразительный (AssignmentLate, не «редактировать»),
 * кликабельный (тап → вкладка заданий, хитбокс ≥44dp как T2 в TasksScreen) и мультицветный:
 * до 3 цветных точек по типам заданий дня. Доминирующий цвет — первый тип; если типов нет
 * (источник не дал тип на урок) — одна точка цвета домашки.
 */
@Composable
private fun HomeworkBadge(
    lessonNumber: Int,
    types: List<TaskType>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // До 3 разных типов; доминирующий — первый (для основного значка)
    val distinctTypes = remember(types) { types.distinct().take(3) }
    val dominant = if (distinctTypes.isNotEmpty()) taskTypeBadgeColor(distinctTypes.first()) else ColorBadgeHW
    // Хитбокс 44dp (паттерн S2/T2): зона нажатия больше видимого значка
    Box(
        modifier = modifier
            .size(44.dp)
            .bouncyClickable(onClick = onClick)
            .testTag("homework_badge_$lessonNumber"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = dominant,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AssignmentLate,
                        contentDescription = "Открыть задания",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            // Мультицвет: маленькие точки по типам заданий (читаемый «что задано»)
            if (distinctTypes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    distinctTypes.forEach { type ->
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(taskTypeBadgeColor(type))
                        )
                    }
                }
            }
        }
    }
}
