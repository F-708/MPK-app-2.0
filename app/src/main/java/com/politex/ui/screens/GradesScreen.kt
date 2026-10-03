package com.politex.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.politex.util.GradeSettings
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
 * Волна X: X6 — секции-списки категорий и мини-дерево убраны с экрана предмета (история
 *      оценок живёт в окошке у нампада); X8 — округление ".0"/".00"/"целое" (HALF_UP),
 *      «в пользу студента» удалено; X9 — дефолт весов 25/25/50/100, шаг слайдера 25,
 *      кастомный Canvas-слайдер, weightsEnabled per-subject + глобально (GradeSettings),
 *      превью-строка весов под средней (по убыванию веса); X10 — блок (средняя+панель)
 *      вертикально центрирован; X16 — категория отображается только один раз.
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
    // X9: глобальное отключение весов (prefs «mpk_prefs», пишет экран Настроек через GradeSettings).
    // ponytail: чтение в композиции без Flow — обновление при рекомпозиции после возврата с Настроек.
    val globalWeights = GradeSettings.getGlobalWeightsEnabled(context)

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
                    globalWeights = globalWeights,
                    onBack = { settingsSubject = null },
                )
            openSubject != null ->
                GradeSubjectScreen(
                    subject = openSubject!!,
                    dao = dao,
                    globalWeights = globalWeights,
                    onBack = { openSubject = null },
                    onOpenSettings = { settingsSubject = openSubject },
                )
            else ->
                GradeSubjectList(
                    groupInfo = groupInfo,
                    dao = dao,
                    globalWeights = globalWeights,
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
    globalWeights: Boolean,
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
            // G2/G3 + X9: веса из grade_config (пусто = DEFAULT_WEIGHTS); weightsEnabled
            // (глобально и per-subject) = false → plainAverage
            // (ponytail: format в списке всегда 1 знак, округление из config — только детальный экран)
            val avg =
                remember(entries, configs, subject, globalWeights) {
                    val config = configs.firstOrNull { it.subjectName == subject }
                    subjectAverage(
                        byCategory =
                        entries
                            .filter { it.subjectName == subject }
                            .groupBy({ it.category }) { it.value },
                        config = config,
                        weightsEnabled = globalWeights && (config?.weightsEnabled ?: true),
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
    globalWeights: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = rememberToday()
    val entries by dao.entriesForSubject(subject).collectAsState(initial = emptyList())
    val configs by dao.allConfigs().collectAsState(initial = emptyList())

    val byCategory = remember(entries) { entries.groupBy({ it.category }) { it.value } }
    val config = remember(configs, subject) { configs.firstOrNull { it.subjectName == subject } }
    // X9: веса действуют, если включены глобально И у предмета
    val weightsEnabled = globalWeights && (config?.weightsEnabled ?: true)
    val ws = remember(config) { parseWeightsList(config?.weightsJson) ?: GradeAggregator.DEFAULT_WEIGHTS }
    val mode = config?.roundingMode ?: ".0"
    val rawAvg = remember(byCategory, config, weightsEnabled) { subjectAverage(byCategory, config, weightsEnabled) }
    // V18/X8: детальный экран применяет настройку округления предмета (список — всегда 1 знак)
    val avg =
        remember(rawAvg, mode) {
            rawAvg?.let { GradeAggregator.roundAvg(it, mode) }
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = ColorTextTitle)
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

        // X10: блок (средний балл + строка весов + панель ввода) — вертикально по центру
        Spacer(modifier = Modifier.weight(1f))

        // V17/X8: средний балл 40sp в режиме округления предмета
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
                    text = GradeAggregator.format(avg, mode),
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

        // X9/X16: строка весов под средней — каждая категория один раз, по убыванию веса;
        // веса выключены → скрыта. (X6: секции-списки категорий удалены — история оценок
        // видна в окошке у нампада.)
        if (weightsEnabled) {
            Text(
                text = weightsPreviewLine(ws),
                textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("grades_weight_line"),
            )
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

        // X10: нижняя половина центровки блока
        Spacer(modifier = Modifier.weight(1f))
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

// ── Слайдер веса (X9) ─────────────────────────────────────────────────────────

/**
 * X9: кастомный Canvas-слайдер весов — линия-трек 4dp + thumb-круг 16dp, 5 позиций
 * (0..100 с шагом 25, [GradeAggregator.snapWeight]). Material3 Slider анимирует
 * увеличение thumb/track при касании — здесь фидбек drag ТОЛЬКО движение thumb.
 * enabled=false → серый, жесты отключены.
 */
@Composable
private fun WeightSlider(
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // доля по полной ширине: snap в ближайшую из 5 позиций прощает неточность insets
    val snap: (Float, Float) -> Int = { x, w ->
        GradeAggregator.snapWeight((x / w).coerceIn(0f, 1f))
    }
    Canvas(
        modifier =
        modifier
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { onValueChange(snap(it.x, size.width.toFloat())) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { onValueChange(snap(it.x, size.width.toFloat())) },
                    onHorizontalDrag = { change, _ ->
                        onValueChange(snap(change.position.x, size.width.toFloat()))
                        change.consume()
                    },
                )
            },
    ) {
        val pad = 8.dp.toPx() // радиус thumb — трек не вылезает за границы
        val y = size.height / 2f
        val trackW = size.width - pad * 2
        val thumbX = pad + trackW * (value / 100f)
        val stroke = 4.dp.toPx()
        drawLine(ColorBorderLight, Offset(pad, y), Offset(pad + trackW, y), stroke, cap = StrokeCap.Round)
        if (enabled) {
            drawLine(ColorActiveBlue, Offset(pad, y), Offset(thumbX, y), stroke, cap = StrokeCap.Round)
        }
        drawCircle(if (enabled) ColorBrandFill else ColorTextMuted, 8.dp.toPx(), Offset(thumbX, y))
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

// ── Экран настроек предмета (V18, X8/X9) ─────────────────────────────────────

/** X8: «.0» = 1 знак, «.00» = 2 знака, «целое» = 0 знаков (HALF_UP, без «в пользу студента»). */
private val ROUNDING_MODES = listOf(".0" to ".0", ".00" to ".00", "целое" to "целое")

/** X8: нормализация legacy-режимов v8 ("0.1"/"0.5"/"1") к новым строкам roundAvg. */
private fun normalizeMode(mode: String?): String =
    when (mode) {
        ".00" -> ".00"
        "целое", "1" -> "целое"
        else -> ".0"
    }

/**
 * V18: «Настройки предмета» — принцип счёта (округление) и веса категорий.
 * X9: тумблер весов per-subject (выключены → слайдеры серые, средний = plainAverage),
 * дефолт весов 25/25/50/100, шаг слайдера 25 (кастомный [WeightSlider]).
 * X8: roundUp удалён из UI — при сохранении пишется false (колонка не используется).
 * СОХРАНИТЬ пишет grade_config целиком (upsertConfig = REPLACE), поэтому
 * legacy priorityJson сохраняется как есть.
 */
@Composable
private fun GradeSubjectSettingsScreen(
    subject: String,
    dao: GradeDao,
    globalWeights: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val entries by dao.entriesForSubject(subject).collectAsState(initial = emptyList())
    val byCategory = remember(entries) { entries.groupBy({ it.category }) { it.value } }

    var weights by remember { mutableStateOf<List<Int>?>(null) } // null = загрузка
    var priorityJson by remember { mutableStateOf("[]") }
    var roundingMode by remember { mutableStateOf(".0") }
    var weightsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(subject) {
        val cfg = dao.getConfig(subject)
        priorityJson = cfg?.priorityJson?.ifBlank { "[]" } ?: "[]"
        weights = parseWeightsList(cfg?.weightsJson) ?: GradeAggregator.DEFAULT_WEIGHTS
        roundingMode = normalizeMode(cfg?.roundingMode)
        weightsEnabled = cfg?.weightsEnabled ?: true
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = ColorTextTitle)
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
                // X9: тумблер весов предмета (стиль прежнего roundUp-ряда)
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = if (weightsEnabled) ColorSurfaceHighlight else ColorBgMain,
                    border = BorderStroke(1.dp, if (weightsEnabled) ColorBrandBlue else ColorBorderLight),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("grades_weights_toggle")
                        .bouncyClickable { weightsEnabled = !weightsEnabled },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Веса категорий",
                            style =
                            TextStyle(
                                fontSize = 13.sp,
                                fontWeight = if (weightsEnabled) FontWeight.Bold else FontWeight.Normal,
                                color = if (weightsEnabled) ColorTextTitle else ColorTextBody,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── Группа 2: ВЕСА ──
                Spacer(modifier = Modifier.height(16.dp))
                SectionCaption("ВЕСА")
                // X9: глобальное отключение гасит веса всех предметов
                val weightsOn = globalWeights && weightsEnabled
                val avg =
                    remember(byCategory, ws, roundingMode, weightsOn) {
                        val raw =
                            if (weightsOn) {
                                GradeAggregator.weightedAverage(byCategory, weightsMap(ws))
                            } else {
                                GradeAggregator.plainAverage(byCategory)
                            }
                        raw?.let { GradeAggregator.roundAvg(it, roundingMode) }
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
                                text = GradeAggregator.format(avg, roundingMode),
                                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = gradeColor(avg)),
                            )
                        }
                        // X9: строка долей — только при включённых весах; X16: каждая категория один раз
                        if (weightsOn) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = weightsPreviewLine(ws),
                                textAlign = TextAlign.Center,
                                style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                UI_CATEGORIES.forEachIndexed { i, category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = CATEGORY_SHORT[category.name] ?: category.title,
                            maxLines = 1,
                            style =
                            TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (weightsOn) ColorTextTitle else ColorTextMuted,
                            ),
                            modifier = Modifier.width(60.dp),
                        )
                        // X9: кастомный Canvas-слайдер, шаг 25; веса выключены → серый и неактивный
                        WeightSlider(
                            value = ws[i],
                            enabled = weightsOn,
                            onValueChange = { v -> weights = ws.toMutableList().apply { set(i, v) } },
                            modifier = Modifier.weight(1f).testTag("grades_weight_slider_${category.name}"),
                        )
                        Text(
                            text = "${ws[i]}%",
                            maxLines = 1,
                            textAlign = TextAlign.End,
                            style =
                            TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (weightsOn) ColorBrandBlue else ColorTextMuted,
                            ),
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
                                    roundUp = false, // X8: колонка оставлена, но не используется
                                    weightsEnabled = weightsEnabled, // X9
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
 * X9: средний балл предмета. Веса включены → weightedAverage (пустой weightsJson =
 * DEFAULT_WEIGHTS 25/25/50/100); выключены (глобально или у предмета) → plainAverage:
 * все оценки всех категорий как одна группа. Legacy priorityJson больше не участвует
 * (хранится только для обратной совместимости схемы).
 */
private fun subjectAverage(
    byCategory: Map<String, List<Int>>,
    config: GradeConfigEntity?,
    weightsEnabled: Boolean,
): Double? =
    if (weightsEnabled) {
        GradeAggregator.weightedAverage(
            byCategory,
            weightsMap(parseWeightsList(config?.weightsJson) ?: GradeAggregator.DEFAULT_WEIGHTS),
        )
    } else {
        GradeAggregator.plainAverage(byCategory)
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

/**
 * X9/X16: «50% Контр + 25% Обычн + …» — нормализованные доли, сортировка по весу
 * УБЫВАНИЮ (позиция отражает величину), каждая категория ровно один раз.
 */
private fun weightsPreviewLine(ws: List<Int>): String {
    val total = ws.sum()
    if (total == 0) return "—"
    return UI_CATEGORIES
        .mapIndexed { i, c -> ws[i] to "${(ws[i] * 100.0 / total).roundToInt()}% ${CATEGORY_SHORT[c.name] ?: c.title}" }
        .sortedByDescending { it.first }
        .joinToString(" + ") { it.second }
}

/** W21: компактные подписи для сегментов ввода и превью весов. */
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
