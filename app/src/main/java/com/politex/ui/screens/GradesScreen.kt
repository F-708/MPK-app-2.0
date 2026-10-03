package com.politex.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.MpkDatabase
import com.politex.data.local.dao.GradeDao
import com.politex.data.local.entity.GradeConfigEntity
import com.politex.data.local.entity.GradeEntryEntity
import com.politex.data.model.GroupInfo
import com.politex.ui.theme.ColorActiveBlue
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
 * Ф4.2 «Оценки» (агент G, MASTER-PLAN фаза 4.2) + волны W17–W22, V17–V20.
 *
 * V17: средний балл 40sp, пустые секции категорий не рендерятся, EmptyDots в Box 18dp.
 * V18: «НАСТРОИТЬ ВЕС» заменён шестерёнкой в шапке → GradeSubjectSettingsScreen
 *      (принцип счёта: округление 0.1/0.5/целое + «в пользу студента»; веса слайдерами).
 *      Экран дерева с drag&drop (бывший W22 «Как считается балл») удалён — priorityJson
 *      остаётся только как legacy-порядок сворачивания, когда веса не заданы.
 * V19: LAB убран из UI (enum и данные не тронуты): 4 сегмента категорий, без «Зачёт/Незачёт».
 * V20: окошко введённых оценок активной категории между чипами и нампадом (тап = удалить),
 *      счётчик у чипа категории, ЭКЗАМЕН без лимита (всегда insert).
 *
 * G1: «страницы» без nav-графа (rememberSaveable-строки).
 * Данные — напрямую из GradeDao (паттерн MaterialBaseScreen), без ViewModel.
 */

/**
 * V17: детальный режим оценок (открыт предмет или его настройки) — по образцу ViewerMode.
 * TODO(босс): скрыть чипы хаба (материалы/задания/оценки) в этом режиме — требует правки
 * LessonsHubScreen (зона VIEWERS): обернуть чипы в `if (!GradeDetailMode.active && !ViewerMode.active)`.
 * Флаг не rememberSaveable: смерть процесса обязана его сбрасывать.
 */
object GradeDetailMode {
    var active by mutableStateOf(false)
}

/** V19: категории в UI — без LAB (enum/данные не трогаем, старые LAB-записи просто не видны). */
private val UI_CATEGORIES = GradeCategory.values().filter { it != GradeCategory.LAB }

@Composable
fun GradesScreen(
    groupInfo: GroupInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dao = remember { MpkDatabase.getInstance(context).gradeDao() }

    var openSubject by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsSubject by rememberSaveable { mutableStateOf<String?>(null) }

    // V17: хаб-чипы LessonsHub должны гаснуть, когда открыт детальный экран оценок
    val detail = openSubject != null || settingsSubject != null
    DisposableEffect(detail) {
        GradeDetailMode.active = detail
        onDispose { GradeDetailMode.active = false }
    }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        when {
            settingsSubject != null ->
                GradeSubjectSettingsScreen(
                    subject = settingsSubject!!,
                    dao = dao,
                    onBack = { settingsSubject = null },
                )
            openSubject != null ->
                GradeSubjectScreen(
                    subject = openSubject!!,
                    dao = dao,
                    onBack = { openSubject = null },
                    onOpenSettings = { settingsSubject = openSubject },
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
            // G2/G3 + V18: веса из grade_config, если заданы; иначе legacy-сворачивание по priority
            // (ponytail: format в списке всегда 1 знак, округление из config — только детальный экран)
            val avg =
                remember(entries, configs, subject) {
                    subjectAverage(
                        byCategory =
                        entries
                            .filter { it.subjectName == subject }
                            .groupBy({ it.category }) { it.value },
                        config = configs.firstOrNull { it.subjectName == subject },
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
                        // W18: нет оценок — три точки, а не «—» (шижина = сами точки, Row!)
                        EmptyDots(modifier = Modifier.width(22.dp).testTag("grades_avg_$subject"))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeSubjectScreen(
    subject: String,
    dao: GradeDao,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = rememberToday()
    val entries by dao.entriesForSubject(subject).collectAsState(initial = emptyList())
    val configs by dao.allConfigs().collectAsState(initial = emptyList())

    val byCategory = remember(entries) { entries.groupBy({ it.category }) { it.value } }
    val config = remember(configs, subject) { configs.firstOrNull { it.subjectName == subject } }
    val priority = remember(config) { config?.let { parsePriority(it.priorityJson) } ?: GradeAggregator.DEFAULT_PRIORITY }
    val rawAvg = remember(byCategory, config) { subjectAverage(byCategory, config) }
    // V18: детальный экран применяет настройку округления предмета (список — всегда 1 знак)
    val avg =
        remember(rawAvg, config) {
            rawAvg?.let { GradeAggregator.roundAvg(it, config?.roundingMode ?: "0.1", config?.roundUp == true) }
        }

    var activeCategory by rememberSaveable(subject) { mutableStateOf(GradeCategory.REGULAR.name) }

    // W19: быстрый ввод без «+ работу», дата = сегодня. V20: ЭКЗАМЕН без лимита — всегда insert
    fun submit(value: Int) {
        val entry =
            GradeEntryEntity(
                subjectName = subject,
                category = activeCategory,
                value = value,
                label = "",
                dateIso = isoOf(today),
            )
        scope.launch { dao.insert(entry) }
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .testTag("grades_subject_screen"),
    ) {
        // Шапка (V18): назад + предмет (W17: две строки с многоточием) + шестерёнка справа
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
                    .padding(end = 4.dp),
            )
            IconButton(
                onClick = onOpenSettings,
                modifier =
                Modifier
                    .size(44.dp)
                    .testTag("grades_settings_btn"),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Настройки предмета",
                    tint = ColorTextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // V17: средний балл 40sp, честная вертикальная центровка + отступ от названия
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (avg == null) {
                EmptyDots(modifier = Modifier.testTag("grades_current_avg"))
            } else {
                Text(
                    text = GradeAggregator.format(avg),
                    style =
                    TextStyle(
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = gradeColor(avg),
                    ),
                    modifier = Modifier.testTag("grades_current_avg"),
                )
            }
        }

        // W22: мини-дерево формулы — листья-категории снизу, итог сверху (legacy priority)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AggregationTree(priority = priority, byCategory = byCategory)
        }

        // Секции категорий (V17: пустые не рендерятся вовсе; V19: без LAB)
        LazyColumn(
            modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("grades_entries_list"),
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            UI_CATEGORIES.forEach { category ->
                val categoryEntries = entries.filter { it.category == category.name }
                if (categoryEntries.isEmpty()) return@forEach
                item(key = "header_${category.name}") {
                    Text(
                        text = "${category.title} · ${categoryEntries.size}",
                        style =
                        TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextMuted,
                        ),
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                items(categoryEntries, key = { it.id }) { entry ->
                    GradeEntryRow(
                        entry = entry,
                        onDelete = { scope.launch { dao.deleteById(entry.id) } },
                    )
                }
            }
        }

        // ── Панель ввода (G4, W20/W21, V19/V20) ──
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
                // W21/V19: 4 сегмента (без LAB), V20: счётчик оценок у чипа
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    UI_CATEGORIES.forEach { category ->
                        CategorySegment(
                            category = category,
                            count = entries.count { it.category == category.name },
                            selected = activeCategory == category.name,
                            onClick = { activeCategory = category.name },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // V20: окошко введённых оценок активной категории; тап = удалить без подтверждения
                val activeEntries = remember(entries, activeCategory) { entries.filter { it.category == activeCategory } }
                if (activeEntries.isEmpty()) {
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("grades_recent_box"),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyDots()
                    }
                } else {
                    FlowRow(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp, max = 124.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag("grades_recent_box"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // ponytail: DAO отдаёт ASC — самая свежая оценка справа без reversed()
                        // (спека писала «reversed», предполагая DESC-порядок)
                        activeEntries.forEach { e ->
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = ColorBgMain,
                                border = BorderStroke(1.dp, ColorBorderLight),
                                modifier =
                                Modifier
                                    .size(28.dp)
                                    .testTag("grades_recent_${e.id}")
                                    .bouncyClickable { scope.launch { dao.deleteById(e.id) } },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = e.value.toString(),
                                        maxLines = 1,
                                        style =
                                        TextStyle(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = gradeColor(e.value.toDouble()),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // W20: нампад 2×5 (1–5 / 6–9 и 10), кнопки ~56dp с weight(1f) — без скролла
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

/** W19: строка оценки — только значение-бейдж + ✕ удаления (44dp). */
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
            // W19: бейдж значения — без бейджа категории и даты
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBgMain,
                border = BorderStroke(1.dp, ColorBorderLight),
            ) {
                Text(
                    text = entry.value.toString(),
                    maxLines = 1,
                    style =
                    TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = gradeColor(entry.value.toDouble()),
                    ),
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

/** W21/V20: сегмент категории — компактная подпись со счётчиком, weight(1f), выбранная залита ColorTopBar. */
@Composable
private fun RowScope.CategorySegment(
    category: GradeCategory,
    count: Int,
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
            val short = CATEGORY_SHORT[category.name] ?: category.title
            Text(
                text = if (count > 0) "$short · $count" else short, // V20: счётчик оценок
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

// ── Дерево агрегации (W22, legacy priority) ─────────────────────────────────

/** W22: параметры раскладки дерева — после V18 осталось только мини-дерево на экране предмета. */
private data class TreeSpec(
    val cardW: Dp,
    val cardH: Dp,
    val gap: Dp,
    val vGap: Dp,
)

private val MiniTreeSpec = TreeSpec(cardW = 64.dp, cardH = 34.dp, gap = 8.dp, vGap = 14.dp)

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
 * W22: мини-дерево агрегации — лестница пар снизу вверх (листья → итог),
 * линии = «(левый + правый) ÷ 2». Рисует только непустые категории
 * (честная текущая формула). V18: полное дерево с drag&drop удалено вместе
 * с кнопкой «НАСТРОИТЬ ВЕС» — его заменил экран настроек предмета.
 */
@Composable
private fun AggregationTree(
    priority: List<String>,
    byCategory: Map<String, List<Int>>,
) {
    val levels =
        remember(priority, byCategory) {
            GradeAggregator.buildTreeLevels(priority, byCategory.filterValues { it.isNotEmpty() }.keys)
        }
    if (levels.isEmpty()) return
    val layout = remember(levels) { treeLayout(levels, MiniTreeSpec) }

    Box(modifier = Modifier.size(layout.widthDp.dp, layout.heightDp.dp)) {
        // W22: линии сложения — узел 0 уровня k сходится с узлами 0/1 уровня k-1
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cardHpx = MiniTreeSpec.cardH.toPx()
            val vGapPx = MiniTreeSpec.vGap.toPx()
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
                val x = (layout.centers[k][j] - MiniTreeSpec.cardW.value / 2f).dp
                val y = (layout.heightDp - MiniTreeSpec.cardH.value - k * (MiniTreeSpec.cardH.value + MiniTreeSpec.vGap.value)).dp
                Box(
                    modifier =
                    Modifier
                        .offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                        .size(MiniTreeSpec.cardW, MiniTreeSpec.cardH),
                ) {
                    TreeCard(
                        node = node,
                        isTop = k == levels.lastIndex,
                        byCategory = byCategory,
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
) {
    val leaves = node.split("+")
    val isLeaf = leaves.size == 1
    val value = remember(node, byCategory) { GradeAggregator.average(byCategory, leaves) }
    val leafAvg = if (isLeaf) byCategory[leaves[0]]?.average() else null
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (isTop) ColorTopBar else ColorBgMain,
        border = BorderStroke(1.dp, if (isTop) ColorTopBar else ColorBorderLight),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
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
                        text = CATEGORY_SHORT[leaves[0]] ?: leaves[0],
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

/** W18/V17: три наглядные точки — пустое состояние; Box 18dp, вертикально по центру. */
@Composable
private fun EmptyDots(modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .height(18.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
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
}

// ── Экран настроек предмета (V18) ────────────────────────────────────────────

private val ROUNDING_MODES = listOf("0.1" to "0.1", "0.5" to "0.5", "1" to "Целое")

/**
 * V18: «Настройки предмета» — принцип счёта (шаг округления + «в пользу студента»)
 * и веса категорий (слайдеры 0..100 с шагом 5). Живое превью среднего балла.
 * СОХРАНИТЬ пишет grade_config целиком (upsertConfig = REPLACE), поэтому
 * legacy priorityJson сохраняется как есть.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeSubjectSettingsScreen(
    subject: String,
    dao: GradeDao,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val entries by dao.entriesForSubject(subject).collectAsState(initial = emptyList())
    val byCategory = remember(entries) { entries.groupBy({ it.category }) { it.value } }

    var weights by remember { mutableStateOf<List<Int>?>(null) } // null = загрузка
    var priorityJson by remember { mutableStateOf("[]") }
    var roundingMode by remember { mutableStateOf("0.1") }
    var roundUp by remember { mutableStateOf(false) }

    LaunchedEffect(subject) {
        val cfg = dao.getConfig(subject)
        priorityJson = cfg?.priorityJson?.ifBlank { "[]" } ?: "[]"
        weights = parseWeightsList(cfg?.weightsJson) ?: List(UI_CATEGORIES.size) { 25 }
        roundingMode = cfg?.roundingMode ?: "0.1"
        roundUp = cfg?.roundUp == true
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .testTag("grades_settings_screen"),
    ) {
        // Шапка: back44 + «Настройки предмета» + имя предмета 12sp muted
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp).testTag("grades_settings_back")) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Назад", tint = ColorTextTitle)
            }
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    text = "Настройки предмета",
                    maxLines = 1,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
                )
                Text(
                    text = subject,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                )
            }
        }

        val ws = weights
        if (ws != null) {
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            ) {
                // ── Группа 1: ПРИНЦИП СЧЁТА ──
                SectionCaption("ПРИНЦИП СЧЁТА")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ROUNDING_MODES.forEach { (value, label) ->
                        RoundingSegment(
                            label = label,
                            selected = roundingMode == value,
                            tag = "grades_round_$value",
                            onClick = { roundingMode = value },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = if (roundUp) ColorSurfaceHighlight else ColorBgMain,
                    border = BorderStroke(1.dp, if (roundUp) ColorBrandBlue else ColorBorderLight),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("grades_roundup_toggle")
                        .bouncyClickable { roundUp = !roundUp },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Округлять в пользу студента",
                            style =
                            TextStyle(
                                fontSize = 13.sp,
                                fontWeight = if (roundUp) FontWeight.Bold else FontWeight.Normal,
                                color = if (roundUp) ColorTextTitle else ColorTextBody,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── Группа 2: ВЕСА ──
                Spacer(modifier = Modifier.height(16.dp))
                SectionCaption("ВЕСА")
                val avg =
                    remember(byCategory, ws, roundingMode, roundUp) {
                        GradeAggregator
                            .weightedAverage(byCategory, weightsMap(ws))
                            ?.let { GradeAggregator.roundAvg(it, roundingMode, roundUp) }
                    }
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorSurfaceHighlight,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("grades_weight_preview"),
                ) {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Средний балл", style = TextStyle(fontSize = 11.sp, color = ColorTextMuted))
                        if (avg == null) {
                            EmptyDots()
                        } else {
                            Text(
                                text = GradeAggregator.format(avg),
                                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = gradeColor(avg)),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Нормализованные доли: «35% Обычн + 25% Практи + …»
                        val total = ws.sum()
                        Text(
                            text =
                            if (total == 0) {
                                "—"
                            } else {
                                UI_CATEGORIES
                                    .mapIndexed { i, c ->
                                        "${(ws[i] * 100.0 / total).roundToInt()}% ${CATEGORY_SHORT[c.name] ?: c.title}"
                                    }.joinToString(" + ")
                            },
                            textAlign = TextAlign.Center,
                            style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val sliderColors =
                    SliderDefaults.colors(
                        thumbColor = ColorBrandFill,
                        activeTrackColor = ColorActiveBlue,
                        inactiveTrackColor = ColorBorderLight,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    )
                UI_CATEGORIES.forEachIndexed { i, category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = CATEGORY_SHORT[category.name] ?: category.title,
                            maxLines = 1,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
                            modifier = Modifier.width(60.dp),
                        )
                        // V18: material3 1.3.0 — thumb 16dp и трек 4dp только через slot-overload
                        val interactionSource = remember { MutableInteractionSource() }
                        Slider(
                            value = ws[i].toFloat(),
                            onValueChange = { v -> weights = ws.toMutableList().apply { set(i, v.roundToInt()) } },
                            colors = sliderColors,
                            interactionSource = interactionSource,
                            steps = 19, // шаг 5
                            thumb = {
                                SliderDefaults.Thumb(
                                    interactionSource = interactionSource,
                                    colors = sliderColors,
                                    thumbSize = DpSize(16.dp, 16.dp),
                                )
                            },
                            track = { state ->
                                SliderDefaults.Track(
                                    sliderState = state,
                                    modifier = Modifier.height(4.dp),
                                    colors = sliderColors,
                                    drawStopIndicator = null,
                                    drawTick = { _, _ -> },
                                )
                            },
                            valueRange = 0f..100f,
                            modifier =
                            Modifier
                                .weight(1f)
                                .testTag("grades_weight_slider_${category.name}"),
                        )
                        Text(
                            text = "${ws[i]}%",
                            maxLines = 1,
                            textAlign = TextAlign.End,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorBrandBlue),
                            modifier = Modifier.width(44.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // СОХРАНИТЬ: BrandFill 44dp на всю ширину
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                border = BorderStroke(1.dp, ColorBrandFill),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .height(44.dp)
                    .testTag("grades_settings_save")
                    .bouncyClickable {
                        scope.launch {
                            dao.upsertConfig(
                                GradeConfigEntity(
                                    subjectName = subject,
                                    priorityJson = priorityJson,
                                    weightsJson = JSONArray(ws).toString(),
                                    roundingMode = roundingMode,
                                    roundUp = roundUp,
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

/** V18: CAPS-заголовок группы настроек (стиль Tokens.SectionHeader). */
@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorBrandBlue),
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

/** V18: сегмент шага округления — ChipForm 32dp, выбранный залит ColorTopBar (как чипы категорий). */
@Composable
private fun RowScope.RoundingSegment(
    label: String,
    selected: Boolean,
    tag: String,
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
            .testTag(tag)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
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

// ── Мелочи ───────────────────────────────────────────────────────────────────

/** G3: ≥7 зелёный, <4 красный, 4–6.9 нейтральный, нет оценок — приглушённый. */
private fun gradeColor(avg: Double?): Color = when {
    avg == null -> ColorTextMuted
    avg >= 7.0 -> ColorSuccess
    avg < 4.0 -> ColorError
    else -> ColorTextTitle
}

/**
 * V18: средний балл предмета — weightedAverage по weightsJson, если веса заданы;
 * иначе legacy-сворачивание пар по priorityJson (обратная совместимость).
 * ponytail: мини-дерево на экране предмета по-прежнему рисует priority-формулу —
 * при заданных весах оно иллюстративно, честное число показывает превью настроек.
 */
private fun subjectAverage(
    byCategory: Map<String, List<Int>>,
    config: GradeConfigEntity?,
): Double? {
    val weights = parseWeightsList(config?.weightsJson)
    return if (weights != null) {
        GradeAggregator.weightedAverage(byCategory, weightsMap(weights))
    } else {
        GradeAggregator.average(
            byCategory,
            config?.let { parsePriority(it.priorityJson) } ?: GradeAggregator.DEFAULT_PRIORITY,
        )
    }
}

/** V18: weightsJson — позиционный JSON-массив по [UI_CATEGORIES]; пусто/бито/не той длины = null. */
private fun parseWeightsList(json: String?): List<Int>? =
    try {
        if (json.isNullOrBlank()) {
            null
        } else {
            val arr = JSONArray(json)
            if (arr.length() != UI_CATEGORIES.size) {
                null
            } else {
                (0 until arr.length()).map { arr.getInt(it).coerceIn(0, 100) }
            }
        }
    } catch (_: Exception) {
        null
    }

private fun weightsMap(weights: List<Int>): Map<String, Int> = UI_CATEGORIES.map { it.name }.zip(weights).toMap()

private fun parsePriority(json: String): List<String> =
    try {
        val arr = JSONArray(json)
        (0 until arr.length())
            .map { arr.getString(it) }
            .filter { name -> name != GradeCategory.LAB.name && GradeCategory.values().any { it.name == name } }
            .ifEmpty { GradeAggregator.DEFAULT_PRIORITY }
    } catch (_: Exception) {
        GradeAggregator.DEFAULT_PRIORITY
    }

/** W21: компактные подписи для сегментов ввода, мини-дерева и превью весов. */
private val CATEGORY_SHORT =
    mapOf(
        GradeCategory.REGULAR.name to "Обычн",
        GradeCategory.PRACTICAL.name to "Практи",
        GradeCategory.CONTROL.name to "Контр",
        GradeCategory.EXAM.name to "Экз",
    )

private fun isoOf(cal: Calendar): String =
    String.format(
        Locale.ROOT,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    )
