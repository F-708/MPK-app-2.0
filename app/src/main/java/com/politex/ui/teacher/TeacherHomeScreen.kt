package com.politex.ui.teacher

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.politex.data.network.MpkNetworkClient
import com.politex.data.repository.LessonStatus
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLogic
import com.politex.data.repository.teacherTargetCalendar
import com.politex.ui.components.AppCard
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorSuccess
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.util.bouncyClickable
import com.politex.util.DateFormats
import kotlinx.coroutines.delay
import java.util.Calendar

// Экран «Мой день» преподавателя (SPEC §4.1 фича 3, §6.3).
@Composable
fun TeacherHomeScreen(
    profile: TeacherProfile?,
    onPickProfile: () -> Unit = {},
    onRoomClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeacherDayViewModel = viewModel(),
) {
    val metrics = LocalTeacherMetrics.current
    val state by viewModel.state.collectAsState()
    LaunchedEffect(profile) { viewModel.setProfile(profile) }

    if (profile == null) {
        Column(
            modifier = modifier.fillMaxSize().background(ColorBgMain).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Выберите себя", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColorTextTitle))
            Spacer(Modifier.height(8.dp))
            Text(
                "Чтобы увидеть свой день, найдите себя в списке преподавателей колледжа.",
                style = TextStyle(fontSize = metrics.body, color = ColorTextMuted),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorTopBar,
                modifier = Modifier.heightIn(min = metrics.hitTarget).bouncyClickable(onClick = onPickProfile).testTag("teacher_pick_profile"),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text("Выбрать", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = metrics.buttonText)
                }
            }
        }
        return
    }

    val context = LocalContext.current
    var nowMinutes by remember { mutableIntStateOf(minutesNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMinutes = minutesNow()
        }
    }
    val statuses =
        remember(state.lessons, nowMinutes, state.dayOffset) {
            TeacherDayLogic.statuses(state.lessons, nowMinutes, isToday = state.dayOffset == 0)
        }
    val eveningTomorrowHint = remember(nowMinutes) { nowMinutes >= 20 * 60 }

    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ColorTextTitle), maxLines = 2)
                Text(
                    text =
                        if (state.lastSyncEpoch > 0L) {
                            "обновлено в " +
                                java.text.SimpleDateFormat("HH:mm", java.util.Locale.forLanguageTag("ru"))
                                    .format(java.util.Date(state.lastSyncEpoch))
                        } else {
                            "данные кэша"
                        },
                    style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted),
                )
            }
            Box(
                modifier = Modifier.size(metrics.hitTarget).bouncyClickable { viewModel.refresh() }.testTag("teacher_refresh"),
                contentAlignment = Alignment.Center,
            ) {
                if (state.refreshing) {
                    CircularProgressIndicator(color = ColorBrandBlue, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = ColorBrandBlue, modifier = Modifier.size(22.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TeacherDaySlotCell("СЕГОДНЯ", slotSubtitle(0), selected = state.dayOffset == 0, onClick = { viewModel.selectDay(0) }, modifier = Modifier.weight(1f), tag = "teacher_day_today")
            TeacherDaySlotCell("ЗАВТРА", slotSubtitle(1), selected = state.dayOffset == 1, onClick = { viewModel.selectDay(1) }, modifier = Modifier.weight(1f), tag = "teacher_day_tomorrow")
        }

        if (state.dayOffset == 1 && eveningTomorrowHint) {
            Text(
                "Расписание на завтра могло ещё не опубликоваться — сверьтесь с чатом учебной части",
                style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        val listState = rememberLazyListState()
        LaunchedEffect(statuses, state.dayOffset) {
            val idx = statuses.indexOfFirst { it == LessonStatus.NOW || it == LessonStatus.NEXT }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }

        when {
            state.loading && state.lessons.isEmpty() ->
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ColorBrandBlue, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }

            state.lessons.isEmpty() ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.error.ifBlank { TeacherDayLogic.emptyDayText(state.dow, state.nextLesson) },
                        style = TextStyle(fontSize = metrics.body, color = ColorTextMuted),
                        textAlign = TextAlign.Center,
                    )
                    if (state.error.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Открыть расписание на сайте колледжа",
                            style = TextStyle(fontSize = metrics.buttonText, fontWeight = FontWeight.SemiBold, color = ColorActiveBlue),
                            modifier =
                                Modifier
                                    .heightIn(min = metrics.hitTarget)
                                    .bouncyClickable {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MpkNetworkClient.SCHEDULE_PORTAL_URL)))
                                        }
                                    }.testTag("teacher_open_site"),
                        )
                    }
                }

            else ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.ambiguous) {
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
                    itemsIndexed(state.lessons, key = { i, l -> "${l.lessonNumber}_$i" }) { i, lesson ->
                        TeacherLessonCard(
                            lesson = lesson,
                            status = statuses.getOrElse(i) { LessonStatus.UPCOMING },
                            metrics = metrics,
                            onRoomClick = onRoomClick,
                        )
                    }
                }
        }
    }
}

@Composable
private fun TeacherDaySlotCell(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "",
) {
    val metrics = LocalTeacherMetrics.current
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        color = if (selected) ColorTopBar else ColorBgMain,
        modifier = modifier.height(metrics.hitTarget).bouncyClickable(onClick = onClick).testTag(tag),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                title,
                softWrap = false,
                maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = metrics.chipText, color = if (selected) Color.White else ColorTextBody),
            )
            Text(
                subtitle,
                softWrap = false,
                maxLines = 1,
                style = TextStyle(fontSize = 10.sp, color = if (selected) Color.White.copy(alpha = 0.75f) else ColorTextMuted),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeacherLessonCard(
    lesson: TeacherDayLesson,
    status: LessonStatus,
    metrics: TeacherMetrics,
    onRoomClick: (String) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            when (status) {
                LessonStatus.NOW -> StatusLabel("ИДЁТ", ColorSuccess)
                LessonStatus.NEXT -> StatusLabel("СЛЕДУЮЩАЯ", ColorActiveBlue)
                LessonStatus.PAST -> StatusLabel("ПРОШЛА", ColorTextMuted)
                LessonStatus.UPCOMING -> Unit
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (lesson.start.isNotBlank()) {
                        Text(
                            "${lesson.start}–${lesson.end}",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (status == LessonStatus.PAST) ColorTextMuted else ColorTextTitle),
                        )
                    }
                    Text(
                        lesson.subject.ifBlank { "Пара" },
                        style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = metrics.body, color = if (status == LessonStatus.PAST) ColorTextMuted else ColorTextBody),
                    )
                    if (lesson.groups.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            lesson.groups.forEach { g ->
                                Surface(shape = RoundedCornerShape(2.dp), color = ColorSurfaceHighlight) {
                                    Text(g, style = TextStyle(fontSize = metrics.chipText, color = ColorBrandBlue, fontWeight = FontWeight.Medium), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }
                if (lesson.room.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // SPEC §6.3: кабинет — КРУПНЕЙШИЙ шрифт экрана
                        Text(lesson.room, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, color = ColorBrandBlue))
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, ColorBorderLight),
                            color = ColorSurfaceHighlight,
                            modifier = Modifier.size(metrics.hitTarget).bouncyClickable(scaleDown = 0.97f) { onRoomClick(lesson.room) }.testTag("teacher_room_${lesson.room}"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Flag, contentDescription = "Как дойти до кабинета ${lesson.room}", tint = ColorBrandBlue, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(
    text: String,
    color: Color,
) {
    Text(text, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = color), modifier = Modifier.padding(bottom = 4.dp))
}

private fun minutesNow(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

private fun slotSubtitle(offset: Int): String {
    val cal = teacherTargetCalendar(offset, null)
    return DateFormats.dotDate(cal).dropLast(5) + ", " + TeacherDayLogic.shortDow(com.politex.util.Dow.of(cal))
}
