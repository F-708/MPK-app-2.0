package com.politex.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.MpkDatabase
import com.politex.data.local.dao.GradeDao
import com.politex.data.local.entity.GradeConfigEntity
import com.politex.data.local.entity.GradeEntryEntity
import com.politex.data.model.GroupInfo
import com.politex.ui.components.SimpleDatePickerDialog
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorError
import com.politex.ui.theme.ColorSuccess
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberToday
import com.politex.util.GradeAggregator
import com.politex.util.GradeCategory
import com.politex.util.MpkCurriculum
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale

/**
 * Ф4.2 «Оценки» (агент G, MASTER-PLAN фаза 4.2).
 *
 * G1: три «страницы» в одном файле без nav-графа — список предметов → предмет → вес категорий.
 * Внутренняя навигация двумя rememberSaveable-строками: hub-экран вызывает нас как лист,
 * свой back-стек тут дешевле, чем тянуть Navigation в запрещённую зону LessonsHubScreen.
 * Данные — напрямую из GradeDao (паттерн MaterialBaseScreen), без ViewModel.
 *
 * G2: агрегация — чистый GradeAggregator (util), здесь только подготовка Map<category, values>.
 * G3: цветовая семантика среднего балла: ≥7 зелёный, <4 красный, иначе нейтральный.
 * G4: панель ввода под большой палец — чипы категорий + квадраты 1..10 (40dp), LAB — тумблер
 *     «Зачёт/Незачёт», EXAM — одна запись с заменой (upsertExam) и стиранием (✕ в строке).
 * G5: reorder приоритета категорий кнопками вверх/вниз (v1, ponytail: настоящий drag — позже).
 */
@Composable
fun GradesScreen(
    groupInfo: GroupInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dao = remember { MpkDatabase.getInstance(context).gradeDao() }

    var openSubject by rememberSaveable { mutableStateOf<String?>(null) }
    var prioritySubject by rememberSaveable { mutableStateOf<String?>(null) }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        when {
            prioritySubject != null ->
                GradePriorityScreen(
                    subject = prioritySubject!!,
                    dao = dao,
                    onBack = { prioritySubject = null },
                )
            openSubject != null ->
                GradeSubjectScreen(
                    subject = openSubject!!,
                    dao = dao,
                    onBack = { openSubject = null },
                    onOpenPriority = { prioritySubject = openSubject },
                )
            else ->
                GradeSubjectList(
                    groupInfo = groupInfo,
                    dao = dao,
                    onOpenSubject = { openSubject = it },
                )
        }
    }
}

// ── Список предметов ─────────────────────────────────────────────────────────

@Composable
private fun GradeSubjectList(
    groupInfo: GroupInfo,
    dao: GradeDao,
    onOpenSubject: (String) -> Unit,
) {
    val entries by dao.allEntries().collectAsState(initial = emptyList())
    val configs by dao.allConfigs().collectAsState(initial = emptyList())
    val subjects =
        remember(groupInfo.specialtyCode, groupInfo.course) {
            MpkCurriculum.getSubjects(groupInfo.specialtyCode, groupInfo.course)
        }

    LazyColumn(
        modifier =
        Modifier
            .fillMaxSize()
            .testTag("grades_root_list"),
        contentPadding =
        androidx.compose.foundation.layout
            .PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(subjects) { subject ->
            // G2/G3: средний балл справа крупно, цвет по семантике
            val avg =
                remember(entries, configs, subject) {
                    GradeAggregator.average(
                        byCategory =
                        entries
                            .filter { it.subjectName == subject }
                            .groupBy({ it.category }) { it.value },
                        priority = priorityFor(configs, subject),
                    )
                }
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBgMain,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("grades_subject_row_$subject")
                    .bouncyClickable { onOpenSubject(subject) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = subject,
                        style = TextStyle(fontSize = 14.sp, color = ColorTextTitle),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = GradeAggregator.format(avg),
                        style =
                        TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = gradeColor(avg),
                        ),
                        modifier = Modifier.testTag("grades_avg_$subject"),
                    )
                }
            }
        }
    }
}

// ── Экран предмета ───────────────────────────────────────────────────────────

@Composable
private fun GradeSubjectScreen(
    subject: String,
    dao: GradeDao,
    onBack: () -> Unit,
    onOpenPriority: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = rememberToday()
    val entries by dao.entriesForSubject(subject).collectAsState(initial = emptyList())
    val configs by dao.allConfigs().collectAsState(initial = emptyList())

    val byCategory = remember(entries) { entries.groupBy({ it.category }) { it.value } }
    val avg =
        remember(byCategory, configs, subject) {
            GradeAggregator.average(byCategory, priorityFor(configs, subject))
        }

    var activeCategory by rememberSaveable(subject) { mutableStateOf(GradeCategory.REGULAR.name) }
    // G4: «+ работа» взводит подпись и дату — следующий тап по оценке пишет их
    var pendingWork by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showWorkDialog by remember { mutableStateOf(false) }

    fun submit(value: Int) {
        val entry =
            GradeEntryEntity(
                subjectName = subject,
                category = activeCategory,
                value = value,
                label = pendingWork?.first ?: "",
                dateIso = pendingWork?.second ?: isoOf(today),
            )
        scope.launch {
            if (activeCategory == GradeCategory.EXAM.name) dao.upsertExam(entry) else dao.insert(entry)
            pendingWork = null
        }
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .testTag("grades_subject_screen"),
    ) {
        // Шапка: назад, предмет, шестерёнка приоритета
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("grades_back")) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Назад", tint = ColorTextTitle)
            }
            Text(
                text = subject,
                maxLines = 1,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onOpenPriority,
                modifier = Modifier.size(48.dp).testTag("grades_priority_btn"),
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Вес категорий", tint = ColorTextMuted)
            }
        }

        // G3: ТЕКУЩИЙ средний балл по центру, 32sp
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = GradeAggregator.format(avg),
                style =
                TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = gradeColor(avg),
                ),
                modifier = Modifier.testTag("grades_current_avg"),
            )
        }

        // Секции категорий: список оценок (value, label, дата) — видно, какие работы сданы
        LazyColumn(
            modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("grades_entries_list"),
            contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GradeCategory.values().forEach { category ->
                val categoryEntries = entries.filter { it.category == category.name }
                item(key = "header_${category.name}") {
                    Text(
                        text = category.title + if (categoryEntries.isEmpty()) "" else " · ${categoryEntries.size}",
                        style =
                        TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextMuted,
                        ),
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                if (categoryEntries.isEmpty()) {
                    item(key = "empty_${category.name}") {
                        Text(
                            text = "Пока нет оценок",
                            style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                } else {
                    items(categoryEntries, key = { it.id }) { entry ->
                        GradeEntryRow(
                            entry = entry,
                            onDelete = { scope.launch { dao.deleteById(entry.id) } },
                        )
                    }
                }
            }
        }

        // ── Панель ввода (G4) ──
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = ColorBgMain,
            border = BorderStroke(1.dp, ColorBorderLight),
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("grades_input_panel"),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // «+ работа с подписью»: диалог label + дата (дефолт сегодня)
                    Chip(
                        text = "+ работа",
                        selected = pendingWork != null,
                        tag = "grades_add_work",
                        onClick = { showWorkDialog = true },
                    ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    if (pendingWork != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Chip(
                            text = "${pendingWork!!.first} · ${displayDate(pendingWork!!.second)}",
                            selected = true,
                            tag = "grades_pending_work",
                            onClick = { pendingWork = null },
                        ) { Icon(Icons.Default.Close, contentDescription = "Сбросить подпись", modifier = Modifier.size(14.dp)) }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GradeCategory.values().toList()) { category ->
                        Chip(
                            text = category.title,
                            selected = activeCategory == category.name,
                            tag = "grades_chip_${category.name}",
                            onClick = { activeCategory = category.name },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                when (activeCategory) {
                    GradeCategory.LAB.name ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // G4: LAB — тумблер «Зачёт/Незачёт», value 1/0
                            LabButton(text = "Зачёт", value = 1, tag = "grades_lab_pass", onSubmit = ::submit)
                            LabButton(text = "Незачёт", value = 0, tag = "grades_lab_fail", onSubmit = ::submit)
                        }
                    else ->
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items((1..10).toList()) { value ->
                                NumberKey(value = value, onSubmit = ::submit)
                            }
                        }
                }
            }
        }
    }

    if (showWorkDialog) {
        AddWorkDialog(
            today = today,
            onDismiss = { showWorkDialog = false },
            onConfirm = { label, dateIso ->
                pendingWork = label to dateIso
                showWorkDialog = false
            },
        )
    }
}

/** Строка оценки: value-бейдж, подпись, дата, ✕ (44dp хитбокс). LAB — «Зачёт/Незачёт». */
@Composable
private fun GradeEntryRow(
    entry: GradeEntryEntity,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier =
        Modifier
            .fillMaxWidth()
            .testTag("grades_entry_${entry.id}"),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val valueText =
                if (entry.category == GradeCategory.LAB.name) {
                    if (entry.value == 1) "Зач" else "Нез"
                } else {
                    entry.value.toString()
                }
            Text(
                text = valueText,
                style =
                TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color =
                    if (entry.category == GradeCategory.LAB.name) {
                        ColorTextTitle
                    } else {
                        gradeColor(entry.value.toDouble())
                    },
                ),
                modifier = Modifier.width(34.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label.ifEmpty { GradeCategory.values().first { it.name == entry.category }.title },
                    maxLines = 1,
                    style = TextStyle(fontSize = 13.sp, color = ColorTextTitle),
                )
                Text(
                    text = displayDate(entry.dateIso),
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                )
            }
            Box(
                modifier =
                Modifier
                    .size(44.dp)
                    .bouncyClickable(onClick = onDelete)
                    .testTag("grades_delete_${entry.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Удалить оценку",
                    tint = ColorTextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Квадрат 40dp с цифрой — под большой палец одной руки (G4). */
@Composable
private fun NumberKey(
    value: Int,
    onSubmit: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier =
        Modifier
            .size(40.dp)
            .testTag("grades_num_$value")
            .bouncyClickable { onSubmit(value) },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = value.toString(),
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.LabButton(
    text: String,
    value: Int,
    tag: String,
    onSubmit: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (value == 1) ColorBrandFill else ColorBgMain,
        border = BorderStroke(1.dp, if (value == 1) ColorBrandFill else ColorBorderLight),
        modifier =
        Modifier
            .weight(1f)
            .height(40.dp)
            .testTag(tag)
            .bouncyClickable { onSubmit(value) },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style =
                TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (value == 1) Color.White else ColorTextTitle,
                ),
            )
        }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (selected) ColorBrandBlue else ColorBgMain,
        border = BorderStroke(1.dp, if (selected) ColorBrandBlue else ColorBorderLight),
        modifier =
        Modifier
            .height(30.dp)
            .testTag(tag)
            .bouncyClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                maxLines = 1,
                style =
                TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else ColorTextBody,
                ),
            )
        }
    }
}

/** Диалог «+ работа»: подпись («Практ. №3») + дата, дефолт сегодня (G4). */
@Composable
private fun AddWorkDialog(
    today: Calendar,
    onDismiss: () -> Unit,
    onConfirm: (label: String, dateIso: String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var dateIso by remember { mutableStateOf(isoOf(today)) }
    var showDatePicker by remember { mutableStateOf(false) }
    val year = today.get(Calendar.YEAR)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = {
            Text(
                text = "Работа с подписью",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text("Например: Практ. №3") },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("grades_work_label"),
                )
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier =
                    Modifier
                        .testTag("grades_work_date")
                        .bouncyClickable { showDatePicker = true },
                ) {
                    Text(
                        text = "Дата: ${displayDate(dateIso)}",
                        style = TextStyle(fontSize = 13.sp, color = ColorTextTitle),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label.trim().ifEmpty { "Работа" }, dateIso) },
                modifier = Modifier.testTag("grades_work_confirm"),
            ) { Text("ГОТОВО", style = TextStyle(fontWeight = FontWeight.Bold, color = ColorBrandBlue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ОТМЕНА", style = TextStyle(fontWeight = FontWeight.Bold, color = ColorTextMuted))
            }
        },
    )

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initialDateIso = dateIso,
            minYear = year - 1,
            maxYear = year + 1,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                dateIso = it
                showDatePicker = false
            },
        )
    }
}

// ── Экран «Вес категорий» (G5) ───────────────────────────────────────────────

@Composable
private fun GradePriorityScreen(
    subject: String,
    dao: GradeDao,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf<List<String>?>(null) } // null = загрузка

    LaunchedEffect(subject) {
        order = dao.getConfig(subject)?.let { parsePriority(it.priorityJson) }
            ?: GradeAggregator.DEFAULT_PRIORITY
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .testTag("grades_priority_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Назад", tint = ColorTextTitle)
            }
            Text(
                text = "Вес категорий",
                maxLines = 1,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = subject,
            style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        val list = order
        if (list != null) {
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                list.forEachIndexed { index, categoryName ->
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBgMain,
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("grades_priority_row_$categoryName"),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = null,
                                tint = ColorTextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "${index + 1}. ${titleOf(categoryName)}",
                                style = TextStyle(fontSize = 14.sp, color = ColorTextTitle),
                                modifier = Modifier.weight(1f),
                            )
                            ReorderArrow(
                                tag = "grades_priority_up_$categoryName",
                                up = true,
                                enabled = index > 0,
                                onClick = { order = list.swap(index, index - 1) },
                            )
                            ReorderArrow(
                                tag = "grades_priority_down_$categoryName",
                                up = false,
                                enabled = index < list.lastIndex,
                                onClick = { order = list.swap(index, index + 1) },
                            )
                        }
                    }
                }
            }

            // Сохранение в grade_config (одна строка JSON-массива)
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                border = BorderStroke(1.dp, ColorBrandFill),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .height(44.dp)
                    .testTag("grades_priority_save")
                    .bouncyClickable {
                        scope.launch {
                            dao.upsertConfig(
                                GradeConfigEntity(
                                    subjectName = subject,
                                    priorityJson = JSONArray(list).toString(),
                                ),
                            )
                            onBack()
                        }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "СОХРАНИТЬ",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderArrow(
    tag: String,
    up: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
        Modifier
            .size(44.dp) // D4: хитбокс 44dp
            .testTag(tag)
            .bouncyClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (up) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (up) "Выше" else "Ниже",
            tint = if (enabled) ColorBrandBlue else ColorTextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Мелочи ───────────────────────────────────────────────────────────────────

/** G3: ≥7 зелёный, <4 красный, 4–6.9 нейтральный, нет оценок — приглушённый. */
private fun gradeColor(avg: Double?): Color = when {
    avg == null -> ColorTextMuted
    avg >= 7.0 -> ColorSuccess
    avg < 4.0 -> ColorError
    else -> ColorTextTitle
}

/** Приоритет предмета из grade_config или дефолт; битый JSON = дефолт (не роняем экран). */
private fun priorityFor(
    configs: List<GradeConfigEntity>,
    subject: String,
): List<String> = configs.firstOrNull { it.subjectName == subject }?.let { parsePriority(it.priorityJson) }
    ?: GradeAggregator.DEFAULT_PRIORITY

private fun parsePriority(json: String): List<String> = try {
    val arr = JSONArray(json)
    (0 until arr.length())
        .map { arr.getString(it) }
        .filter { name -> name != GradeCategory.LAB.name && GradeCategory.values().any { it.name == name } }
        .ifEmpty { GradeAggregator.DEFAULT_PRIORITY }
} catch (_: Exception) {
    GradeAggregator.DEFAULT_PRIORITY
}

private fun titleOf(categoryName: String): String = GradeCategory.values().firstOrNull { it.name == categoryName }?.title ?: categoryName

private fun <T> List<T>.swap(
    i: Int,
    j: Int,
): List<T> = toMutableList().also {
    it[i] = it[j].also { tmp -> it[j] = it[i] }
}

private fun isoOf(cal: Calendar): String = String.format(
    Locale.ROOT,
    "%04d-%02d-%02d",
    cal.get(Calendar.YEAR),
    cal.get(Calendar.MONTH) + 1,
    cal.get(Calendar.DAY_OF_MONTH),
)

/** yyyy-MM-dd → dd.MM.yyyy (конвенция показа дат проекта). */
private fun displayDate(iso: String): String {
    val p = iso.split("-")
    return if (p.size == 3) "${p[2]}.${p[1]}.${p[0]}" else iso
}
