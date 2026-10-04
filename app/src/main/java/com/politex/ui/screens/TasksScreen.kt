package com.politex.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TaskReminderEntity
import com.politex.data.model.GroupInfo
import com.politex.data.model.TaskType
import com.politex.data.repository.TaskRepository
import com.politex.ui.screens.materials.BrandActionButton
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
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.ui.components.TutorialController
import com.politex.ui.components.TutorialFeature
import com.politex.ui.components.tutorialAnchor
import com.politex.util.MpkCurriculum
import kotlinx.coroutines.launch

@Composable
fun TasksScreen(
    groupInfo: GroupInfo,
    taskRepository: TaskRepository,
    modifier: Modifier = Modifier,
    // T1: SnackbarHost из MainScreen — undo удаления задания
    snackbarHostState: SnackbarHostState? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf<TaskType?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // H1: редактирование по тапу + напоминания. Напоминания живут в отдельной таблице,
    // DAO берём из той же боевой БД (паттерн как в ScheduleScreen) — репозиторий заданий
    // конструируется в нескольких местах (в т.ч. в запрещённом к правке ScheduleScreen),
    // поэтому не меняем его конструктор, а работаем с reminderDao напрямую.
    val context = LocalContext.current
    val reminderDao = remember { MpkDatabase.getInstance(context).taskReminderDao() }
    var editingTask by remember { mutableStateOf<StudentTaskEntity?>(null) }
    var editingReminders by remember { mutableStateOf<List<Long>>(emptyList()) }
    // V13 (доступность): долгий тап по карточке — диалог подтверждения удаления
    var deleteConfirmTask by remember { mutableStateOf<StudentTaskEntity?>(null) }

    val allTasksFlow =
        remember(groupInfo.canonicalName) {
            taskRepository.getTasksForGroup(groupInfo.canonicalName)
        }
    val allTasks: List<StudentTaskEntity> by allTasksFlow.collectAsState(initial = emptyList())

    val filteredTasks =
        remember(allTasks, selectedFilter) {
            if (selectedFilter == null) {
                allTasks
            } else {
                allTasks.filter { it.taskTypeEnum == selectedFilter }
            }
        }

    val availableTypes =
        remember(groupInfo.course) {
            TaskType.getAvailableForCourse(groupInfo.course)
        }

    /**
     * T1/V13: удаление с undo. Удаляем сразу; снекбар «ОТМЕНИТЬ» возвращает задание ТЕМ ЖЕ id
     * (напоминания в task_reminders остаются привязаны). Окно undo — ровно 7 с (X11b): показываем с
     * Indefinite (в SnackbarDuration нет «7 с»), UndoSnackbar в MainScreen рисует полосу-таймер
     * и по её исчерпании сам гасит хост (onExpire → dismissSnackbar).
     * В снекбар уходит не сама сущность, а её копия — Room мог бы дергать
     * этот же объект при повторном удалении, копия исключает гонки.
     */
    fun deleteWithUndo(task: StudentTaskEntity) {
        coroutineScope.launch {
            taskRepository.deleteTask(task)
            if (snackbarHostState == null) return@launch
            val result =
                snackbarHostState.showSnackbar(
                    message = "Задание удалено",
                    actionLabel = "ОТМЕНИТЬ",
                    duration = SnackbarDuration.Indefinite,
                )
            if (result == SnackbarResult.ActionPerformed) {
                taskRepository.addTask(task.copy())
            }
        }
    }

    // V14: туториал «Удаление свайпом» — по первому входу во вкладку, и ТОЛЬКО если есть
    // задания. При пустом списке цели нет → запрос не делаем, флаг не ставим: туториал
    // откладывается до момента, когда задание появится (переживёт обновление приложения).
    val firstTaskId = filteredTasks.firstOrNull()?.id
    LaunchedEffect(firstTaskId) {
        if (firstTaskId != null) TutorialController.request(context, TutorialFeature.TASK_SWIPE)
    }
    // Уход с вкладки при живом туториале — снимаем БЕЗ флага (покажем при следующем входе).
    DisposableEffect(Unit) {
        onDispose {
            if (TutorialController.active == TutorialFeature.TASK_SWIPE) TutorialController.clear()
        }
    }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // V12: шапка одной строкой — слева фирменная «ДОБАВИТЬ» (BrandActionButton,
            // высота 44 = Tokens.ButtonHeight), справа скроллящийся Row wrap-content чипов
            // (Все + типы по курсу, Tokens.ChipScreen=40) с автоскроллом к выбранному.
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandActionButton(
                    icon = Icons.Default.Add,
                    label = "ДОБАВИТЬ",
                    testTag = "add_task_top_btn",
                    onClick = { showAddDialog = true },
                )

                val chipScroll = rememberScrollState()
                // Позиции чипов в координатах контента (positionInParent не зависит от
                // скролла): start → width. X15: автоскролл ЦЕНТРИРУЕТ выбранный чип.
                val chipOffsets = remember { mutableMapOf<Int, Pair<Int, Int>>() }
                LaunchedEffect(selectedFilter) {
                    val index = if (selectedFilter == null) 0 else availableTypes.indexOf(selectedFilter) + 1
                    if (index == 0) {
                        // «Все» — первый чип, скролл в начало
                        chipScroll.animateScrollTo(0)
                    } else {
                        chipOffsets[index]?.let { (start, width) ->
                            chipScroll.animateScrollTo(
                                // viewportSize в ScrollState 1.7 — Int (ширина вьюпорта, px)
                                centerScrollOffset(start, width, chipScroll.viewportSize, chipScroll.maxValue),
                            )
                        }
                    }
                }
                Row(
                    modifier =
                    Modifier
                        .weight(1f)
                        .horizontalScroll(chipScroll),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TaskFilterChip(
                        label = "Все",
                        isSelected = selectedFilter == null,
                        index = 0,
                        offsets = chipOffsets,
                        onClick = { selectedFilter = null },
                    )
                    availableTypes.forEachIndexed { i, type ->
                        TaskFilterChip(
                            label = type.shortFilterLabel(),
                            isSelected = selectedFilter == type,
                            index = i + 1,
                            offsets = chipOffsets,
                            onClick = { selectedFilter = type },
                        )
                    }
                }
            }

            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(1.dp)
                    .background(ColorDividerLight),
            )

            if (filteredTasks.isEmpty()) {
                // T4: пустое состояние честно различает «нет вообще» и «нет по фильтру»
                val filterActive = selectedFilter != null

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
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = null,
                                tint = ColorBrandBlue,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (filterActive) "По этому фильтру ничего нет" else "Нет активных заданий",
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ColorTextTitle,
                            ),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (filterActive) {
                            // Сброс фильтра прямо из пустого состояния
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = ColorBrandFill,
                                contentColor = Color.White,
                                modifier =
                                Modifier
                                    // V15: высота кнопки — токен 44
                                    .heightIn(min = Tokens.ButtonHeight)
                                    .bouncyClickable { selectedFilter = null }
                                    .testTag("reset_filter_btn"),
                            ) {
                                // C2: у Surface внутри Box(propagateMinConstraints=true)
                                // без contentAlignment — голый Text вставал к верху
                                // 48dp-слота. Box берёт min-высоту как свою и центрирует.
                                // fillMaxWidth НЕ добавляем: кнопка wrap-content.
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "СБРОСИТЬ ФИЛЬТР",
                                        softWrap = false,
                                        maxLines = 1,
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            // V15: CAPS-текст кнопок — токен 12sp
                                            fontSize = Tokens.ButtonText,
                                            color = Color.White,
                                        ),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Нажмите «Добавить», чтобы зафиксировать домашнее задание, лабораторную или курсовую работу.",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    color = ColorTextMuted,
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        // V13: удаление свайпом влево (кнопка ✕ из карточки убрана)
                        // V14: свайп-удаление = успех туториала task_swipe
                        SwipeToDeleteBox(
                            onDelete = {
                                deleteWithUndo(task)
                                TutorialController.success(TutorialFeature.TASK_SWIPE)
                            },
                        ) {
                            TaskItemCard(
                                task = task,
                                // V14: первая карточка — цель туториала «Удаление свайпом»
                                modifier =
                                if (task.id == firstTaskId) {
                                    Modifier.tutorialAnchor("task_card_first")
                                } else {
                                    Modifier
                                },
                                onToggleCompleted = {
                                    coroutineScope.launch {
                                        taskRepository.toggleTaskCompleted(task.id, !task.isCompleted)
                                    }
                                },
                                // H1: тап по карточке — редактирование (не «выполнено»)
                                onEdit = {
                                    coroutineScope.launch {
                                        editingReminders = reminderDao.getForTask(task.id).map { it.triggerAtMillis }
                                        editingTask = task
                                    }
                                },
                                onLongPressDelete = { deleteConfirmTask = task },
                            )
                        }
                    }
                }
            }
        }
    }

    // H1: общий путь сохранения — upsert задания (REPLACE по id) + атомарная замена напоминаний.
    // Один и тот же для «добавить» (id=0) и «сохранить правку» (id=N).
    fun saveTaskAndReminders(
        entity: StudentTaskEntity,
        reminderMillis: List<Long>,
        onDone: () -> Unit,
    ) {
        coroutineScope.launch {
            val id = taskRepository.saveTask(entity)
            reminderDao.replaceForTask(
                id,
                reminderMillis.map { TaskReminderEntity(taskId = id, triggerAtMillis = it) },
            )
            onDone()
        }
    }

    if (showAddDialog) {
        TaskEditDialog(
            groupInfo = groupInfo,
            availableTypes = availableTypes,
            task = null,
            initialReminders = emptyList(),
            onDismissRequest = { showAddDialog = false },
            onTaskSaved = { entity, reminders ->
                saveTaskAndReminders(entity, reminders) { showAddDialog = false }
            },
        )
    }

    editingTask?.let { task ->
        TaskEditDialog(
            groupInfo = groupInfo,
            availableTypes = availableTypes,
            task = task,
            initialReminders = editingReminders,
            onDismissRequest = { editingTask = null },
            onTaskSaved = { entity, reminders ->
                saveTaskAndReminders(entity, reminders) { editingTask = null }
            },
        )
    }

    // V13 (доступность): свайп недоступен без жеста — долгий тап даёт тот же путь
    // удаления через подтверждающий диалог, дальше работает deleteWithUndo.
    deleteConfirmTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTask = null },
            shape = RoundedCornerShape(2.dp),
            containerColor = ColorBgMain,
            title = {
                Text(
                    text = "Удалить задание?",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ColorTextTitle,
                    ),
                )
            },
            confirmButton = {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorDangerFill,
                    modifier =
                    Modifier
                        .testTag("delete_confirm_btn")
                        .bouncyClickable {
                            deleteConfirmTask = null
                            deleteWithUndo(task)
                        },
                ) {
                    Text(
                        text = "УДАЛИТЬ",
                        softWrap = false,
                        maxLines = 1,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = Tokens.ButtonText,
                            color = Color.White,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            },
            dismissButton = {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = Color.Transparent,
                    modifier = Modifier.bouncyClickable { deleteConfirmTask = null },
                ) {
                    Text(
                        text = "ОТМЕНА",
                        softWrap = false,
                        maxLines = 1,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = Tokens.ButtonText,
                            color = ColorTextBody,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TaskItemCard(
    task: StudentTaskEntity,
    onToggleCompleted: () -> Unit,
    onEdit: () -> Unit,
    // V13: долгий тап — подтверждение удаления (доступность вместо свайпа)
    onLongPressDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeColor =
        when (task.taskTypeEnum) {
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
        modifier =
        modifier
            .fillMaxWidth()
            // H1: тап по карточке открывает редактирование; «выполнено» — только на чекбоксе слева.
            // V13: combinedClickable разводит тап и долгий тап (bouncyClickable longClick не умеет).
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEdit,
                onLongClick = onLongPressDelete,
            )
            .testTag("task_item_${task.id}"),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // T2/V15: чекбокс — самое частое действие; хитбокс = Tokens.IconHit (44).
            // ОДИН обработчик на узле (паттерн HomeworkBadge): Box+bouncyClickable, Icon внутри.
            Box(
                modifier =
                Modifier
                    .size(Tokens.IconHit)
                    .bouncyClickable(onClick = onToggleCompleted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (task.isCompleted) "Выполнено" else "Не выполнено",
                    tint = if (task.isCompleted) ColorBrandBlue else ColorTextMuted,
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                // V16: строка 1 — предмет (вес на всю ширину) + справа бейдж типа задания.
                // Цветная точка 8dp и подполоса 2dp из W24 удалены.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.subjectName.isNotBlank()) {
                        Text(
                            text = task.shortSubjectName,
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = ColorBrandBlue,
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = badgeColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, badgeColor),
                    ) {
                        Text(
                            text = task.taskTypeEnum.badge,
                            softWrap = false,
                            maxLines = 1,
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = badgeColor,
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = task.title,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) ColorTextMuted else ColorTextTitle,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = ColorTextMuted,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (task.deadlineDate.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = ColorTextMuted,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Срок: ${formatDateForShow(task.deadlineDate)}",
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontSize = 11.sp,
                                color = ColorTextMuted,
                            ),
                        )
                    }
                }
            }
            // V13: кнопка ✕ Delete удалена — только свайп влево / долгий тап + подтверждение
        }
    }
}

// W23: короткие подписи сегментов фильтра — 7 штук помещаются в ряд без прокрутки
private fun TaskType.shortFilterLabel(): String =
    when (this) {
        TaskType.HOMEWORK -> "ДЗ"
        TaskType.LAB_WORK -> "Лаб"
        TaskType.PRACTICAL_WORK -> "Практ"
        TaskType.CONTROL_WORK -> "КР"
        TaskType.COURSE_WORK -> "Курс"
        TaskType.DIPLOMA -> "Дипл"
    }

/**
 * V12: чип фильтра категорий — wrap-content, высота Tokens.ChipScreen (40),
 * selected = ColorTopBar + белый Bold. Пишет свои (start, width) в [offsets] (координаты
 * скроллящегося родителя) — по ним шапка центрирует выбранный чип (X15).
 */
@Composable
private fun TaskFilterChip(
    label: String,
    isSelected: Boolean,
    index: Int,
    offsets: MutableMap<Int, Pair<Int, Int>>,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
        color = if (isSelected) ColorTopBar else ColorBgMain,
        modifier =
        Modifier
            .height(Tokens.ChipScreen)
            .onGloballyPositioned { offsets[index] = it.positionInParent().x.toInt() to it.size.width }
            .bouncyClickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = Tokens.ChipText,
                    color = if (isSelected) Color.White else ColorTextBody,
                ),
            )
        }
    }
}

/**
 * X15: скролл, при котором центр чипа совпадает с центром вьюпорта; клампится в [0, maxScroll]
 * (первый чип → 0, последний → maxScroll). Чистая функция для юнит-тестов, все значения в px.
 */
internal fun centerScrollOffset(
    chipStart: Int,
    chipWidth: Int,
    viewport: Int,
    maxScroll: Int,
): Int = (chipStart + chipWidth / 2 - viewport / 2).coerceIn(0, maxScroll)

// ─────────────────────────────────────────────────────────────────────────────
// V13: swipe-to-delete — контейнер карточки + чистая функция порогов (юнит-тест)
// ─────────────────────────────────────────────────────────────────────────────

/** Флинг: кадровое смещение больше этого (в сторону удаления) считается броском. X11a: 24→12. */
private val SwipeFlingThreshold = 12.dp

/** Возврат карточки после свайпа ниже порога — пружина по спеке V13. */
private val SwipeReturnSpring: FiniteAnimationSpec<Float> =
    spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)

/**
 * V13/X11a: удалять ли при отпускании. Чистая функция для юнит-тестов.
 * Условия: утянули влево на 35% ширины или дальше (|offsetX| ≥ 0.35·width)
 * ИЛИ флинг влево (кадровый Δx ≤ -порог). offsetX ∈ [-width, 0];
 * флинг вправо — это жест возврата, удалять он не должен.
 */
internal fun shouldDelete(
    offsetX: Float,
    widthPx: Float,
    lastFrameDeltaPx: Float,
    flingThresholdPx: Float,
): Boolean =
    widthPx > 0f &&
        (-offsetX >= widthPx * 0.35f || lastFrameDeltaPx <= -flingThresholdPx)

/**
 * V13: свайп-контейнер задания. Тянем строго влево (x ∈ [-width, 0]); под карточкой —
 * красный слой ColorDangerFill с иконкой удаления. При превышении порога ([shouldDelete])
 * две фазы: выезд до -width (150 мс), затем схлопывание высоты measured→0 и alpha→0
 * (200 мс), после чего вызывается [onDelete] (deleteWithUndo). Ниже порога — возврат
 * пружиной [SwipeReturnSpring].
 */
@Composable
private fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }
    // фаза 2: доля высоты/непрозрачности, 1 → 0 за 200 мс
    val collapse = remember { Animatable(1f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableIntStateOf(0) }
    var startHeight by remember { mutableStateOf(Dp.Unspecified) }
    var deleting by remember { mutableStateOf(false) }
    var lastDragDelta by remember { mutableFloatStateOf(0f) }

    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .then(if (deleting) Modifier.height(startHeight * collapse.value) else Modifier)
            .graphicsLayer { alpha = collapse.value }
            .onSizeChanged {
                if (!deleting) {
                    widthPx = it.width.toFloat()
                    heightPx = it.height
                }
            }
            .pointerInput(deleting) {
                if (deleting) return@pointerInput
                val flingThresholdPx = with(density) { SwipeFlingThreshold.toPx() }
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (shouldDelete(offsetX.value, widthPx, lastDragDelta, flingThresholdPx)) {
                            deleting = true
                            startHeight = with(density) { heightPx.toDp() }
                            scope.launch {
                                offsetX.animateTo(-widthPx, tween(durationMillis = 150))
                                collapse.animateTo(0f, tween(durationMillis = 200))
                                onDelete()
                            }
                        } else {
                            scope.launch { offsetX.animateTo(0f, SwipeReturnSpring) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, SwipeReturnSpring) }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    lastDragDelta = dragAmount
                    scope.launch { offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-widthPx, 0f)) }
                }
            },
    ) {
        Box(
            modifier =
            Modifier
                .matchParentSize()
                .background(ColorDangerFill),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = Color.White,
                modifier =
                Modifier
                    .padding(end = 20.dp)
                    .size(20.dp),
            )
        }
        Box(modifier = Modifier.graphicsLayer { translationX = offsetX.value }) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditDialog(
    groupInfo: GroupInfo,
    availableTypes: List<TaskType>,
    // H1: null = новое задание; не-null = редактирование (поля предзаполняются)
    task: StudentTaskEntity? = null,
    initialReminders: List<Long> = emptyList(),
    onDismissRequest: () -> Unit,
    onTaskSaved: (entity: StudentTaskEntity, reminderMillis: List<Long>) -> Unit,
) {
    val isEdit = task != null
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    // W32: «Срок сдачи» (выбор даты) удалён из диалога; deadlineDate в БД остаётся —
    // новое задание пишется с пустым сроком, при правке прежний срок не затирается.
    val currentYear =
        remember {
            java.util.Calendar
                .getInstance()
                .get(java.util.Calendar.YEAR)
        }
    var selectedType by remember { mutableStateOf(task?.taskTypeEnum ?: TaskType.HOMEWORK) }

    val officialSubjects =
        remember(groupInfo.specialtyCode, groupInfo.course) {
            MpkCurriculum.getSubjects(groupInfo.specialtyCode, groupInfo.course)
        }
    val defaultSubject = officialSubjects.firstOrNull() ?: "Охрана труда"
    // H1: предмет из официального списка → dropdown; иначе → «своё название»
    val subjectIsOfficial = task != null && officialSubjects.contains(task.subjectName)
    var selectedSubject by remember {
        mutableStateOf(if (subjectIsOfficial) (task?.subjectName ?: defaultSubject) else defaultSubject)
    }
    var subjectExpanded by remember { mutableStateOf(false) }
    var isCustomSubject by remember { mutableStateOf(task != null && !subjectIsOfficial) }
    var customSubject by remember {
        mutableStateOf(if (task != null && !subjectIsOfficial) task.subjectName else "")
    }
    val effectiveSubject = if (isCustomSubject) customSubject.trim() else selectedSubject

    // H1: напоминания (epoch millis, локальное время). Чипы-список + компактный композер.
    var reminders by remember { mutableStateOf(initialReminders) }
    var showReminderComposer by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        // D8: тап вне диалога не закрывает — набранный текст не теряется
        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isEdit) "Редактировать задание" else "Новое задание",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ColorTextTitle,
                    ),
                )
                IconButton(
                    onClick = onDismissRequest,
                    // D4/V15: хитбокс крестика — Tokens.IconHit (44)
                    modifier = Modifier.size(Tokens.IconHit),
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть", tint = ColorTextMuted)
                }
            }
        },
        text = {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    // H1: форма стала выше (блок напоминаний) — прокрутка, чтобы не резалось
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    // W24: полный тип задания — здесь (в карточке осталась только цветная точка)
                    text = "Категория: ${selectedType.displayName}",
                    style =
                    androidx.compose.ui.text
                        .TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(availableTypes) { type ->
                        val isSelected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, if (isSelected) ColorBrandBlue else ColorBorderLight),
                            color = if (isSelected) ColorBrandBlue else ColorBgMain,
                            contentColor = if (isSelected) Color.White else ColorTextBody,
                            modifier =
                            Modifier
                                .height(Tokens.ChipForm)
                                .bouncyClickable { selectedType = type },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                Text(
                                    text = type.badge,
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                    ),
                                )
                            }
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
                    modifier = Modifier.fillMaxWidth(),
                )

                // H1: блок «Напоминания». W33: перенесён ВЫШЕ блока предмета —
                // порядок полей: Категория → Что нужно сделать → Напоминания → Предмет.
                Text(
                    text = "Напоминания:",
                    style =
                    androidx.compose.ui.text
                        .TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // быстрые чипы: W32 — больше не привязаны к «Сроку сдачи», считаем от сегодня
                    item {
                        ReminderQuickChip(text = "+1 день") {
                            reminderMillisFrom(todayIso(), 18, 0, 1)?.let { m -> reminders = reminders + m }
                        }
                    }
                    item {
                        ReminderQuickChip(text = "+2 дня") {
                            reminderMillisFrom(todayIso(), 18, 0, 2)?.let { m -> reminders = reminders + m }
                        }
                    }
                    item {
                        ReminderQuickChip(text = "+ напоминание", tag = "add_reminder_chip") { showReminderComposer = !showReminderComposer }
                    }
                }

                // добавленные напоминания чипами «12.10 18:00 ×» (удаление по крестику)
                reminders.forEachIndexed { index, millis ->
                    ReminderChip(
                        label = formatReminderChip(millis),
                        onRemove = { reminders = reminders.filterIndexed { i, _ -> i != index } },
                    )
                }

                if (showReminderComposer) {
                    ReminderComposer(
                        initialDateIso = todayIso(),
                        currentYear = currentYear,
                        onDismiss = { showReminderComposer = false },
                        onAdd = { millis ->
                            reminders = reminders + millis
                            showReminderComposer = false
                        },
                    )
                }

                // W33: предмет — НИЖЕ блока напоминаний (по замечанию заказчика)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (!isCustomSubject) ColorBrandBlue else ColorBorderLight),
                        color = if (!isCustomSubject) ColorBrandBlue else ColorBgMain,
                        contentColor = if (!isCustomSubject) Color.White else ColorTextBody,
                        modifier =
                        Modifier
                            .height(Tokens.ChipForm)
                            .bouncyClickable { isCustomSubject = false },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(
                                text = "Из списка",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                ),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (isCustomSubject) ColorBrandBlue else ColorBorderLight),
                        color = if (isCustomSubject) ColorBrandBlue else ColorBgMain,
                        contentColor = if (isCustomSubject) Color.White else ColorTextBody,
                        modifier =
                        Modifier
                            .height(Tokens.ChipForm)
                            .bouncyClickable { isCustomSubject = true },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(
                                text = "Другое…",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                ),
                            )
                        }
                    }
                }

                if (isCustomSubject) {
                    OutlinedTextField(
                        value = customSubject,
                        onValueChange = { customSubject = it },
                        label = { Text("Своё название", fontSize = 12.sp) },
                        placeholder = { Text("Например: Репетитор, Секции", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(2.dp),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("custom_subject_field"),
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = subjectExpanded,
                        onExpandedChange = { subjectExpanded = !subjectExpanded },
                    ) {
                        OutlinedTextField(
                            value = selectedSubject,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Официальный предмет", fontSize = 12.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                            shape = RoundedCornerShape(2.dp),
                            modifier =
                            Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = subjectExpanded,
                            onDismissRequest = { subjectExpanded = false },
                        ) {
                            officialSubjects.forEach { subject ->
                                DropdownMenuItem(
                                    text = { Text(subject, fontSize = 13.sp) },
                                    onClick = {
                                        selectedSubject = subject
                                        subjectExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Находка 1: пустое название/предмет → кнопка физически disabled и затемнена,
            // вместо «тап молча ничего не делает»
            val canSave = title.isNotBlank() && effectiveSubject.isNotBlank()
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                modifier =
                Modifier
                    .testTag("task_edit_confirm")
                    .alpha(if (canSave) 1f else 0.4f)
                    .bouncyClickable(enabled = canSave) {
                    // W32: срок сдачи в диалоге больше не выбирается — новое задание
                    // пишется с пустым deadlineDate, правка не затирает прежний срок.
                    val dueIso = task?.deadlineDate ?: ""
                    // H1: новое → собираем сущность; правка → copy (id/group/completed/createdAt сохраняются)
                    val entity =
                        if (task == null) {
                            StudentTaskEntity(
                                groupName = groupInfo.canonicalName,
                                course = groupInfo.course,
                                subjectName = effectiveSubject,
                                taskType = selectedType.displayName,
                                title = title.trim(),
                                description = description.trim(),
                                deadlineDate = dueIso,
                                createdAt = System.currentTimeMillis(),
                            )
                        } else {
                            task.copy(
                                subjectName = effectiveSubject,
                                taskType = selectedType.displayName,
                                title = title.trim(),
                                description = description.trim(),
                                deadlineDate = dueIso,
                            )
                        }
                    onTaskSaved(entity, reminders)
                },
            ) {
                Text(
                    text = if (isEdit) "СОХРАНИТЬ" else "ДОБАВИТЬ",
                    softWrap = false,
                    maxLines = 1,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        },
        dismissButton = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, ColorBorderLight),
                color = Color.Transparent,
                modifier = Modifier.bouncyClickable { onDismissRequest() },
            ) {
                Text(
                    text = "ОТМЕНА",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ColorTextBody,
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        },
    )
}

internal fun formatDateForShow(raw: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(raw.trim()) ?: return raw
    return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]}"
}

// ─────────────────────────────────────────────────────────────────────────────
// H1: напоминания — хелперы времени и компактный выбор даты/времени
// ─────────────────────────────────────────────────────────────────────────────

/**
 * H1: epoch millis из ISO-даты (yyyy-MM-dd) + время, в ЛОКАЛЬНОМ часовом поясе устройства
 * (напоминание должно срабатывать по местному настенному времени, в отличие от deadlineDate,
 * который хранится как дата без TZ). plusDays — для быстрых чипов «+1 день», «+2 дня».
 */
internal fun reminderMillisFrom(
    iso: String,
    hour: Int,
    minute: Int,
    plusDays: Int,
): Long? {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(iso.trim()) ?: return null
    val cal =
        java.util.Calendar.getInstance().apply {
            clear()
            set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt(), hour, minute, 0)
            if (plusDays != 0) add(java.util.Calendar.DAY_OF_YEAR, plusDays)
        }
    return cal.timeInMillis
}

/** H1: подпись чипа напоминания «12.10 18:00» (локальный TZ). */
internal fun formatReminderChip(millis: Long): String {
    val c =
        java.util.Calendar
            .getInstance()
            .apply { timeInMillis = millis }
    return String.format(
        java.util.Locale.ROOT,
        "%02d.%02d %02d:%02d",
        c.get(java.util.Calendar.DAY_OF_MONTH),
        c.get(java.util.Calendar.MONTH) + 1,
        c.get(java.util.Calendar.HOUR_OF_DAY),
        c.get(java.util.Calendar.MINUTE),
    )
}

/** H1: сегодняшняя дата в ISO (локальный TZ) — старт композера, когда срок сдачи не задан. */
internal fun todayIso(): String {
    val c = java.util.Calendar.getInstance()
    return String.format(
        java.util.Locale.ROOT,
        "%04d-%02d-%02d",
        c.get(java.util.Calendar.YEAR),
        c.get(java.util.Calendar.MONTH) + 1,
        c.get(java.util.Calendar.DAY_OF_MONTH),
    )
}

@Composable
private fun ReminderQuickChip(
    text: String,
    tag: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBorderLight),
        color = ColorBgMain,
        contentColor = ColorTextBody,
        modifier =
        Modifier
            .height(Tokens.ChipForm)
            .bouncyClickable(onClick = onClick)
            .let { if (tag != null) it.testTag(tag) else it },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = text,
                softWrap = false,
                maxLines = 1,
                style =
                androidx.compose.ui.text
                    .TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun ReminderChip(
    label: String,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.5f)),
        color = ColorBrandBlue.copy(alpha = 0.12f),
        contentColor = ColorBrandBlue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp),
        ) {
            Text(
                text = label,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = ColorBrandBlue,
                ),
                modifier = Modifier.weight(1f),
            )
            // V15/находка 7: зона крестика — Tokens.IconHit (44), визуальный размер иконки прежний
            IconButton(onClick = onRemove, modifier = Modifier.size(Tokens.IconHit)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Удалить напоминание",
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun TimeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorBrandBlue else ColorBorderLight),
        color = if (selected) ColorBrandBlue else ColorBgMain,
        contentColor = if (selected) Color.White else ColorTextBody,
        modifier =
        Modifier
            .height(Tokens.ChipForm)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label,
                softWrap = false,
                maxLines = 1,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}

/**
 * H1: компактный выбор напоминания — дата (SimpleDatePickerDialog) + время
 * (быстрые чипы 08:00/12:00/18:00/21:00 или «своё» ЧЧ:ММ). Покрывает сценарии заказчика:
 * несколько дат с одним временем, одна дата с разными временами, «следующий день — другое время».
 */
@Composable
private fun ReminderComposer(
    initialDateIso: String,
    currentYear: Int,
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit,
) {
    var dateIso by remember { mutableStateOf(initialDateIso) }
    var showDatePicker by remember { mutableStateOf(false) }
    var customTime by remember { mutableStateOf(false) }
    var presetHour by remember { mutableIntStateOf(18) }
    var presetMinute by remember { mutableIntStateOf(0) }
    var hourInput by remember { mutableStateOf("18") }
    var minuteInput by remember { mutableStateOf("00") }
    val presets = remember { listOf(8 to 0, 12 to 0, 18 to 0, 21 to 0) }

    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
        color = ColorSurfaceHighlight,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBgMain,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .bouncyClickable { showDatePicker = true },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = ColorBrandBlue,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Дата: ${formatDateForShow(dateIso)}",
                        style =
                        androidx.compose.ui.text
                            .TextStyle(fontSize = 13.sp, color = ColorTextTitle),
                    )
                }
            }

            Text(
                text = "Время:",
                style =
                androidx.compose.ui.text
                    .TextStyle(fontSize = 12.sp, color = ColorTextMuted),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(presets) { preset ->
                    val (h, m) = preset
                    val selected = !customTime && presetHour == h && presetMinute == m
                    TimeChip(
                        label = String.format(java.util.Locale.ROOT, "%02d:%02d", h, m),
                        selected = selected,
                        onClick = {
                            customTime = false
                            presetHour = h
                            presetMinute = m
                        },
                    )
                }
                item {
                    TimeChip(label = "Своё", selected = customTime, onClick = { customTime = true })
                }
            }

            if (customTime) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = hourInput,
                        onValueChange = { hourInput = it.filter(Char::isDigit).take(2) },
                        label = { Text("ЧЧ", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.width(72.dp),
                    )
                    Text(
                        text = ":",
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ColorTextTitle,
                        ),
                    )
                    OutlinedTextField(
                        value = minuteInput,
                        onValueChange = { minuteInput = it.filter(Char::isDigit).take(2) },
                        label = { Text("ММ", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.width(72.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = Color.Transparent,
                    modifier = Modifier.bouncyClickable(onClick = onDismiss),
                ) {
                    Text(
                        text = "ОТМЕНА",
                        softWrap = false,
                        maxLines = 1,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ColorTextBody,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Находка 1: та же enabled+alpha-логика — невалидное «своё» время → кнопка disabled
                val h = if (customTime) hourInput.toIntOrNull() ?: -1 else presetHour
                val m = if (customTime) minuteInput.toIntOrNull() ?: -1 else presetMinute
                val canAdd = h in 0..23 && m in 0..59
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    modifier =
                    Modifier
                        .testTag("reminder_composer_confirm")
                        .alpha(if (canAdd) 1f else 0.4f)
                        .bouncyClickable(enabled = canAdd) {
                            reminderMillisFrom(dateIso, h, m, 0)?.let(onAdd)
                        },
                ) {
                    Text(
                        text = "ДОБАВИТЬ",
                        softWrap = false,
                        maxLines = 1,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        com.politex.ui.components.SimpleDatePickerDialog(
            initialDateIso = dateIso,
            minYear = currentYear,
            maxYear = currentYear + 4,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                dateIso = it
                showDatePicker = false
            },
        )
    }
}
