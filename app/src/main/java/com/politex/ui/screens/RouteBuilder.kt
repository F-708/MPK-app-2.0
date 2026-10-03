package com.politex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.politex.data.model.CollegeMap
import com.politex.data.model.CollegeMapData
import com.politex.data.model.CollegeRouteFinder
import com.politex.data.model.FloorMap
import com.politex.data.model.MapPoint
import com.politex.data.model.MapRoom
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable
import kotlin.math.hypot

// ---------- V25: ручной мультимаршрут. Чистые функции тестируются без Compose ----------

/** Точка маршрута: key — номер кабинета / id лестницы / [MAIN_ENTRANCE_KEY] для входа. */
data class Waypoint(
    val key: String,
    val label: String,
    val isEntrance: Boolean = false,
)

/**
 * X25: этап отрисовки — ОДИН непрерывный участок на одном плане (этаж или площадка 4 эт.).
 * [polylines] — несколько полилиний этапа: соседние FloorPath одного floorId+isReturn склеены
 * в этап, но если конец предыдущей не совпал с началом следующей (<1e-3) — это вторая полилиния
 * (например, разрыв на лестнице «3-4»: подход у 335 / площадка 4-го этажа).
 */
data class RouteStage(
    val floorId: String,
    val polylines: List<List<MapPoint>>,
    val label: String,
    val isReturn: Boolean = false,
)

/** Перегон между двумя waypoint'ами; route == null — недостижимо. */
data class RouteLeg(
    val from: Waypoint,
    val to: Waypoint,
    val route: CollegeRouteFinder.MultiFloorRoute?,
    val isReturn: Boolean = false,
)

/** Ключ waypoint'а главного входа = номер псевдо-комнаты mainEntranceRoom. */
const val MAIN_ENTRANCE_KEY = "100"

val waypointListSaver =
    listSaver<List<Waypoint>, Any>(
        save = { list -> list.flatMap { listOf(it.key, it.label, it.isEntrance) } },
        restore = { flat -> flat.chunked(3).map { Waypoint(it[0] as String, it[1] as String, it[2] as Boolean) } },
    )

/**
 * 6.2: глобальный режим конструктора маршрута. MainScreen прячет нижнюю навигацию, пока
 * конструктор активен (как ViewerMode для PDF). Object, а не CompositionLocal: панель рисуется
 * в MainScreen, конструктор — глубоко в карте. Флаг не rememberSaveable — смерть процесса
 * обязана его сбрасывать (панель возвращается при холодном старте).
 */
object RouteBuilderMode {
    var active by mutableStateOf(false)
}

/** Номер ближайшего реального кабинета к точке плана (подпись лестницы, pseudo-room). */
fun nearestRoomNumber(floor: FloorMap, x: Float, y: Float): String? =
    floor.rooms
        .filter { it.number.isNotBlank() && it.status != "nonexistent" }
        .minByOrNull { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) }
        ?.number

/**
 * Waypoint → MapRoom для findMultiFloorRoute: кабинет — поиск по всем этажам; главный
 * вход — псевдо-комната «100»; лестница — псевдо-комната с координатами лестницы и номером
 * ближайшего кабинета (floorForRoom по номеру даёт нужный этаж, в т.ч. «3-4»).
 */
fun resolveWaypointRoom(wp: Waypoint, mapData: CollegeMapData): MapRoom? {
    if (!wp.isEntrance) return findRoomEverywhere(mapData, wp.key)
    if (wp.key == MAIN_ENTRANCE_KEY) return mainEntranceRoom(mapData)
    CollegeMap.Floor.entries.forEach { f ->
        val floor = mapData.floor(f.id)
        floor.entrances.firstOrNull { it.id == wp.key }?.let { e ->
            val near = nearestRoomNumber(floor, e.x, e.y) ?: return null
            return MapRoom(
                id = e.id, number = near, title = wp.label, note = "", status = "normal",
                x = e.x, y = e.y, w = 0.02f, h = 0.02f,
            )
        }
    }
    return null
}

/**
 * Маршрут пары ключей через кэш (Dijkstra недешёвая). null кэшируется тоже.
 * Кэш живёт в remember экрана и читается и из композиции, и из Dispatchers.Default
 * (автонавигация) — поэтому synchronized.
 */
fun cachedRoute(
    aKey: String,
    bKey: String,
    mapData: CollegeMapData,
    floors: Map<String, FloorMap>,
    cache: MutableMap<Pair<String, String>, CollegeRouteFinder.MultiFloorRoute?>,
): CollegeRouteFinder.MultiFloorRoute? {
    val pair = aKey to bKey
    synchronized(cache) { if (cache.containsKey(pair)) return cache[pair] }
    val a = cacheWaypoint(aKey, mapData)
    val b = cacheWaypoint(bKey, mapData)
    val r = if (a != null && b != null && aKey != bKey) CollegeRouteFinder.findMultiFloorRoute(a, b, floors) else null
    synchronized(cache) { cache[pair] = r }
    return r
}

// обратный путь key → Waypoint для resolve: key кабинета = номер; иначе ищем лестницу/вход
private fun cacheWaypoint(key: String, mapData: CollegeMapData): MapRoom? {
    findRoomEverywhere(mapData, key)?.let { return it }
    return resolveWaypointRoom(Waypoint(key, "", isEntrance = true), mapData)
}

/**
 * Перегоны мультимаршрута: пары соседних точек. X19: глобальный дедуп — ключ встречается
 * в пути максимум один раз (последовательный дубль схлопывается, A,B,A не даёт петли).
 * Edge: 1 точка — маршрут от главного входа.
 */
fun buildLegs(
    wps: List<Waypoint>,
    mapData: CollegeMapData,
    floors: Map<String, FloorMap>,
    cache: MutableMap<Pair<String, String>, CollegeRouteFinder.MultiFloorRoute?>? = null,
): List<RouteLeg> {
    val pts = ArrayList<Waypoint>(wps.size + 1)
    for (wp in wps) if (pts.none { it.key == wp.key }) pts.add(wp)
    if (pts.size == 1) pts.add(0, Waypoint(MAIN_ENTRANCE_KEY, "Главный вход", isEntrance = true))
    if (pts.size < 2) return emptyList()

    fun route(a: Waypoint, b: Waypoint): CollegeRouteFinder.MultiFloorRoute? =
        if (cache != null) cachedRoute(a.key, b.key, mapData, floors, cache)
        else {
            val ra = resolveWaypointRoom(a, mapData)
            val rb = resolveWaypointRoom(b, mapData)
            if (ra != null && rb != null && a.key != b.key) CollegeRouteFinder.findMultiFloorRoute(ra, rb, floors) else null
        }

    val legs = ArrayList<RouteLeg>()
    for (i in 1 until pts.size) legs.add(RouteLeg(pts[i - 1], pts[i], route(pts[i - 1], pts[i])))
    return legs
}

/**
 * X25: Legs → этапы отрисовки (точки сразу ортогонализованы и упрощены). Смежные FloorPath
 * ОДНОГО floorId + isReturn сливаются в ОДИН этап: полилинии склеиваются, если конец
 * предыдущей совпал с началом следующей (<1e-3), иначе добавляются второй полилинией этапа.
 * Подпись БЕЗ «Этап N:»: «3-4 эт → лестница у 335 → каб. 401» (floor-префикс один раз,
 * каждый следующий сегмент дописывает только «→ …»).
 */
fun legsToStages(legs: List<RouteLeg>, mapData: CollegeMapData): List<RouteStage> {
    val stages = ArrayList<RouteStage>()
    legs.forEach { leg ->
        val fps = leg.route?.floorPaths ?: return@forEach
        fps.forEachIndexed { i, fp ->
            val floor = mapData.floor(fp.floorId)
            val isLegEnd = i == fps.lastIndex
            val near = if (!isLegEnd) fp.points.lastOrNull()?.let { nearestRoomNumber(floor, it.x, it.y) } else null
            val suffix = when {
                near != null -> " → лестница у $near"
                isLegEnd -> " → ${leg.to.label}"
                else -> ""
            }
            val poly = CollegeRouteFinder.simplifyRoute(
                CollegeRouteFinder.orthogonalize(fp.points, floor.rooms),
            )
            val last = stages.lastOrNull()
            if (last != null && last.floorId == fp.floorId && last.isReturn == leg.isReturn) {
                stages[stages.lastIndex] = last.copy(
                    polylines = gluePolylines(last.polylines, poly),
                    label = last.label + suffix,
                )
            } else {
                stages.add(RouteStage(fp.floorId, listOf(poly), "${fp.floorId} эт$suffix", leg.isReturn))
            }
        }
    }
    return stages
}

/** X25: склейка полилиний этапа — конец==начало (<1e-3) объединяем, иначе вторая полилиния. */
private fun gluePolylines(existing: List<List<MapPoint>>, add: List<MapPoint>): List<List<MapPoint>> {
    if (existing.isEmpty()) return listOf(add)
    val out = ArrayList<List<MapPoint>>(existing)
    val a = out.last().lastOrNull()
    val b = add.firstOrNull()
    if (a != null && b != null && hypot(a.x - b.x, a.y - b.y) < 1e-3f) {
        out[out.lastIndex] = out.last() + add.drop(1)
    } else {
        out.add(add)
    }
    return out
}

/**
 * X36: сдвиг полосы маршрута в экранных dp. Первая линия (i=0) идёт по центру (0), остальные
 * раскладываются ±6,±12,±18… попарно (нечётные в +, чётные в −), чтобы встречные/параллельные
 * полилинии одного этажа не слипались в одну линию.
 */
internal fun laneOffsetDp(i: Int): Float =
    if (i == 0) 0f else ((i + 1) / 2) * 6f * if (i % 2 == 1) 1f else -1f

/** Ближайший сосед (жадно) от [start] по всем [rest]; dist — дистанция ключей. */
fun greedyOrder(start: Waypoint, rest: List<Waypoint>, dist: (String, String) -> Float): List<Waypoint> {
    val pool = rest.toMutableList()
    val out = ArrayList<Waypoint>(rest.size)
    var cur = start
    while (pool.isNotEmpty()) {
        val next = pool.minByOrNull { dist(cur.key, it.key) } ?: break
        pool.remove(next)
        out += next
        cur = next
    }
    return out
}

/**
 * Сдвиг полилинии перпендикулярно ходу на [offsetPx] px ПРОСТРАНСТВА ПЛАНА (экран px ×
 * img-коэф): n = normalize(dy·imgH, −dx·imgW); вершина получает усреднённую нормаль
 * соседних сегментов.
 */
fun offsetPolyline(points: List<MapPoint>, imgW: Float, imgH: Float, offsetPx: Float): List<MapPoint> {
    if (points.size < 2 || offsetPx == 0f || imgW <= 0f || imgH <= 0f) return points
    val out = ArrayList<MapPoint>(points.size)
    for (i in points.indices) {
        var nx = 0f
        var ny = 0f
        fun addNormal(a: MapPoint, b: MapPoint) {
            val dx = (b.x - a.x) * imgW
            val dy = (b.y - a.y) * imgH
            val len = hypot(dx, dy)
            if (len < 1e-6f) return
            nx += dy / len
            ny += -dx / len
        }
        if (i > 0) addNormal(points[i - 1], points[i])
        if (i < points.lastIndex) addNormal(points[i], points[i + 1])
        val p = points[i]
        val nLen = hypot(nx, ny)
        if (nLen < 1e-6f) {
            out += p
            continue
        }
        out += MapPoint(p.x + (nx / nLen) * offsetPx / imgW, p.y + (ny / nLen) * offsetPx / imgH)
    }
    return out
}

// ---------- UI конструктора ----------

/** Чипы этапов над картой; тап — переход на этаж этапа; возвратные этапы — пунктир. */
@Composable
fun StageChipsRow(
    stages: List<RouteStage>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) {
        items(stages.size) { i ->
            val st = stages[i]
            val selected = i == selectedIndex
            Box(
                modifier =
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (selected) ColorBrandFill else ColorBgMain)
                    .drawBehind {
                        val w = with(density) { 1.dp.toPx() }
                        drawStageBorder(
                            selected = selected,
                            isReturn = st.isReturn,
                            strokePx = w,
                            dash = with(density) { floatArrayOf(4.dp.toPx(), 3.dp.toPx()) },
                        )
                    }.bouncyClickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    text = st.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                    TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (selected) Color.White else ColorTextBody,
                    ),
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }
        }
    }
}

private fun DrawScope.drawStageBorder(selected: Boolean, isReturn: Boolean, strokePx: Float, dash: FloatArray) {
    drawRoundRect(
        color = if (selected) ColorBrandFill else if (isReturn) ColorTextMuted else ColorBorderLight,
        topLeft = Offset(strokePx / 2f, strokePx / 2f),
        size = Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        style = Stroke(width = strokePx, pathEffect = if (isReturn && !selected) PathEffect.dashPathEffect(dash) else null),
    )
}

private const val ROW_H_DP = 44

/**
 * Панель списка точек (вместо RoomPanel): строки «≡ [n] каб. 214 ✕». X18: список — LazyColumn
 * с ключами по waypoint (перестановка анимируется animateItem), drag-reorder за ≡ (позиция 0
 * закреплена — это старт): перетаскиваемая строка поднимается (zIndex+тень) и визуально следует
 * за пальцем (translationY=accum), при переходе за половину строки — swap и accum корректируется.
 * 6.7: «Автонавигация» видна только при >4 точках. 6.8: панель минимум 180dp высотой.
 */
@Composable
fun RouteWaypointsPanel(
    waypoints: List<Waypoint>,
    autoOrdering: Boolean,
    ordered: Boolean,
    onRemove: (Int) -> Unit,
    onSwap: (Int, Int) -> Unit,
    onAutoOrder: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var accum by remember { mutableFloatStateOf(0f) }
    val rowPx = with(density) { ROW_H_DP.dp.toPx() }

    Surface(
        shape = RoundedCornerShape(3.dp),
        color = ColorBgMain,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorderLight),
        modifier = modifier.fillMaxWidth().heightIn(min = 180.dp).padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (waypoints.size > 4) {
                    PanelButton(
                        text = if (ordered) "АВТОНАВИГАЦИЯ ✓" else "АВТОНАВИГАЦИЯ",
                        filled = true,
                        enabled = !autoOrdering,
                        modifier = Modifier.weight(1f),
                    ) { onAutoOrder() }
                }
                if (autoOrdering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ColorBrandFill,
                    )
                }
                PanelButton(text = "ОЧИСТИТЬ", filled = false, modifier = Modifier.weight(1f)) { onClear() }
            }
            if (waypoints.isEmpty()) {
                Text(
                    text = "Тапайте кабинеты на карте, чтобы добавить точки маршрута.",
                    style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = (ROW_H_DP * 4).dp)) {
                    itemsIndexed(waypoints, key = { _, wp -> wp.key }) { i, wp ->
                        val isDragging = draggingIndex == i
                        val currentIndex by rememberUpdatedState(i)
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(ROW_H_DP.dp)
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer { if (isDragging) translationY = accum }
                                .then(
                                    if (isDragging) {
                                        Modifier.shadow(6.dp, RoundedCornerShape(3.dp)).background(ColorBgMain)
                                    } else {
                                        Modifier
                                    },
                                )
                                .animateItem(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // ≡ — ручка перетаскивания; позиция 0 закреплена (старт)
                            Box(
                                modifier =
                                Modifier
                                    .size(40.dp)
                                    .then(
                                        if (i > 0) {
                                            Modifier.pointerInput(Unit) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { draggingIndex = currentIndex; accum = 0f },
                                                    onDragEnd = { draggingIndex = -1 },
                                                    onDragCancel = { draggingIndex = -1 },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        accum += amount.y
                                                        val cur = draggingIndex
                                                        if (cur < 1) return@detectDragGesturesAfterLongPress
                                                        if (accum > rowPx / 2f && cur < waypoints.lastIndex) {
                                                            onSwap(cur, cur + 1); draggingIndex = cur + 1; accum -= rowPx
                                                        } else if (accum < -rowPx / 2f && cur > 1) {
                                                            onSwap(cur, cur - 1); draggingIndex = cur - 1; accum += rowPx
                                                        }
                                                    },
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "≡",
                                    style = TextStyle(fontSize = 16.sp, color = if (i > 0) ColorTextMuted else ColorBorderLight),
                                )
                            }
                            Text(
                                text = "[${i + 1}]",
                                style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                                modifier = Modifier.width(26.dp),
                            )
                            Text(
                                text = wp.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontSize = 14.sp, color = ColorTextTitle),
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier.size(40.dp).bouncyClickable { onRemove(i) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Убрать точку ${wp.label}",
                                    tint = ColorTextMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = if (filled) ColorBrandFill else ColorSurfaceVariantLight,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (filled) ColorBrandFill else ColorBorderLight),
        modifier = modifier.bouncyClickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = if (filled) Color.White else ColorTextBody),
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        )
    }
}
