package com.politex.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.model.CollegeMap
import com.politex.data.model.CollegeMapData
import com.politex.data.model.CollegeRouteFinder
import com.politex.data.model.FloorMap
import com.politex.data.model.MapPoint
import com.politex.data.model.MapRoom
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable
import kotlin.math.hypot

data class Waypoint(
    val key: String,
    val label: String,
    val isEntrance: Boolean = false,
)

data class RouteLeg(
    val from: Waypoint,
    val to: Waypoint,
    val route: CollegeRouteFinder.MultiFloorRoute?,
    val isReturn: Boolean = false,
)

const val MAIN_ENTRANCE_KEY = "100"

object RouteBuilderMode {
    var active by mutableStateOf(false)
}

fun nearestRoomNumber(floor: FloorMap, x: Float, y: Float): String? =
    floor.rooms
        .filter { it.number.isNotBlank() && it.status != "nonexistent" }
        .minByOrNull { (it.x - x) * (it.x - x) + (it.y - y) * (it.y - y) }
        ?.number

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

private fun cacheWaypoint(key: String, mapData: CollegeMapData): MapRoom? {
    findRoomEverywhere(mapData, key)?.let { return it }
    return resolveWaypointRoom(Waypoint(key, "", isEntrance = true), mapData)
}

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

internal fun laneOffsetDp(i: Int): Float =
    if (i == 0) 0f else ((i + 1) / 2) * 6f * if (i % 2 == 1) 1f else -1f

/** FF3: состояние полей конструктора маршрута. */
data class BuilderFields(
    val active: Boolean,
    val a: String,
    val b: String,
)

/** FF3: откуда берётся отображаемый на карте маршрут после тапа по кабинету. */
enum class RouteSource { PINNED, BUILDER, NONE }

/**
 * FF3: результат тапа по кабинету — что показать и какие поля обновить.
 *
 * - [source] — источник маршрута (PINNED — запиненный авто/ручной, BUILDER —
 *   конструктор, NONE — маршрута нет).
 * - [target] — новый выбранный кабинет (панель/подсветка) — всегда равен тапнутому.
 * - [pairA]/[pairB] — если не null, записать в соответствующее поле конструктора.
 * - [build] — вызвать build-логику (как кнопка «ПОСТРОИТЬ»).
 */
data class RouteSourceResult(
    val source: RouteSource,
    val target: String,
    val pairA: String?,
    val pairB: String?,
    val build: Boolean,
)

/**
 * FF3: чистая функция разрешения тапа по кабинету на карте.
 *
 * Поведение:
 * - Конструктор ВЫКЛЮЧЕН: тап только ВЫБИРАЕТ кабинет — запиненный маршрут
 *   (авто из расписания/виджета или ручной построенный) НЕ перестраивается.
 * - Конструктор ВКЛЮЧЁН: тап заполняет поля — первый (A пусто) → A, иначе → B;
 *   при обоих непустых инициирует build. Тап по кабинету уже в поле — не дублирует.
 *
 * Тестируется без Compose.
 */
fun resolveRouteSource(
    pinnedRouteActive: Boolean,
    builderFields: BuilderFields,
    tapRoom: String,
): RouteSourceResult {
    if (!builderFields.active) {
        return RouteSourceResult(
            source = if (pinnedRouteActive) RouteSource.PINNED else RouteSource.NONE,
            target = tapRoom,
            pairA = null,
            pairB = null,
            build = false,
        )
    }
    val a = builderFields.a.trim()
    val b = builderFields.b.trim()
    return if (a.isEmpty()) {
        // первый тап → A; build если B уже заполнено (и это не тот же кабинет)
        RouteSourceResult(
            source = RouteSource.BUILDER,
            target = tapRoom,
            pairA = tapRoom,
            pairB = null,
            build = b.isNotEmpty() && tapRoom != b,
        )
    } else if (tapRoom == a || tapRoom == b) {
        // A заполнено, тап по кабинету уже в поле — не дублируем
        RouteSourceResult(
            source = RouteSource.BUILDER,
            target = tapRoom,
            pairA = null,
            pairB = null,
            build = false,
        )
    } else {
        // A заполнено → B; обе непустые → build
        RouteSourceResult(
            source = RouteSource.BUILDER,
            target = tapRoom,
            pairA = null,
            pairB = tapRoom,
            build = true,
        )
    }
}

@Composable
fun RoutePairPanel(
    valueA: String,
    onValueA: (String) -> Unit,
    valueB: String,
    onValueB: (String) -> Unit,
    onBuild: () -> Unit,
    modifier: Modifier = Modifier,
    highlightTick: Int = 0,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorderLight),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PairField(placeholder = "Откуда (пусто = вход)", value = valueA, onValueChange = onValueA, modifier = Modifier.weight(1f))
                PairField(placeholder = "Куда", value = valueB, onValueChange = onValueB, modifier = Modifier.weight(1f), highlightTick = highlightTick)
            }
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                modifier = Modifier.fillMaxWidth().bouncyClickable(onClick = onBuild),
            ) {
                Text(
                    text = "ПОСТРОИТЬ",
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PairField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    highlightTick: Int = 0,
) {
    // З3: разовая пульсация подсветки при смене highlightTick (автоподстановка «Куда»).
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(highlightTick) {
        if (highlightTick > 0) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(220))
            pulse.animateTo(0f, tween(380))
        }
    }
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = lerp(ColorSurfaceVariantLight, ColorActiveBlue.copy(alpha = 0.35f), pulse.value),
        border = androidx.compose.foundation.BorderStroke(1.dp, lerp(ColorBorderLight, ColorActiveBlue, pulse.value)),
        modifier = modifier.height(com.politex.ui.theme.Tokens.SearchHeight),
    ) {
        // BB10: без постоянной подписи сверху — полупрозрачный placeholder внутри поля
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted.copy(alpha = 0.7f)),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it.isLetter() }.take(6)) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = ColorTextTitle),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

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
