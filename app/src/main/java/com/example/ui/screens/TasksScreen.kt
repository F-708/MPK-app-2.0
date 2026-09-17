package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.StudentTaskEntity
import com.example.data.model.GroupInfo
import com.example.data.model.TaskType
import com.example.data.repository.TaskRepository
import com.example.ui.theme.ColorActiveBlue
import com.example.ui.theme.ColorBadgeCourse
import com.example.ui.theme.ColorBadgeDiploma
import com.example.ui.theme.ColorBadgeExam
import com.example.ui.theme.ColorBadgeHW
import com.example.ui.theme.ColorBadgeLab
import com.example.ui.theme.ColorBadgePract
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorDividerLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.theme.ColorTopBar
import com.example.ui.theme.TextStylePageTitle
import com.example.ui.util.bouncyClickable
import com.example.util.MpkCurriculum
import kotlinx.coroutines.launch
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorSurfaceHighlight
import com.example.ui.theme.ColorSurfaceVariantLight

/**
 * Экран учебных заданий студента по официальному Style Guide МПК:
 * - Заголовок страницы «Учебные задания» (32-34px Bold, Sentence case)
 * - 0-2dp геометрия карточек и чипов
 * - 0dp elevation
 * - 1px рамки #E2E8F0
 * - Бейдж строго «ДЗ» (не «ДЗ на следующий раз»)
 * - Курсовые только для 2-4 курсов, диплом — только для 4 курса
 */
@Composable
fun TasksScreen(
    groupInfo: GroupInfo,
    taskRepository: TaskRepository,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf<TaskType?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val allTasksFlow = remember(groupInfo.canonicalName) {
        taskRepository.getTasksForGroup(groupInfo.canonicalName)
    }
    val allTasks: List<StudentTaskEntity> by allTasksFlow.collectAsState(initial = emptyList())

    val filteredTasks = remember(allTasks, selectedFilter) {
        if (selectedFilter == null) {
            allTasks
        } else {
            allTasks.filter { it.taskTypeEnum == selectedFilter }
        }
    }

    val availableTypes = remember(groupInfo.course) {
        TaskType.getAvailableForCourse(groupInfo.course)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Заголовок страницы
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Учебные задания",
                        style = TextStylePageTitle,
                        maxLines = 1
                    )
                    Text(
                        text = "Группа ${groupInfo.canonicalName} • ${allTasks.count { !it.isCompleted }} активных",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = ColorBrandBlue
                        ),
                        maxLines = 1
                    )
                }

                // Кнопка добавления (фиксированной ширины, без переносов букв)
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBrandBlue),
                    color = ColorBrandFill,
                    modifier = Modifier
                        .bouncyClickable { showAddDialog = true }
                        .testTag("add_task_top_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Добавить",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ДОБАВИТЬ",
                            softWrap = false,
                            maxLines = 1,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            // Горизонтальный ряд фильтров (Все + типы заданий)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val isSelected = selectedFilter == null
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                        color = if (isSelected) ColorTopBar else ColorBgMain,
                        modifier = Modifier
                            .height(34.dp)
                            .bouncyClickable { selectedFilter = null }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "ВСЕ (${allTasks.size})",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else ColorTextBody
                                )
                            )
                        }
                    }
                }

                items(availableTypes) { type ->
                    val isSelected = selectedFilter == type
                    val count = allTasks.count { it.taskTypeEnum == type }
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                        color = if (isSelected) ColorTopBar else ColorBgMain,
                        modifier = Modifier
                            .height(34.dp)
                            .bouncyClickable { selectedFilter = type }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = "${type.displayName.uppercase()} ($count)",
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(1.dp)
                    .background(ColorDividerLight)
            )

            if (filteredTasks.isEmpty()) {
                // Пустой список заданий
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
                                imageVector = Icons.Default.Checklist,
                                contentDescription = null,
                                tint = ColorBrandBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Нет активных заданий",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ColorTextTitle
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Нажмите «Добавить», чтобы зафиксировать домашнее задание, лабораторную или курсовую работу.",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = ColorTextMuted
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            onToggleCompleted = {
                                coroutineScope.launch {
                                    taskRepository.toggleTaskCompleted(task.id, !task.isCompleted)
                                }
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    taskRepository.deleteTask(task)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            groupInfo = groupInfo,
            availableTypes = availableTypes,
            onDismissRequest = { showAddDialog = false },
            onTaskAdded = { title, description, subject, type, dueDate ->
                coroutineScope.launch {
                    val entity = StudentTaskEntity(
                        groupName = groupInfo.canonicalName,
                        course = groupInfo.course,
                        subjectName = subject,
                        taskType = type.displayName,
                        title = title,
                        description = description,
                        deadlineDate = dueDate,
                        createdAt = System.currentTimeMillis()
                    )
                    taskRepository.addTask(entity)
                    showAddDialog = false
                }
            }
        )
    }
}

/**
 * Карточка задания по Design System МПК.
 */
@Composable
fun TaskItemCard(
    task: StudentTaskEntity,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColor = when (task.taskTypeEnum) {
        TaskType.HOMEWORK -> ColorBadgeHW
        TaskType.LAB_WORK -> ColorBadgeLab
        TaskType.PRACTICAL_WORK -> ColorBadgePract
        TaskType.CONTROL_WORK -> ColorBadgeExam
        TaskType.COURSE_WORK -> ColorBadgeCourse
        TaskType.DIPLOMA -> ColorBadgeDiploma
    }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (task.isCompleted) ColorSurfaceVariantLight else ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onToggleCompleted)
            .testTag("task_item_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Чекбокс отметки о выполнении
            IconButton(
                onClick = onToggleCompleted,
                modifier = Modifier
                    .size(32.dp)
                    .bouncyClickable(onClick = onToggleCompleted)
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (task.isCompleted) "Выполнено" else "Не выполнено",
                    tint = if (task.isCompleted) ColorBrandBlue else ColorTextMuted
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Бейдж типа задания (строго 2px radius)
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                        color = badgeColor.copy(alpha = 0.12f),
                        contentColor = badgeColor
                    ) {
                        Text(
                            text = task.taskTypeEnum.badge,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (task.subjectName.isNotBlank()) {
                        Text(
                            text = task.shortSubjectName,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = ColorBrandBlue
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = task.title,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) ColorTextMuted else ColorTextTitle
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = ColorTextMuted
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (task.deadlineDate.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = ColorTextMuted,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Срок: ${formatDateForShow(task.deadlineDate)}",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 11.sp,
                                color = ColorTextMuted
                            )
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(28.dp)
                    .bouncyClickable(onClick = onDelete)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = ColorTextMuted.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Диалог создания нового задания с выбором предмета из официального справочника MpkCurriculum.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    groupInfo: GroupInfo,
    availableTypes: List<TaskType>,
    onDismissRequest: () -> Unit,
    onTaskAdded: (title: String, description: String, subject: String, type: TaskType, dueDate: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = null)
    var selectedType by remember { mutableStateOf(TaskType.HOMEWORK) }

    val officialSubjects = remember(groupInfo.specialtyCode, groupInfo.course) {
        MpkCurriculum.getSubjects(groupInfo.specialtyCode, groupInfo.course)
    }
    var selectedSubject by remember {
        mutableStateOf(officialSubjects.firstOrNull() ?: "Охрана труда")
    }
    var subjectExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Новое задание",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ColorTextTitle
                    )
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть", tint = ColorTextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Категория:",
                    style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = ColorTextMuted)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availableTypes) { type ->
                        val isSelected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, if (isSelected) ColorBrandBlue else ColorBorderLight),
                            color = if (isSelected) ColorBrandBlue else ColorBgMain,
                            contentColor = if (isSelected) Color.White else ColorTextBody,
                            modifier = Modifier
                                .height(30.dp)
                                .bouncyClickable { selectedType = type }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = type.badge,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Выбор дисциплины из официального списка MpkCurriculum
                ExposedDropdownMenuBox(
                    expanded = subjectExpanded,
                    onExpandedChange = { subjectExpanded = !subjectExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedSubject,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Официальный предмет", fontSize = 12.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = subjectExpanded,
                        onDismissRequest = { subjectExpanded = false }
                    ) {
                        officialSubjects.forEach { subject ->
                            DropdownMenuItem(
                                text = { Text(subject, fontSize = 13.sp) },
                                onClick = {
                                    selectedSubject = subject
                                    subjectExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Что нужно сделать", fontSize = 12.sp) },
                    placeholder = { Text("Например: Упр. 4, Отчет по лабе №2", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Срок сдачи выбирается в календаре, а не пишется текстом:
                // иначе в поле попадало что угодно («на следующей неделе»),
                // и задание нельзя было отсортировать по дате.
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClickable { showDatePicker = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = dueDateMillis?.let { "Срок сдачи: ${formatDateForShow(isoDate(it))}" }
                                ?: "Срок сдачи — выбрать дату",
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = if (dueDateMillis == null) ColorTextMuted else ColorTextTitle
                            )
                        )
                    }
                }

                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                dueDateMillis = datePickerState.selectedDateMillis
                                showDatePicker = false
                            }) { Text("ГОТОВО") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("ОТМЕНА") }
                        }
                    ) {
                        DatePicker(
                            state = datePickerState,
                            title = null,
                            headline = null,
                            showModeToggle = false
                        )
                    }
                }
            }
        },
        confirmButton = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                modifier = Modifier.bouncyClickable {
                    if (title.isNotBlank()) {
                        onTaskAdded(
                            title.trim(),
                            description.trim(),
                            selectedSubject,
                            selectedType,
                            dueDateMillis?.let { isoDate(it) } ?: ""
                        )
                    }
                }
            ) {
                Text(
                    text = "ДОБАВИТЬ",
                    softWrap = false,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        },
        dismissButton = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, ColorBorderLight),
                color = Color.Transparent,
                modifier = Modifier.bouncyClickable { onDismissRequest() }
            ) {
                Text(
                    text = "ОТМЕНА",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ColorTextBody
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    )
}


// ---------------------------------------------------------------------------
// Даты срока сдачи
// ---------------------------------------------------------------------------

/**
 * Миллисекунды из календаря → «ГГГГ-ММ-ДД».
 * Календарь Material 3 отдаёт UTC-полночь, поэтому и читаем его в UTC —
 * иначе в любом отрицательном поясе дата сдвинется на день назад.
 */
internal fun isoDate(millis: Long): String {
    val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = millis
    }
    return String.format(
        java.util.Locale.ROOT, "%04d-%02d-%02d",
        c.get(java.util.Calendar.YEAR),
        c.get(java.util.Calendar.MONTH) + 1,
        c.get(java.util.Calendar.DAY_OF_MONTH)
    )
}

/**
 * Дата сдачи для показа: «ГГГГ-ММ-ДД» → «ДД.ММ.ГГГГ».
 *
 * Старые задания могли содержать в этом поле произвольный текст
 * («на следующей неделе») — такое показываем как есть, чтобы ничего не потерять.
 */
internal fun formatDateForShow(raw: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(raw.trim()) ?: return raw
    return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]}"
}
