package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.EventNote
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.model.CollegeBellSchedule
import com.example.data.model.GroupInfo
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
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorSurfaceHighlight
import com.example.ui.theme.ColorSurfaceVariantLight

/**
 * Главный экран расписания занятий колледжа ГУО «МГПК».
 *
 * СООТВЕТСТВИЕ ДИЗАЙН-СИСТЕМЕ И БИЗНЕС-ПРАВИЛАМ:
 * 1. Крупный заголовок страницы «Расписание» (32–34px Bold, Sentence case, #111827).
 * 2. Две ячейки дней: «Сегодня» и следующий учебный день (суббота учебна только
 *    для 1 курса или при наличии субботних уроков в базе; в выходные — Пн и Вт).
 * 3. Календарь-архив — справа от ячеек дней, открывает выбор любой даты.
 * 4. Запрет фейковых предметов: при отсутствии пар — аккуратный Empty State.
 * 5. Разделение по подгруппам: ровно 50/50 с микро-бейджами «1» и «2».
 * 6. Геометрия 0-2px, отсутствие теней (elevation 0dp), 1px рамки #E2E8F0.
 */
@Composable
fun ScheduleScreen(
    groupInfo: GroupInfo,
    scheduleRepository: ScheduleRepository,
    onSyncRequest: () -> Unit,
    isSyncing: Boolean = false,
    /** Переход к карточке преподавателя по тапу на его ФИО. */
    onTeacherClick: (String) -> Unit = {},
    /** Переход к карте колледжа по тапу на номер кабинета. */
    onRoomClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Преподаватели из базы колледжа. Тех, кого там нет (например, ведут только
    // замену), нельзя открыть в карточку — значит, и выглядеть нажимаемыми они
    // не должны.
    val isTeacherKnown: (String) -> Boolean = remember(context) {
        val known = com.example.data.repository.TeachersRepository(context)
            .loadTeachers()
            .map { com.example.data.repository.TeacherInsights.surnameOf(it.name) }
            .toHashSet()
        val check: (String) -> Boolean = { name ->
            com.example.data.repository.TeacherInsights.surnameOf(name) in known
        }
        check
    }

    // 1. Точный расчет реального сегодняшнего дня
    val realNow = remember { Calendar.getInstance() }
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

    // Суббота — учебный день для 1 курса, а также для любой группы,
    // у которой в базе есть уроки на субботу (расписание публикуется накануне)
    val saturdayLessons by scheduleRepository
        .getLessonsForDay(groupInfo.canonicalName, 6)
        .collectAsState(initial = emptyList<com.example.data.local.entity.LessonEntity>())
    val saturdayIsSchoolDay = groupInfo.course == 1 || saturdayLessons.isNotEmpty()

    fun calendarDayOfWeek(cal: Calendar): Int = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }

    fun isSchoolDay(dow: Int): Boolean = dow in 1..5 || (dow == 6 && saturdayIsSchoolDay)

    // Две ячейки дней: слева — текущий учебный день (сегодня; в выходные — понедельник),
    // справа — следующий учебный день (со 2–4 курса в пятницу это понедельник, а не суббота)
    val daySlots = remember(groupInfo.canonicalName, saturdayIsSchoolDay, realDayOfWeek) {
        val left = Calendar.getInstance().apply {
            when (realDayOfWeek) {
                6 -> if (!saturdayIsSchoolDay) add(Calendar.DAY_OF_YEAR, 2) // Сб без занятий -> Пн
                7 -> add(Calendar.DAY_OF_YEAR, 1)                           // Вс -> Пн
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

    val tomorrow = remember { (Calendar.getInstance() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) } }
    fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val leftIsToday = sameDay(leftCal, realNow)
    val rightIsTomorrow = sameDay(rightCal, tomorrow)

    var selectedSlot by remember(groupInfo.canonicalName) { mutableIntStateOf(0) }
    var selectedDateString by remember(groupInfo.canonicalName) { mutableStateOf("") }
    var showArchiveDialog by remember { mutableStateOf(false) }

    val activeDayOfWeek = if (selectedSlot == 0) calendarDayOfWeek(leftCal) else calendarDayOfWeek(rightCal)
    val activeCal = if (selectedSlot == 0) leftCal else rightCal

    // Подпись под заголовком: «Сегодня, 16.09» / «Завтра, 17.09» / «Понедельник, 21.09»
    val dayNamesNominative = listOf("", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    val subtitleText = if (selectedDateString.isNotBlank()) {
        "${dayNamesNominative[activeDayOfWeek]} • $selectedDateString"
    } else {
        val d = activeCal.get(Calendar.DAY_OF_MONTH)
        val m = activeCal.get(Calendar.MONTH) + 1
        val prefix = when {
            selectedSlot == 0 && leftIsToday -> "Сегодня"
            selectedSlot == 1 && rightIsTomorrow -> "Завтра"
            else -> dayNamesNominative[activeDayOfWeek].lowercase()
        }
        String.format(java.util.Locale.ROOT, "%s, %02d.%02d", prefix, d, m)
    }

    // Текущий урок по времени (показывается на ячейке «Сегодня»)
    val currentLessonNumber = remember(activeDayOfWeek, selectedSlot) {
        if (selectedSlot == 0 && leftIsToday) {
            val cal = com.example.util.DebugClock.now(context)
            val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val bells = CollegeBellSchedule.getBellsForDay(activeDayOfWeek)
            bells.firstOrNull { minutes in it.startMinutes..it.endMinutes }?.lessonNumber
                ?.takeIf { it > 0 }
        } else null
    }

    // 2. Поток расписания из Room: null = идёт первая загрузка из базы
    //    (чтобы не мигало «не опубликовано» до прихода данных)
    val lessonsFromDbState: List<com.example.data.local.entity.LessonEntity>? by scheduleRepository
        .getLessonsForDay(groupInfo.canonicalName, activeDayOfWeek)
        .collectAsState(initial = null)
    val lessonsFromDb = lessonsFromDbState ?: emptyList()
    val isInitialLoad = lessonsFromDbState == null

    // 3. Фильтрация по архивной дате или последнему снапшоту дня
    val lessons: List<com.example.data.local.entity.LessonEntity> = remember(lessonsFromDb, selectedDateString) {
        if (selectedDateString.isBlank()) {
            // Обычный просмотр дня: общие уроки (без даты) + только ПОСЛЕДНИЙ
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

    // Диалог архива
    if (showArchiveDialog) {
        CalendarArchiveDialog(
            groupName = groupInfo.canonicalName,
            scheduleRepository = scheduleRepository,
            onDismiss = { showArchiveDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        // =========================================================================
        // =========================================================================
        // Заголовок страницы «Расписание» + подпись выбранного дня + синхронизация
        // =========================================================================
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
                    maxLines = 1
                )
                Text(
                    text = subtitleText,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = ColorBrandBlue
                    ),
                    maxLines = 1
                )
            }

            // Кнопка синхронизации с сайтом (вращается во время обновления)
            val infiniteTransition = rememberInfiniteTransition(label = "SyncRotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 900)),
                label = "SyncRotationAngle"
            )
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier
                    .size(34.dp)
                    .bouncyClickable(onClick = onSyncRequest)
                    .testTag("sync_button")
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

        // =========================================================================
        // Панель дней: [Сегодня][Следующий учебный день][Календарь-архив]
        // Левая ячейка — текущий учебный день, правая — следующий учебный день
        // (для 2–4 курсов в пятницу это понедельник; в выходные — Пн и Вт)
        // =========================================================================
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
                    selectedSlot = 0
                    selectedDateString = ""
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
                    selectedSlot = 1
                    selectedDateString = ""
                },
                modifier = Modifier.weight(1f)
            )

            // Календарь-архив: выбор любой даты
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

        // =========================================================================
        // Список пар или чистый Empty State
        // =========================================================================
        AnimatedContent(
            targetState = lessons,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScheduleListAnimation"
        ) { currentLessons ->
            if (isInitialLoad) {
                // Первый кадр: база ещё не отдала кэш — тихий skeleton
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = ColorBrandBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else if (currentLessons.isEmpty()) {
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
                                        text = "СИНХРОНИЗИРОВАТЬ С САЙТОМ",
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
                // Уроки идут не подряд: первых уроков может не быть вовсе (день
                // начинается со 2-го или 3-го). Раньше это выглядело дырой в списке —
                // теперь на месте пропуска стоит пометка «урока нет».
                // Верхнюю границу берём по последнему реальному уроку: про уроки
                // после него ничего не известно, выдумывать их не нужно.
                val slots = remember(currentLessons) {
                    if (currentLessons.isEmpty()) emptyList()
                    else {
                        val byNumber = currentLessons.associateBy { it.lessonNumber }
                        (1..currentLessons.maxOf { it.lessonNumber })
                            .map { number -> number to byNumber[number] }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                                onRoomClick = onRoomClick
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

/**
 * Пометка на месте урока, которого в расписании нет.
 *
 * Нужна, потому что занятия идут не подряд: если первый урок не задан, список
 * начинался сразу со второго и выглядел как недоделанный экран.
 */
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
                    fontSize = 13.sp,
                    color = ColorTextMuted
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    isCurrent: Boolean = false,
    onTeacherClick: (String) -> Unit = {},
    /** Есть ли преподаватель в базе колледжа. Нет в базе — не делаем кликабельным. */
    isTeacherKnown: (String) -> Boolean = { true },
    onRoomClick: (String) -> Unit = {},
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

                // Акроним предмета
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = ColorSurfaceVariantLight,
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

                    // Подгруппа 2
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
                        TeacherChip(
                            name = lesson.teacherFirst,
                            onClick = if (isTeacherKnown(lesson.teacherFirst)) {
                                { onTeacherClick(lesson.teacherFirst) }
                            } else null
                        )
                    }

                    if (lesson.roomFirst.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, ColorBorderLight),
                            color = ColorSurfaceHighlight,
                            modifier = Modifier
                                .wrapContentWidth()
                                // Кабинет ведёт на карту колледжа: видно, где он
                                .bouncyClickable(scaleDown = 0.97f) { onRoomClick(lesson.roomFirst) }
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
/**
 * Имя преподавателя как нажимаемая «таблетка».
 *
 * Раньше в подгруппах это был просто серый текст без обработчика — по нему нельзя
 * было перейти. Теперь и в обычной паре, и при разделении это одинаковый элемент
 * с рамкой и шевроном: рамка и стрелка показывают, что по имени можно нажать.
 *
 * [onClick] = null — преподавателя нет в базе колледжа (например, ведёт только
 * замену). Такой остаётся обычным текстом: открывать по нему нечего, и делать
 * вид, что он нажимается, нельзя.
 */
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

    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
        color = ColorSurfaceHighlight,
        modifier = modifier.bouncyClickable(
            scaleDown = 0.97f,
            onClick = onClick
        )
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
            // Микро-бейдж подгруппы «1» или «2»
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
                Text(
                    text = "каб. $room",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = ColorBrandBlue,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.bouncyClickable(scaleDown = 0.97f) { onRoomClick(room) }
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

/**
 * Ячейка дня на панели расписания: заголовок (СЕГОДНЯ / ЗАВТРА / ПОНЕДЕЛЬНИК) + дата.
 * Выбранная ячейка — заливка ColorTopBar с белым текстом, строгая геометрия 2px.
 */
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
