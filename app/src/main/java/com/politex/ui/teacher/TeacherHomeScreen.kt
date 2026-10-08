package com.politex.ui.teacher

import android.content.Context
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
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.politex.data.local.entity.TeacherOverrideEntity
import com.politex.data.model.CollegeMap
import com.politex.data.model.CollegeMapData
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
import com.politex.ui.theme.ColorErrorText
import com.politex.ui.theme.ColorSuccess
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.util.bouncyClickable
import com.politex.util.DateFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

// Экран «Мой день» преподавателя (SPEC §4.1 фича 3, §6.3).
@Composable
fun TeacherHomeScreen(
    profile: TeacherProfile?,
    onPickProfile: () -> Unit = {},
    onRoomClick: (String) -> Unit = {},
    onGroupClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeacherDayViewModel = viewModel(),
) {
    val metrics = LocalTeacherMetrics.current
    val state by viewModel.state.collectAsState()
    LaunchedEffect(profile) { viewModel.setProfile(profile) }
    // SPEC §4.1 фича 10: пара, для которой открыт bottom sheet «Править».
    var editing by remember { mutableStateOf<TeacherDayLesson?>(null) }

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

    val staleData = remember(state.lastSyncEpoch) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        state.lastSyncEpoch < cal.timeInMillis
    }
    val notifPrefs = remember { context.getSharedPreferences("mpk_teacher_profile", android.content.Context.MODE_PRIVATE) }
    var notifPromptShown by remember { mutableStateOf(notifPrefs.getBoolean("notif_prompt_shown", false)) }
    val notifGranted =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifPrefs.edit().putBoolean("notif_prompt_shown", true).apply()
        notifPromptShown = true
    }

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

        // SPEC §5.4: молчаливый сброс правки запрещён — одноразовая плашка до закрытия.
        if (state.overrideResets.isNotEmpty()) {
            AppCard(
                color = ColorSurfaceHighlight,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("teacher_reset_plate"),
            ) {
                Row(modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.overrideResets.forEach { reset ->
                            Text(
                                TeacherDayLogic.resetMessage(reset),
                                style = TextStyle(fontSize = metrics.chipText, color = ColorTextBody),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.size(metrics.hitTarget).bouncyClickable(onClick = viewModel::dismissOverrideResets).testTag("teacher_reset_close"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = ColorTextMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (!notifPromptShown && !notifGranted && !state.loading) {
            AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Уведомлять об изменениях расписания?",
                        style = TextStyle(fontSize = metrics.body, color = ColorTextTitle, fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorTopBar,
                            modifier = Modifier.heightIn(min = metrics.hitTarget).bouncyClickable(
                                onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            ).testTag("teacher_notif_enable"),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text("Включить", color = Color.White, fontSize = metrics.buttonText, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        TextButton(
                            onClick = {
                                notifPrefs.edit().putBoolean("notif_prompt_shown", true).apply()
                                notifPromptShown = true
                            },
                            modifier = Modifier.heightIn(min = metrics.hitTarget),
                        ) {
                            Text("Позже", color = ColorTextMuted, fontSize = metrics.buttonText)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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
                        text = state.error.ifBlank { TeacherDayLogic.emptyDayText(state.dow, state.nextLesson, isToday = state.dayOffset == 0) },
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
                    itemsIndexed(state.lessons, key = { i, l -> "${l.lessonNumber}_${l.subject}_$i" }) { i, lesson ->
                        TeacherLessonCard(
                            lesson = lesson,
                            status = statuses.getOrElse(i) { LessonStatus.UPCOMING },
                            metrics = metrics,
                            onRoomClick = onRoomClick,
                            onGroupClick = onGroupClick,
                            onEdit = { editing = lesson },
                            staleData = staleData,
                        )
                    }
                }
        }
    }

    editing?.let { lesson ->
        TeacherOverrideSheet(
            lesson = lesson,
            onSave = { kind, room ->
                viewModel.saveOverride(lesson.lessonNumber, kind, room)
                editing = null
            },
            onCancelOverride = {
                viewModel.cancelOverride(lesson.lessonNumber)
                editing = null
            },
            onDismiss = { editing = null },
        )
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
    onGroupClick: (String) -> Unit = {},
    onEdit: () -> Unit = {},
    staleData: Boolean = false,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (lesson.isReplacement) {
                // SPEC §6.6 (контраст ≥4.5): ColorWarning #F59E0B на белом даёт ~2.1:1 — берём тёмный янтарь (5.0:1)
                StatusLabel("ЗАМЕНА", ColorWarningText)
            }
            when (status) {
                LessonStatus.NOW -> StatusLabel("ИДЁТ", ColorSuccess)
                LessonStatus.NEXT -> StatusLabel("СЛЕДУЮЩАЯ", ColorActiveBlue)
                LessonStatus.PAST -> StatusLabel("ПРОШЛА", ColorTextMuted)
                LessonStatus.UPCOMING -> Unit
            }
            if (staleData && (status == LessonStatus.NOW || status == LessonStatus.NEXT)) {
                Text(
                    "по данным на вчерашний вечер — сверьтесь с чатом учебной части",
                    style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted),
                    modifier = Modifier.padding(bottom = 4.dp).testTag("teacher_stale_plate"),
                )
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
                                // SPEC §4.1 фича 11: тап по чипу группы → «мои пары у этой группы»
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = ColorSurfaceHighlight,
                                    modifier = Modifier.bouncyClickable(scaleDown = 0.95f) { onGroupClick(g) }.testTag("teacher_group_chip_$g"),
                                ) {
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (lesson.overridden) {
                    Text(
                        "✎ ваша правка",
                        style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted, fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(end = 8.dp).testTag("teacher_override_mark"),
                    )
                }
                // SPEC §6.3/§5.4: вход в правку одним тапом, hit ≥48dp
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = ColorSurfaceHighlight,
                    modifier = Modifier.heightIn(min = metrics.hitTarget).bouncyClickable(onClick = onEdit).testTag("teacher_edit_${lesson.lessonNumber}"),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 18.dp)) {
                        Text("Править", style = TextStyle(fontSize = metrics.buttonText, fontWeight = FontWeight.SemiBold, color = ColorBrandBlue))
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
    Text(text, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color), modifier = Modifier.padding(bottom = 4.dp))
}

/** Тёмный янтарь для бейджа ЗАМЕНА: ColorWarning не проходит §6.6 на белом фоне. */
private val ColorWarningText = Color(0xFFB45309)

private fun minutesNow(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

private fun slotSubtitle(offset: Int): String {
    val cal = teacherTargetCalendar(offset, null)
    return DateFormats.dotDate(cal).dropLast(5) + ", " + TeacherDayLogic.shortDow(com.politex.util.Dow.of(cal))
}

// ——— SPEC §4.1 фича 10 / §5.4: bottom sheet «Править» ———

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TeacherOverrideSheet(
    lesson: TeacherDayLesson,
    onSave: (kind: String, room: String) -> Unit,
    onCancelOverride: () -> Unit,
    onDismiss: () -> Unit,
) {
    val metrics = LocalTeacherMetrics.current
    val context = LocalContext.current
    // overridden && !isReplacement == OTHER_ROOM: сразу раскрываем ввод с текущим кабинетом
    var otherRoom by remember { mutableStateOf(lesson.overridden && !lesson.isReplacement) }
    var room by remember(lesson.room) { mutableStateOf(lesson.room) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(lesson.room) {
        suggestions = withContext(Dispatchers.IO) { floorRoomSuggestions(context, lesson.room) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ColorBgMain,
        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                "${lesson.lessonNumber}-я пара · ${lesson.subject.ifBlank { "Пара" }}",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ColorTextTitle),
            )
            if (lesson.start.isNotBlank()) {
                Text(
                    "${lesson.start}–${lesson.end}" + if (lesson.room.isNotBlank()) " · каб. ${lesson.room}" else "",
                    style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted),
                )
            }
            Spacer(Modifier.height(10.dp))
            OverrideSheetItem("Другой кабинет", selected = otherRoom, metrics = metrics, tag = "override_other_room") { otherRoom = true }
            if (otherRoom) {
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Кабинет", fontSize = 12.sp) },
                    placeholder = { Text("Например: 241", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("override_room_field"),
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { s ->
                            val picked = s == room.trim()
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(1.dp, if (picked) ColorTopBar else ColorBorderLight),
                                color = if (picked) ColorTopBar else ColorSurfaceHighlight,
                                modifier = Modifier.heightIn(min = 40.dp).bouncyClickable(scaleDown = 0.97f) { room = s }.testTag("override_room_chip_$s"),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(s, style = TextStyle(fontSize = metrics.chipText, color = if (picked) Color.White else ColorBrandBlue))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorTopBar,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = metrics.hitTarget)
                            .bouncyClickable(enabled = room.isNotBlank(), onClick = { onSave(TeacherOverrideEntity.KIND_OTHER_ROOM, room) })
                            .testTag("override_save_room"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Сохранить", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = metrics.buttonText)
                    }
                }
            }
            OverrideSheetItem("Пары нет", selected = false, metrics = metrics, tag = "override_no_lesson") {
                onSave(TeacherOverrideEntity.KIND_NO_LESSON, "")
            }
            OverrideSheetItem("Замена — веду не я", selected = lesson.isReplacement, metrics = metrics, tag = "override_someone_else") {
                onSave(TeacherOverrideEntity.KIND_SOMEONE_ELSE, "")
            }
            if (lesson.overridden) {
                OverrideSheetItem("Отменить правку", selected = false, metrics = metrics, tag = "override_cancel", danger = true, onClick = onCancelOverride)
            }
        }
    }
}

@Composable
private fun OverrideSheetItem(
    text: String,
    selected: Boolean,
    metrics: TeacherMetrics,
    tag: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val textColor =
        when {
            // SPEC §6.6: ColorError #EF4444 на белом = 3.8:1, для текста берём errorText (4.8:1)
            danger -> ColorErrorText
            selected -> ColorBrandBlue
            else -> ColorTextBody
        }
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (selected) ColorSurfaceHighlight else Color.Transparent,
        modifier = Modifier.fillMaxWidth().heightIn(min = metrics.hitTarget).bouncyClickable(onClick = onClick).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(text, style = TextStyle(fontSize = metrics.body, color = textColor, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
        }
    }
}

/** Комнаты того же этажа/корпуса из college_map.json — быстрые чипы ввода кабинета. */
private fun floorRoomSuggestions(
    context: Context,
    room: String,
): List<String> {
    val floor = CollegeMap.floorForRoom(room) ?: return emptyList()
    val prefix = room.trim().takeWhile { it.isDigit() }.take(1)
    return runCatching {
        CollegeMapData
            .load(context)
            .floor(floor.id)
            .rooms
            .mapNotNull { it.number.trim().takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())
        .filter { prefix.isEmpty() || it.startsWith(prefix) }
        .distinct()
        .sorted()
        .take(16)
}
