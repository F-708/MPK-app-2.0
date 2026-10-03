package com.politex.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.model.BellItem
import com.politex.data.model.BellScheduleType
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorSuccess
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextDisabled
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.util.bouncyClickable
import com.politex.util.Dow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.Calendar

// B2: рельса таймлайна — только токены палитры, тема доходит до последнего экрана
private val RAIL_BLUE: Color get() = ColorActiveBlue
private val RAIL_BLUE_SOFT: Color get() = ColorActiveBlue.copy(alpha = 0.30f)
private val RAIL_GRAY: Color get() = ColorTextMuted.copy(alpha = 0.55f)
private val RAIL_GRAY_SOFT: Color get() = ColorTextDisabled

/**
 * X17: цвет рельса (линии и чёрточки-границы) в минуту [minute] — цвет сегмента урока,
 * которому эта минута принадлежит: прошедший урок/не-сегодня → ColorTextDisabled,
 * будущее до [blueEndMinutes] → ColorActiveBlue, после → ColorTextMuted 55%.
 * Старт идущего урока при этом синий (линия в этой точке синяя), а не серый.
 */
internal fun railColorForMinute(
    minute: Int,
    bells: List<BellItem>,
    currentTimeMinutes: Int,
    blueEndMinutes: Int?,
    isTodaySelected: Boolean,
): Color =
    when {
        !isTodaySelected -> RAIL_GRAY_SOFT
        bells.any { minute in it.startMinutes..it.endMinutes && it.endMinutes <= currentTimeMinutes } ->
            RAIL_GRAY_SOFT
        blueEndMinutes != null && minute <= blueEndMinutes -> RAIL_BLUE
        else -> RAIL_GRAY
    }

private sealed interface BellRow {
    data class Lesson(
        val item: BellItem,
    ) : BellRow

    data class Break(
        val startMinutes: Int,
        val endMinutes: Int,
        val minutes: Int,
        val isBig: Boolean,
    ) : BellRow
}

@Composable
fun BellsScreen(
    groupInfo: com.politex.data.model.GroupInfo,
    scheduleRepository: com.politex.data.repository.ScheduleRepository,
    scrollState: ScrollState = rememberScrollState(),
    scrollToCurrentRequested: Boolean = false,
    onScrollToCurrentConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // SUI-01-паттерн и здесь: remember{Calendar.getInstance()} застревал на дне открытия
    val now =
        com.politex.ui.util
            .rememberToday()
    val todayDow =
        remember {
            Dow.of(now)
        }
    val todayLessons by scheduleRepository
        .getLessonsForDay(groupInfo.canonicalName, todayDow)
        .collectAsState(initial = emptyList<com.politex.data.local.entity.LessonEntity>())
    val lastLessonToday =
        remember(todayLessons) {
            todayLessons.maxOfOrNull { it.lessonNumber }
        }

    val todayType = remember { CollegeBellSchedule.getTypeForDay(todayDow) }
    var selectedScheduleType by remember { mutableStateOf(todayType) }

    var currentTimeMinutes by remember {
        mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val c = Calendar.getInstance()
            currentTimeMinutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        }
    }

    val isTodaySelected = selectedScheduleType == todayType
    val activeSlot =
        remember(currentTimeMinutes, selectedScheduleType, isTodaySelected) {
            if (!isTodaySelected) {
                null
            } else {
                CollegeBellSchedule
                    .getBellsForType(selectedScheduleType)
                    .firstOrNull { currentTimeMinutes in it.startMinutes..it.endMinutes }
            }
        }

    val bells = CollegeBellSchedule.getBellsForType(selectedScheduleType)
    val blueEndNum =
        if (isTodaySelected) {
            lastLessonToday ?: bells.lastOrNull { !it.isInfoHour }?.lessonNumber
        } else {
            null
        }
    val blueEndMinutes =
        blueEndNum?.let { num ->
            bells.lastOrNull { !it.isInfoHour && it.lessonNumber <= num }?.endMinutes
        }

    val rows =
        remember(selectedScheduleType) {
            val b = CollegeBellSchedule.getBellsForType(selectedScheduleType)
            val result = mutableListOf<BellRow>()
            b.forEach { item ->
                result.add(BellRow.Lesson(item))
                if (item.breakAfterMinutes > 0) {
                    result.add(
                        BellRow.Break(
                            startMinutes = item.endMinutes,
                            endMinutes = item.endMinutes + item.breakAfterMinutes,
                            minutes = item.breakAfterMinutes,
                            isBig = item.isBigBreak,
                        ),
                    )
                }
            }
            result
        }

    // V5: один рельс на уровне списка — Canvas под карточками рисует по замеренным
    // позициям строк (индекс строки → top/height в px относительно списка).
    val rowTops = remember { mutableStateMapOf<Int, Float>() }
    val rowHeights = remember { mutableStateMapOf<Int, Float>() }
    LaunchedEffect(rows) {
        // Полный clear() ломает рельс: при смене графика одинаковые по размеру строки
        // не перекладываются и onGloballyPositioned не вызывается повторно — чистим
        // только индексы, которых в новом списке уже нет.
        val stale = rowTops.keys.filter { it !in rows.indices }
        stale.forEach {
            rowTops.remove(it)
            rowHeights.remove(it)
        }
    }

    // Тап по виджету «До звонка»/«Звонки»: проскроллить к текущему звонку
    // (или к последнему, если уроки уже закончились). Одноразово.
    var viewportPx by remember { mutableStateOf(0) }
    // N17: кратковременная подсветка текущего звонка после входа из виджета — фидбэк глазу
    var flashCurrent by remember { mutableStateOf(false) }
    LaunchedEffect(flashCurrent) {
        if (!flashCurrent) return@LaunchedEffect
        delay(1600)
        flashCurrent = false
    }
    LaunchedEffect(scrollToCurrentRequested, activeSlot, rows) {
        if (!scrollToCurrentRequested) return@LaunchedEffect
        // Ждём первого лэйаута, иначе вьюпорт/maxValue ещё 0 и прокрутка невозможна
        androidx.compose.runtime
            .snapshotFlow { scrollState.maxValue > 0 }
            .first { it }
        val target =
            activeSlot
                ?: rows.lastOrNull { it is BellRow.Lesson }?.let { (it as BellRow.Lesson).item }
        val idx = rows.indexOfFirst { (it as? BellRow.Lesson)?.item == target }
        if (idx >= 0) {
            // V5-бонус: позиция строки из замеров onGloballyPositioned вместо хардкода 56/26dp
            snapshotFlow { rowTops[idx] != null && rowHeights[idx] != null }.first { it }
            val top = rowTops[idx] ?: 0f
            val h = rowHeights[idx] ?: 0f
            // Центр карточки — в середине вьюпорта
            val scrollTo = (top + h / 2 - viewportPx / 2).toInt()
            scrollState.animateScrollTo(scrollTo.coerceIn(0, scrollState.maxValue))
            flashCurrent = true
        }
        onScrollToCurrentConsumed()
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // W34: двойной заголовок («Расписание звонков» + «Расписание уроков колледжа»)
            // убран — остаётся один, место освободилось
            Text(
                text = "Расписание звонков",
                style = TextStylePageTitle,
                maxLines = 1,
            )
        }

        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BellScheduleType.entries.forEach { type ->
                val isSelected = selectedScheduleType == type
                val isToday = type == todayType
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                        color = if (isSelected) ColorTopBar else ColorBgMain,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            // B5: хитбокс переключателя графиков 44dp
                            .heightIn(min = 44.dp)
                            .bouncyClickable { selectedScheduleType = type },
                    ) {
                        // O2: у material3 Surface нет contentAlignment (внутри Box с TopStart),
                        // а propagateMinConstraints=true растягивает Text на 44dp — глифы прижаты
                        // к верху. Центрируем через Box (как в TasksScreen/GroupSelectionDialog).
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text =
                                when (type) {
                                    BellScheduleType.STANDARD -> "ПН - ПТ"
                                    BellScheduleType.THURSDAY -> "ЧЕТВЕРГ"
                                    BellScheduleType.SATURDAY -> "СУББОТА"
                                },
                                softWrap = false,
                                maxLines = 1,
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else ColorTextBody,
                                ),
                                modifier =
                                Modifier
                                    .padding(vertical = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally),
                            )
                        }
                    }
                    Box(
                        modifier =
                        Modifier
                            .padding(top = 3.dp)
                            .width(28.dp)
                            .height(3.dp)
                            .background(
                                if (isToday) ColorActiveBlue else Color.Transparent,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
        }

        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(1.dp)
                .background(ColorDividerLight),
        )

        // B3 (решение владельца): контекстная карточка «СЕЙЧАС» есть ВСЕГДА —
        // слот не схлопывается, список не прыгает при смене урока.
        // До 1-го звонка: «Уроки ещё не начались», после последнего: «Уроки закончились».
        if (isTodaySelected && bells.isNotEmpty()) {
            val breakIdx =
                bells.indexOfFirst { bell ->
                    val breakEnd = bell.endMinutes + bell.breakAfterMinutes
                    bell.breakAfterMinutes > 0 &&
                        currentTimeMinutes > bell.endMinutes &&
                        currentTimeMinutes < breakEnd
                }
            val context: NowContext =
                when {
                    activeSlot != null -> NowContext.Active(activeSlot)
                    currentTimeMinutes < bells.first().startMinutes ->
                        NowContext.Inactive("Уроки ещё не начались", "первый звонок в ${bells.first().start}")
                    breakIdx >= 0 -> {
                        val nextStart = bells.getOrNull(breakIdx + 1)?.start ?: bells[breakIdx].end
                        NowContext.Inactive("СЕЙЧАС: ПЕРЕМЕНА", "до $nextStart")
                    }
                    else -> NowContext.Inactive("Уроки закончились", "последний звонок в ${bells.last().end}")
                }
            NowContextCard(context = context)
        }

        // V5: точка «сейчас» — только сегодня и в окне первого..последнего звонка
        val showNowDot =
            isTodaySelected &&
            bells.isNotEmpty() &&
            currentTimeMinutes in bells.first().startMinutes..bells.last().endMinutes
        val pulseRadius by
            if (showNowDot) {
                rememberInfiniteTransition(label = "RailDotPulse").animateFloat(
                    initialValue = 4.5f,
                    targetValue = 7f,
                    animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
                    label = "RailDotPulseRadius",
                )
            } else {
                remember { mutableStateOf(4.5f) }
            }

        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { viewportPx = it.height }
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // V5: рельс — первый ребёнок Box, т.е. ПОД карточками; x — в end-паддинге
                // списка (24dp), карточек не касается.
                Canvas(modifier = Modifier.matchParentSize()) {
                    if (rowTops.isEmpty()) return@Canvas
                    val cx = size.width + 10.dp.toPx()
                    val tickLen = 5.dp.toPx()

                    fun rangeOf(row: BellRow): Pair<Int, Int> =
                        when (row) {
                            is BellRow.Lesson -> row.item.startMinutes to row.item.endMinutes
                            is BellRow.Break -> row.startMinutes to row.endMinutes
                        }

                    // Минута → y: линейно внутри строки и линейно по 6dp-зазору между строками
                    fun yForMinute(m: Int): Float? {
                        for (i in rows.indices) {
                            val top = rowTops[i] ?: continue
                            val h = rowHeights[i] ?: continue
                            val (rs, re) = rangeOf(rows[i])
                            if (m in rs..re) {
                                return top + (m - rs).toFloat() / (re - rs).coerceAtLeast(1) * h
                            }
                            if (m < rs) return null
                            if (i + 1 < rows.size) {
                                val nextTop = rowTops[i + 1] ?: return null
                                val (ns, _) = rangeOf(rows[i + 1])
                                if (m < ns) {
                                    val bottom = top + h
                                    return bottom +
                                        (m - re).toFloat() / (ns - re).coerceAtLeast(1) * (nextTop - bottom)
                                }
                            }
                        }
                        return null
                    }

                    // 1) сегменты уроков: прошедшее — серое, до blueEnd — синее, дальше — приглушённое
                    bells.forEach { bell ->
                        val y0 = yForMinute(bell.startMinutes) ?: return@forEach
                        val y1 = yForMinute(bell.endMinutes) ?: return@forEach
                        drawLine(
                            color =
                            railColorForMinute(
                                bell.startMinutes,
                                bells,
                                currentTimeMinutes,
                                blueEndMinutes,
                                isTodaySelected,
                            ),
                            start = Offset(cx, y0),
                            end = Offset(cx, y1),
                            // ДОП: 1.5dp + Butt — скругления Round выступали за крайние чёрточки
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Butt,
                        )
                    }

                    // 2) чёрточки-границы уроков поверх линии
                    // X17: цвет чёрточки = цвет ЛИНИИ в этой минуте, а не «всегда синий до blueEnd»
                    bells.forEach { bell ->
                        listOf(bell.startMinutes, bell.endMinutes).forEach { m ->
                            val y = yForMinute(m) ?: return@forEach
                            drawLine(
                                color = railColorForMinute(m, bells, currentTimeMinutes, blueEndMinutes, isTodaySelected),
                                start = Offset(cx - tickLen, y),
                                end = Offset(cx + tickLen, y),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Butt,
                            )
                        }
                    }

                    // 3) X40: перемены — серый сегмент-подложка (ColorTextDisabled всегда,
                    // перемена не «урок») + серые деления 1dp по границам строки перемены
                    rows.forEachIndexed { i, row ->
                        if (row !is BellRow.Break) return@forEachIndexed
                        val top = rowTops[i] ?: return@forEachIndexed
                        val bottom = top + (rowHeights[i] ?: return@forEachIndexed)
                        drawLine(
                            color = RAIL_GRAY_SOFT,
                            start = Offset(cx, top),
                            end = Offset(cx, bottom),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Butt,
                        )
                        listOf(top, bottom).forEach { y ->
                            drawLine(
                                color = RAIL_GRAY_SOFT,
                                start = Offset(cx - tickLen, y),
                                end = Offset(cx + tickLen, y),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Butt,
                            )
                        }
                    }

                    // 4) точка «сейчас»: ядро + пульс-ореол
                    if (showNowDot) {
                        val y = yForMinute(currentTimeMinutes)
                        if (y != null) {
                            drawCircle(
                                color = RAIL_BLUE_SOFT,
                                radius = pulseRadius.dp.toPx(),
                                center = Offset(cx, y),
                            )
                            drawCircle(
                                color = RAIL_BLUE,
                                radius = 4.5.dp.toPx(),
                                center = Offset(cx, y),
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEachIndexed { index, row ->
                        Box(
                            modifier =
                            Modifier.onGloballyPositioned {
                                rowTops[index] = it.positionInParent().y
                                rowHeights[index] = it.size.height.toFloat()
                            },
                        ) {
                            when (row) {
                                is BellRow.Lesson ->
                                    LessonBellCard(
                                        item = row.item,
                                        isActive = activeSlot == row.item,
                                        highlight = flashCurrent && activeSlot == row.item,
                                    )
                                is BellRow.Break -> BreakRow(row = row)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** B3: контекст карточки «СЕЙЧАС» — активный слот либо пограничное состояние. */
private sealed interface NowContext {
    data class Active(
        val slot: BellItem,
    ) : NowContext

    data class Inactive(
        val title: String,
        val subtitle: String,
    ) : NowContext
}

@Composable
private fun NowContextCard(context: NowContext) {
    val isActive = context is NowContext.Active
    val slot = (context as? NowContext.Active)?.slot
    val title =
        when (context) {
            is NowContext.Active ->
                if (slot!!.isInfoHour) "СЕЙЧАС: ИНФОРМАЦИОННЫЙ ЧАС" else "СЕЙЧАС: ${slot.title.uppercase()}"
            is NowContext.Inactive -> context.title
        }
    val subtitle =
        when (context) {
            is NowContext.Active -> slot!!.displayRange
            is NowContext.Inactive -> context.subtitle
        }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorSurfaceHighlight,
        border = BorderStroke(1.dp, if (isActive) ColorActiveBlue else ColorBorderLight),
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                Modifier
                    .size(30.dp)
                    .background(if (isActive) ColorActiveBlue else ColorBorderLight, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                    when {
                        slot?.isInfoHour == true -> Icons.Default.Campaign
                        isActive -> Icons.Default.NotificationsActive
                        else -> Icons.Default.AccessTime
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isActive) ColorBrandBlue else ColorTextBody,
                    ),
                )
                Text(
                    text = subtitle,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        color = ColorTextBody,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LessonBellCard(
    item: BellItem,
    isActive: Boolean,
    highlight: Boolean = false,
) {
    // N17: вспышка-подсветка текущей строки при входе из виджета (~1.6s, гаснет)
    val flashAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (highlight) 0.16f else 0f,
        animationSpec = tween(durationMillis = if (highlight) 150 else 900),
        label = "BellFlash",
    )

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border =
        BorderStroke(
            1.dp,
            when {
                item.isInfoHour -> ColorSuccess
                isActive -> ColorActiveBlue
                else -> ColorBorderLight
            },
        ),
        shadowElevation = 0.dp,
        modifier =
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // N17: фоновая вспышка подсветки (flashAlpha гаснет до 0)
                if (flashAlpha > 0.005f) {
                    drawRect(
                        color = ColorActiveBlue.copy(alpha = flashAlpha),
                        size = size,
                    )
                }
            },
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color =
                    when {
                        item.isInfoHour -> ColorSuccess
                        isActive -> ColorActiveBlue
                        else -> ColorBrandBlue
                    },
                    contentColor = Color.White,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        if (item.isInfoHour) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (item.isInfoHour) "ИНФОЧАС" else "${item.lessonNumber} УРОК",
                            softWrap = false,
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, ColorBorderLight),
                color = ColorSurfaceVariantLight,
                modifier = Modifier.wrapContentWidth(),
            ) {
                Text(
                    text = item.displayRange,
                    softWrap = false,
                    maxLines = 1,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ColorBrandBlue,
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BreakRow(row: BellRow.Break) {
    val range = "${minutesToTime(row.startMinutes)} – ${minutesToTime(row.endMinutes)}"
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (row.isBig) "перемена ${row.minutes} мин (большая)" else "перемена ${row.minutes} мин",
            style =
            androidx.compose.ui.text.TextStyle(
                fontSize = 11.sp,
                fontWeight = if (row.isBig) FontWeight.SemiBold else FontWeight.Normal,
                color = if (row.isBig) ColorBrandBlue else ColorTextMuted,
            ),
        )
        Text(
            text = range,
            style =
            androidx.compose.ui.text.TextStyle(
                fontSize = 10.sp,
                color = ColorTextMuted,
            ),
        )
    }
}

private fun minutesToTime(minutes: Int): String = String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
