package com.politex.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.politex.data.local.MpkDatabase
import com.politex.data.local.dao.GradeDao
import com.politex.data.local.entity.GradeConfigEntity
import com.politex.data.local.entity.GradeEntryEntity
import com.politex.data.model.GroupInfo
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorError
import com.politex.ui.theme.ColorSuccess
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberToday
import com.politex.util.GradeAggregator
import com.politex.util.GradeCategory
import com.politex.util.MpkCurriculum
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ф4.2 «Оценки» (агент G, MASTER-PLAN фаза 4.2) + волна W17–W22.
 *
 * W17: название предмета — maxLines=2 + Ellipsis (список, шапка, экран веса).
 * W18: пустые состояния — три точки по центру, а не текст («—»/«Пока нет оценок»).
 * W19: «+ работу» убран полностью — быстрый ввод без подписи и даты (дата = сегодня).
 *      Строка оценки = значение-бейдж + ✕ удаления (44dp).
 * W20: нампад 2×5 (1–5 / 6–9 и 10), кнопки weight(1f) ~56dp — без скролла.
 * W21: категории — фиксированный Row из 5 сегментов, выбранная залита ColorTopBar.
 * W22: «Вес категорий» = ДЕРЕВО агрегации: мини-дерево на экране предмета,
 *      полное дерево с drag&drop листьев (меняется порядок в плоском priorityJson,
 *      схема БД не меняется, формула GradeAggregator не меняется).
 *
 * G1: три «страницы» без nav-графа (rememberSaveable-строки).
 * Данные — напрямую из GradeDao (паттерн MaterialBaseScreen), без ViewModel.
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
        contentPadding = PaddingValues(12.dp),
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
                        maxLines = 2, // W17: длинные названия («физическая культура и…»)
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, color = ColorTextTitle),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (avg == null) {
                        // W18: нет оценок — три точки, а не «—»
                        EmptyDots(modifier = Modifier.testTag("grades_avg_$subject"))
                    } else {
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
    val priority = remember(configs, subject) { priorityFor(configs, subject) }
    val avg = remember(byCategory, priority) { GradeAggregator.average(byCategory, priority) }

    var activeCategory by rememberSaveable(subject) { mutableStateOf(GradeCategory.REGULAR.name) }

    // W19: быстрый ввод — без «+ работу»: label пустой, дата = сегодня автоматически
    fun submit(value: Int) {
        val entry =
            GradeEntryEntity(
                subjectName = subject,
                category = activeCategory,
                value = value,
                label = "",
                dateIso = isoOf(today),
            )
        scope.launch {
            if (activeCategory == GradeCategory.EXAM.name) dao.upsertExam(entry) else dao.insert(entry)
        }
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .testTag("grades_subject_screen"),
    ) {
        // Шапка: назад + предмет (W17: две строки с многоточием)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("grades_back")) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Назад", tint = ColorTextTitle)
            }
            Text(
                text = subject,
                maxLines = 2, // W17
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
                modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
        }

        // G3/W18: текущий средний балл; нет оценок — три точки, а не «—»
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (avg == null) {
                EmptyDots(modifier = Modifier.testTag("grades_current_avg"))
            } else {
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
        }

        // W22: мини-дерево формулы — листья-категории снизу, итог сверху,
        // линии = сложение пар (аккумулятор + категория) ÷ 2 по priority
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AggregationTree(
                priority = priority,
                byCategory = byCategory,
                spec = MiniTreeSpec,
                compact = true,
                skipEmpty = true,
                draggable = false,
            )
        }

        // W22: вход в полное дерево с drag&drop
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = ColorTopBar,
            border = BorderStroke(1.dp, ColorTopBar),
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .height(40.dp)
                .testTag("grades_priority_btn")
                .bouncyClickable(onClick = onOpenPriority),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "НАСТРОИТЬ ВЕС",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    )
                }
            }
        }

        // Секции категорий: строки оценок = значение-бейдж + ✕ (W19)
        LazyColumn(
            modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("grades_entries_list"),
            contentPadding = PaddingValues(horizontal = 12.dp),
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
                        // W18: три точки по центру вместо «Пока нет оценок»
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyDots()
                        }
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

        // ── Панель ввода (G4, W20/W21) ──
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
                // W21: фиксированный Row из 5 сегментов — влезает без скролла,
                // компактные подписи, выбранная залита ColorTopBar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GradeCategory.values().forEach { category ->
                        CategorySegment(
                            category = category,
                            selected = activeCategory == category.name,
                            onClick = { activeCategory = category.name },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                        // W20: нампад 2×5 (1–5 / 6–9 и 10), кнопки ~56dp с weight(1f) —
                        // цифры 1..10 влезают на любом экране без скролла
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                (1..5).forEach { NumberKey(value = it, onSubmit = ::submit) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                (6..9).forEach { NumberKey(value = it, onSubmit = ::submit) }
                                NumberKey(value = 10, onSubmit = ::submit)
                            }
                        }
                }
            }
        }
    }
}

/** W19: строка оценки — только значение-бейдж + ✕ удаления (44dp). LAB — «Зач/Нез». */
@Composable
private fun GradeEntryRow(
    entry: GradeEntryEntity,
    onDelete: () -> Unit,
) {
    val isLab = entry.category == GradeCategory.LAB.name
    val valueText =
        if (isLab) {
            if (entry.value == 1) "Зач" else "Нез"
        } else {
            entry.value.toString()
        }
    val valueColor =
        if (isLab) {
            if (entry.value == 1) ColorSuccess else ColorError
        } else {
            gradeColor(entry.value.toDouble())
        }
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
            // W19: бейдж значения — без бейджа категории и даты
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBgMain,
                border = BorderStroke(1.dp, ColorBorderLight),
            ) {
                Text(
                    text = valueText,
                    maxLines = 1,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
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

/** W20: прямоугольник ~56dp с weight(1f) — нампад 2×5 под большой палец. */
@Composable
private fun RowScope.NumberKey(
    value: Int,
    onSubmit: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier =
        Modifier
            .weight(1f)
            .height(56.dp)
            .testTag("grades_num_$value")
            .bouncyClickable { onSubmit(value) },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = value.toString(),
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
            )
        }
    }
}

@Composable
private fun RowScope.LabButton(
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

/** W21: сегмент категории — компактная подпись, weight(1f), выбранная залита ColorTopBar. */
@Composable
private fun RowScope.CategorySegment(
    category: GradeCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (selected) ColorTopBar else ColorBgMain,
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        modifier =
        Modifier
            .weight(1f)
            .height(32.dp)
            .testTag("grades_chip_${category.name}")
            .bouncyClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = CATEGORY_SHORT[category.name] ?: category.title,
                maxLines = 1,
                style =
                TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else ColorTextBody,
                ),
            )
        }
    }
}

// ── Дерево агрегации (W22) ───────────────────────────────────────────────────

/** W22: параметры раскладки дерева (мини на экране предмета / полное с drag). */
private data class TreeSpec(
    val cardW: Dp,
    val cardH: Dp,
    val gap: Dp,
    val vGap: Dp,
)

private val MiniTreeSpec = TreeSpec(cardW = 64.dp, cardH = 34.dp, gap = 8.dp, vGap = 14.dp)
private val FullTreeSpec = TreeSpec(cardW = 96.dp, cardH = 46.dp, gap = 12.dp, vGap = 32.dp)

/** Раскладка уровней дерева в координаты (dp): уровень 0 — листья снизу. */
private data class TreeLayout(
    val levels: List<List<String>>,
    val centers: List<List<Float>>, // центры узлов по уровням, dp
    val widthDp: Float,
    val heightDp: Float,
)

private fun treeLayout(
    levels: List<List<String>>,
    spec: TreeSpec,
): TreeLayout {
    val step = spec.cardW.value + spec.gap.value
    val centers = ArrayList<List<Float>>(levels.size)
    // листья: узел i стоит на слоте i
    var prev: List<Float> = List(levels[0].size) { it * step + spec.cardW.value / 2f }
    centers += prev
    for (k in 1 until levels.size) {
        val cur = ArrayList<Float>(levels[k].size)
        cur += (prev[0] + prev[1]) / 2f // родитель = середина пары детей
        for (j in 1 until levels[k].size) cur += prev[j + 1] // остальные узлы проходят насквозь
        centers += cur
        prev = cur
    }
    val width = levels[0].size * step - spec.gap.value
    val height = levels.size * spec.cardH.value + (levels.size - 1) * spec.vGap.value
    return TreeLayout(levels, centers, width, height)
}

/**
 * W22: дерево агрегации — лестница пар снизу вверх (листья → итог), линии = «(левый + правый) ÷ 2».
 * [draggable] = экран веса: долгий тап по листу + перетаскивание меняет порядок в
 * плоском priority-списке ( [onReorder] ); схема БД и формула GradeAggregator не меняются.
 * [skipEmpty] = мини-дерево рисует только непустые категории (честная текущая формула),
 * полное дерево показывает все — пустые перетаскиваются «на будущее».
 */
@Composable
private fun AggregationTree(
    priority: List<String>,
    byCategory: Map<String, List<Int>>,
    spec: TreeSpec,
    compact: Boolean,
    skipEmpty: Boolean,
    draggable: Boolean,
    onReorder: (List<String>) -> Unit = {},
) {
    val levels =
        remember(priority, byCategory, skipEmpty) {
            if (skipEmpty) {
                GradeAggregator.buildTreeLevels(priority, byCategory.filterValues { it.isNotEmpty() }.keys)
            } else {
                GradeAggregator.buildTreeLevels(priority)
            }
        }
    if (levels.isEmpty()) return
    val layout = remember(levels, spec) { treeLayout(levels, spec) }
    val density = LocalDensity.current
    val stepPx = with(density) { (spec.cardW + spec.gap).toPx() }
    val leaves = levels[0]

    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var targetSlot by remember { mutableStateOf(-1) }

    Box(modifier = Modifier.size(layout.widthDp.dp, layout.heightDp.dp)) {
        // W22: линии сложения — узел 0 уровня k сходится с узлами 0/1 уровня k-1
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cardHpx = spec.cardH.toPx()
            val vGapPx = spec.vGap.toPx()
            val stroke = 1.5.dp.toPx()
            for (k in 1 until levels.size) {
                val parentBottom = size.height - k * (cardHpx + vGapPx)
                val childTop = size.height - cardHpx - (k - 1) * (cardHpx + vGapPx)
                val px = layout.centers[k][0].dp.toPx()
                for (c in 0..1) {
                    drawLine(
                        color = ColorBorderLight,
                        start = Offset(px, parentBottom),
                        end = Offset(layout.centers[k - 1][c].dp.toPx(), childTop),
                        strokeWidth = stroke,
                    )
                }
            }
        }

        levels.forEachIndexed { k, rowNodes ->
            rowNodes.forEachIndexed { j, node ->
                val x = (layout.centers[k][j] - spec.cardW.value / 2f).dp
                val y = (layout.heightDp - spec.cardH.value - k * (spec.cardH.value + spec.vGap.value)).dp
                val isDragged = draggable && node == dragging
                val highlighted =
                    draggable && k == 0 && targetSlot >= 0 && j == targetSlot && node != dragging
                val dragModifier =
                    if (draggable && k == 0) {
                        // W22: честный drag&drop — долгий тап, карточка следует за пальцем,
                        // дроп в слот другого листа = перемещение в priority-списке
                        Modifier.pointerInput(node, priority) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = node
                                    dragOffset = Offset.Zero
                                    targetSlot = j
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    targetSlot =
                                        (j + (dragOffset.x / stepPx)).roundToInt()
                                            .coerceIn(0, leaves.lastIndex)
                                },
                                onDragEnd = {
                                    if (targetSlot != j) {
                                        onReorder(moveCategory(priority, node, targetSlot, leaves))
                                    }
                                    dragging = null
                                    dragOffset = Offset.Zero
                                    targetSlot = -1
                                },
                                onDragCancel = {
                                    dragging = null
                                    dragOffset = Offset.Zero
                                    targetSlot = -1
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                Box(
                    modifier =
                    Modifier
                        .offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                        .size(spec.cardW, spec.cardH)
                        .zIndex(if (isDragged) 10f else 0f)
                        .graphicsLayer {
                            if (isDragged) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                            }
                        }
                        .then(dragModifier)
                        .let {
                            if (draggable && k == 0) {
                                it.testTag("grades_priority_row_$node")
                            } else {
                                it
                            }
                        },
                ) {
                    TreeCard(
                        node = node,
                        isTop = k == levels.lastIndex,
                        byCategory = byCategory,
                        compact = compact,
                        highlighted = highlighted,
                        showHandle = draggable && k == 0,
                    )
                }
            }
        }
    }
}

/** W22: узел дерева: лист (имя + средний), внутренний (значение + ÷2), итог (заливка ColorTopBar). */
@Composable
private fun TreeCard(
    node: String,
    isTop: Boolean,
    byCategory: Map<String, List<Int>>,
    compact: Boolean,
    highlighted: Boolean,
    showHandle: Boolean,
) {
    val leaves = node.split("+")
    val isLeaf = leaves.size == 1
    val value = remember(node, byCategory) { GradeAggregator.average(byCategory, leaves) }
    val leafAvg = if (isLeaf) byCategory[leaves[0]]?.average() else null
    Surface(
        shape = RoundedCornerShape(2.dp),
        color =
        when {
            highlighted -> ColorSurfaceHighlight
            isTop -> ColorTopBar
            else -> ColorBgMain
        },
        border =
        BorderStroke(
            if (highlighted) 2.dp else 1.dp,
            when {
                highlighted -> ColorBrandFill
                isTop -> ColorTopBar
                else -> ColorBorderLight
            },
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showHandle) {
                // W22: хэндл перетаскивания — теперь он работает (drag по всей карточке)
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Перетащить",
                    tint = ColorTextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when {
                    isTop -> {
                        Text(
                            text = "ИТОГ",
                            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White),
                        )
                        if (value == null) {
                            EmptyDots()
                        } else {
                            Text(
                                text = GradeAggregator.format(value),
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White),
                            )
                        }
                    }
                    isLeaf -> {
                        Text(
                            text = treeTitle(leaves[0], compact),
                            maxLines = 1,
                            style = TextStyle(fontSize = 10.sp, color = ColorTextMuted),
                        )
                        if (leafAvg == null) {
                            EmptyDots()
                        } else {
                            Text(
                                text = GradeAggregator.format(leafAvg),
                                maxLines = 1,
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = gradeColor(leafAvg)),
                            )
                        }
                    }
                    else -> {
                        if (value == null) {
                            EmptyDots()
                        } else {
                            Text(
                                text = GradeAggregator.format(value),
                                maxLines = 1,
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = gradeColor(value)),
                            )
                        }
                        Text("÷2", style = TextStyle(fontSize = 9.sp, color = ColorTextMuted))
                    }
                }
            }
        }
    }
}

/** W18: три наглядные точки — пустое состояние (нет оценок / нет среднего). */
@Composable
private fun EmptyDots(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) {
            Box(
                modifier =
                Modifier
                    .size(4.dp)
                    .background(ColorTextMuted, CircleShape),
            )
        }
    }
}

// ── Экран «Как считается балл» (W22, бывший «Вес категорий») ─────────────────

@Composable
private fun GradePriorityScreen(
    subject: String,
    dao: GradeDao,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val entries by dao.entriesForSubject(subject).collectAsState(initial = emptyList())
    val byCategory = remember(entries) { entries.groupBy({ it.category }) { it.value } }
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
                text = "Как считается балл",
                maxLines = 1,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
                modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
        }
        Text(
            text = subject,
            maxLines = 2, // W17
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = "Каждая линия: (левый + правый) ÷ 2. Держите категорию и перетащите — порядок сложения изменится.",
            style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        val list = order
        if (list != null) {
            // W22: полное дерево, горизонтальный скролл если не влезает, drag листьев
            Box(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AggregationTree(
                        priority = list,
                        byCategory = byCategory,
                        spec = FullTreeSpec,
                        compact = false,
                        skipEmpty = false,
                        draggable = true,
                        onReorder = { order = it },
                    )
                }
            }

            // Сохранение в grade_config (плоский priority-список, схема БД не меняется)
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

/** W21: компактные подписи для сегментов ввода и мини-дерева. */
private val CATEGORY_SHORT =
    mapOf(
        GradeCategory.REGULAR.name to "Обычн",
        GradeCategory.PRACTICAL.name to "Практ",
        GradeCategory.CONTROL.name to "Контр",
        GradeCategory.LAB.name to "Лаб",
        GradeCategory.EXAM.name to "Экз",
    )

private fun treeTitle(
    categoryName: String,
    compact: Boolean,
): String = if (compact) CATEGORY_SHORT[categoryName] ?: categoryName else titleOf(categoryName)

/**
 * W22: перемещение категории в priority-списке по слоту среди листьев дерева.
 * Дроп правее своего места = вставка ПОСЛЕ якоря, левее = ДО якоря
 * (стандартная семантика reorder); пустые категории остаются на своих местах.
 */
private fun moveCategory(
    list: List<String>,
    name: String,
    targetSlot: Int,
    leaves: List<String>,
): List<String> {
    val from = leaves.indexOf(name)
    if (targetSlot == from) return list
    val rest = list.toMutableList()
    rest.remove(name)
    val anchor = leaves.getOrNull(targetSlot)
    val anchorIdx = if (anchor == null) -1 else rest.indexOf(anchor)
    when {
        anchorIdx < 0 -> rest.add(name)
        targetSlot > from -> rest.add(anchorIdx + 1, name)
        else -> rest.add(anchorIdx, name)
    }
    return rest
}

private fun isoOf(cal: Calendar): String = String.format(
    Locale.ROOT,
    "%04d-%02d-%02d",
    cal.get(Calendar.YEAR),
    cal.get(Calendar.MONTH) + 1,
    cal.get(Calendar.DAY_OF_MONTH),
)
