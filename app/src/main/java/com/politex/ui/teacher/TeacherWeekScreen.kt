package com.politex.ui.teacher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.politex.AppGraph
import com.politex.data.network.MpkScheduleParser
import com.politex.data.repository.TeacherDay
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLoader
import com.politex.data.repository.TeacherDayLogic
import com.politex.ui.components.AppCard
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.util.bouncyClickable
import com.politex.util.DateFormats
import com.politex.util.Dow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

// Экран «Неделя» преподавателя (SPEC §4.1 фича 9, §6.4): полоса Пн–Сб + нагрузка.

enum class TeacherWeekTab { DAYS, LOAD }

data class TeacherWeekDayState(
    val lessons: List<TeacherDayLesson> = emptyList(),
    val error: String = "",
    val hasData: Boolean = false,
    val loaded: Boolean = false,
    val ambiguous: Boolean = false,
)

data class TeacherWeekState(
    val tab: TeacherWeekTab = TeacherWeekTab.DAYS,
    val weekDates: List<String> = emptyList(),
    val selectedDate: String = "",
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val days: Map<String, TeacherWeekDayState> = emptyMap(),
)

class TeacherWeekViewModel(
    app: android.app.Application,
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(TeacherWeekState())
    val state: StateFlow<TeacherWeekState> = _state.asStateFlow()

    private var profile: TeacherProfile? = null
    private var week: List<Calendar> = emptyList()
    private var loadJob: Job? = null

    fun setProfile(p: TeacherProfile?) {
        if (p == profile) return
        profile = p
        if (p == null) {
            _state.update { it.copy(loading = false) }
        } else {
            startWeek(TeacherWeekLogic.mondayToSaturday(Calendar.getInstance()))
        }
    }

    fun selectTab(tab: TeacherWeekTab) = _state.update { it.copy(tab = tab) }

    fun selectDate(date: String) = _state.update { it.copy(selectedDate = date) }

    fun shiftWeek(weeks: Int) {
        if (week.isEmpty()) return
        startWeek(TeacherWeekLogic.shiftWeek(week, weeks * 7))
    }

    // SPEC §4.1 фича 9: force-синк ТОЛЬКО выбранного дня (неделя целиком — дорого).
    fun refresh() {
        val p = profile ?: return
        val sel = _state.value.selectedDate
        if (sel.isBlank() || _state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val cal = MpkScheduleParser.parseDotDate(sel)
            // SPEC §4.1 фича 9: force-синк только выбранного дня.
            val ok =
                cal?.let {
                    runCatching { AppGraph.scheduleRepository.syncTeacherScheduleForDate(it, force = true).isSuccess }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrDefault(false)
                } ?: false
            if (ok) TeacherDayLoader.markSyncedNow(getApplication())
            if (cal != null) {
                val day = TeacherDayLoader.loadDay(getApplication(), AppGraph.scheduleRepository, p, sel)
                _state.update { st -> st.copy(refreshing = false, days = st.days + (sel to day.toUi())) }
            } else {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    private fun startWeek(newWeek: List<Calendar>) {
        val p = profile ?: return
        week = newWeek
        val dates = newWeek.map { DateFormats.dotDate(it) }
        val today = DateFormats.dotDate(Calendar.getInstance())
        loadJob?.cancel()
        _state.update {
            it.copy(
                weekDates = dates,
                selectedDate = if (today in dates) today else dates.first(),
                loading = true,
                days = emptyMap(),
            )
        }
        loadJob =
            viewModelScope.launch {
                val ctx = getApplication<android.app.Application>()
                val repo = AppGraph.scheduleRepository
                // Последовательно, кэш-первым (loadDay); счётчик «N из 6» обновляется после каждого дня.
                for ((cal, date) in newWeek.zip(dates)) {
                    ensureActive()
                    val dow = Dow.of(cal)
                    val day =
                        if (dow == 7) {
                            TeacherDay(emptyList(), false, "", false, 0L) // воскресенье не входит в Пн–Сб
                        } else {
                            TeacherDayLoader.loadDay(ctx, repo, p, date)
                        }
                    _state.update { st -> st.copy(days = st.days + (date to day.toUi())) }
                }
                _state.update { it.copy(loading = false) }
            }
    }

    private fun TeacherDay.toUi() =
        TeacherWeekDayState(
            lessons = lessons,
            error = error,
            hasData = isSynced,
            loaded = true,
            ambiguous = ambiguous,
        )
}

// SPEC §6.4: дисклеймер НА ЦИФРЕ, не в футере.
private const val LOAD_DISCLAIMER = "Справочно. Замены и правки учебной части не учтены. Первоисточник — тарификация"

@Composable
fun TeacherWeekScreen(
    profile: TeacherProfile?,
    onRoomClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeacherWeekViewModel = viewModel(),
) {
    val metrics = LocalTeacherMetrics.current
    val state by viewModel.state.collectAsState()
    LaunchedEffect(profile) { viewModel.setProfile(profile) }
    if (profile == null) return

    val weekCals = remember(state.weekDates) { state.weekDates.map { MpkScheduleParser.parseDotDate(it) } }
    val counter =
        remember(state.days, state.loading) {
            if (state.loading) null else TeacherWeekLogic.daysWithDataCounter(state.days.values.count { it.hasData })
        }

    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    weekRangeLabel(state.weekDates),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ColorTextTitle),
                    maxLines = 1,
                )
                Text(
                    text = counter?.let { "данные: $it дней" } ?: "Пн–Сб",
                    style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted),
                    modifier = Modifier.testTag("teacher_week_counter"),
                )
            }
            Box(
                modifier = Modifier.size(metrics.hitTarget).bouncyClickable { viewModel.refresh() }.testTag("teacher_week_refresh"),
                contentAlignment = Alignment.Center,
            ) {
                if (state.refreshing) {
                    CircularProgressIndicator(color = ColorBrandBlue, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить выбранный день", tint = ColorBrandBlue, modifier = Modifier.size(22.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WeekTabCell("НЕДЕЛЯ", state.tab == TeacherWeekTab.DAYS, { viewModel.selectTab(TeacherWeekTab.DAYS) }, Modifier.weight(1f), "teacher_week_tab_days")
            WeekTabCell("НАГРУЗКА", state.tab == TeacherWeekTab.LOAD, { viewModel.selectTab(TeacherWeekTab.LOAD) }, Modifier.weight(1f), "teacher_week_tab_load")
        }
        Spacer(Modifier.height(8.dp))

        if (state.tab == TeacherWeekTab.DAYS) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WeekArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущая неделя", "teacher_week_prev") { viewModel.shiftWeek(-1) }
                state.weekDates.forEachIndexed { i, date ->
                    val cal = weekCals.getOrNull(i) ?: return@forEachIndexed
                    val day = state.days[date]
                    WeekDayCell(
                        dateKey = date,
                        dayNumber = cal.get(Calendar.DAY_OF_MONTH),
                        dow = Dow.of(cal),
                        count = day?.lessons?.size ?: 0,
                        loaded = day?.loaded == true,
                        selected = date == state.selectedDate,
                        onClick = { viewModel.selectDate(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
                WeekArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующая неделя", "teacher_week_next") { viewModel.shiftWeek(1) }
            }
            Spacer(Modifier.height(4.dp))

            val selectedCal = weekCals.getOrNull(state.weekDates.indexOf(state.selectedDate))
            val day = state.days[state.selectedDate]
            when {
                day == null || !day.loaded ->
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ColorBrandBlue, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                    }

                day.lessons.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = emptyDayTextFor(day, selectedCal),
                            style = TextStyle(fontSize = metrics.body, color = ColorTextMuted),
                            textAlign = TextAlign.Center,
                        )
                    }

                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (day.ambiguous) {
                            item {
                                AppCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "Не удалось точно определить (однофамильцы) — проверьте список",
                                        style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = metrics.chipText, color = ColorTextMuted),
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                            }
                        }
                        itemsIndexed(day.lessons, key = { i, l -> "${l.lessonNumber}_${l.subject}_$i" }) { _, lesson ->
                            WeekLessonCard(lesson = lesson, metrics = metrics, onRoomClick = onRoomClick)
                        }
                    }
            }
        } else {
            WeekLoadContent(state)
        }
    }
}

private fun emptyDayTextFor(
    day: TeacherWeekDayState,
    cal: Calendar?,
): String =
    when {
        day.error.isNotBlank() -> day.error
        cal != null && TeacherWeekLogic.outsideCacheWindow(cal) -> "Нет данных (кэш 60 дней) — неделя вне окна кэша расписания"
        else -> TeacherDayLogic.emptyDayText(cal?.let { Dow.of(it) } ?: 0, next = null, isToday = false)
    }

private fun weekRangeLabel(dates: List<String>): String =
    if (dates.size < 6) {
        "Неделя"
    } else {
        "${dates.first().take(5)}–${dates.last().take(5)}.${dates.last().takeLast(4)}"
    }

@Composable
private fun WeekTabCell(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String,
) {
    val metrics = LocalTeacherMetrics.current
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        color = if (selected) ColorTopBar else ColorBgMain,
        modifier = modifier.height(metrics.hitTarget).bouncyClickable(onClick = onClick).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                title,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = metrics.chipText, color = if (selected) Color.White else ColorTextBody),
            )
        }
    }
}

@Composable
private fun WeekArrow(
    icon: ImageVector,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    val metrics = LocalTeacherMetrics.current
    Box(
        modifier = Modifier.size(metrics.hitTarget).bouncyClickable(onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = ColorBrandBlue, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun WeekDayCell(
    dateKey: String,
    dayNumber: Int,
    dow: Int,
    count: Int,
    loaded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalTeacherMetrics.current
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        color = if (selected) ColorTopBar else ColorBgMain,
        modifier = modifier.heightIn(min = metrics.hitTarget).bouncyClickable(onClick = onClick).testTag("teacher_week_day_$dateKey"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$dayNumber",
                maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (selected) Color.White else ColorTextBody),
            )
            Text(
                TeacherDayLogic.shortDow(dow),
                maxLines = 1,
                style = TextStyle(fontSize = 10.sp, color = if (selected) Color.White.copy(alpha = 0.75f) else ColorTextMuted),
            )
            // Бейдж числа пар; пустая строка, чтобы все ячейки были одной высоты
            Text(
                if (loaded && count > 0) "$count" else " ",
                maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (selected) Color.White else ColorBrandBlue),
            )
        }
    }
}

// ponytail: временный дубль приватной TeacherLessonCard из TeacherHomeScreen (тот файл правит агент A,
// статусы/флаг не нужны); при слиянии вынести общий вариант в ui/teacher/TeacherLessonCards.kt
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekLessonCard(
    lesson: TeacherDayLesson,
    metrics: TeacherMetrics,
    onRoomClick: (String) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (lesson.start.isNotBlank()) {
                    Text(
                        "${lesson.start}–${lesson.end}",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitle),
                    )
                }
                Text(
                    lesson.subject.ifBlank { "Пара" },
                    style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = metrics.body, color = ColorTextBody),
                )
                if (lesson.groups.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        lesson.groups.forEach { g ->
                            Surface(shape = RoundedCornerShape(2.dp), color = ColorSurfaceHighlight) {
                                Text(
                                    g,
                                    style = TextStyle(fontSize = metrics.chipText, color = ColorBrandBlue, fontWeight = FontWeight.Medium),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (lesson.room.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    lesson.room,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, color = ColorBrandBlue),
                    modifier =
                        Modifier
                            .heightIn(min = metrics.hitTarget)
                            .wrapContentHeight()
                            .bouncyClickable(scaleDown = 0.97f) { onRoomClick(lesson.room) }
                            .testTag("teacher_week_room_${lesson.room}"),
                )
            }
        }
    }
}

@Composable
private fun WeekLoadContent(state: TeacherWeekState) {
    val metrics = LocalTeacherMetrics.current
    val load =
        remember(state.days, state.weekDates) {
            TeacherWeekLogic.aggregateLoad(state.weekDates.map { state.days[it]?.lessons ?: emptyList() })
        }
    if (load.totalLessons == 0) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = if (state.loading) "Загрузка…" else "Нет данных о парах за эту неделю",
                style = TextStyle(fontSize = metrics.body, color = ColorTextMuted),
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Пар за неделю", style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted))
                            Text("${load.totalLessons}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ColorTextTitle), modifier = Modifier.testTag("teacher_week_lessons_total"))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Часы", style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted))
                            Text(
                                TeacherWeekLogic.formatMinutes(load.totalMinutes),
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ColorTextTitle),
                                modifier = Modifier.testTag("teacher_week_hours_total"),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // SPEC §6.4: дисклеймер сразу под цифрами (§6.6: не мельче chipText)
                    Text(
                        LOAD_DISCLAIMER,
                        style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted),
                        modifier = Modifier.testTag("teacher_week_load_disclaimer"),
                    )
                }
            }
        }
        if (load.bySubject.isNotEmpty()) {
            item { WeekStatCard("По предметам", load.bySubject, "teacher_week_by_subject") }
        }
        if (load.byGroup.isNotEmpty()) {
            item { WeekStatCard("По группам", load.byGroup, "teacher_week_by_group") }
        }
    }
}

@Composable
private fun WeekStatCard(
    title: String,
    rows: List<Pair<String, Int>>,
    tag: String,
) {
    val metrics = LocalTeacherMetrics.current
    AppCard(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = metrics.chipText, color = ColorTextTitle))
            Spacer(Modifier.height(6.dp))
            rows.forEach { (name, minutes) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(name, style = TextStyle(fontSize = metrics.chipText, color = ColorTextBody), modifier = Modifier.weight(1f), maxLines = 2)
                    Spacer(Modifier.width(8.dp))
                    Text(TeacherWeekLogic.formatMinutes(minutes), style = TextStyle(fontSize = metrics.chipText, fontWeight = FontWeight.SemiBold, color = ColorBrandBlue))
                }
            }
        }
    }
}
