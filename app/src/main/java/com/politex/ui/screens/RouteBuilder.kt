package com.politex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceVariantLight
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

/** Перегон между двумя waypoint'ами; route == null — недостижимо. */
data class RouteLeg(
    val from: Waypoint,
    val to: Waypoint,
    val route: CollegeRouteFinder.MultiFloorRoute?,
    val isReturn: Boolean = false,
)

/** Ключ waypoint'а главного входа = номер псевдо-комнаты mainEntranceRoom. */
const val MAIN_ENTRANCE_KEY = "100"

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
 * X36: сдвиг полосы маршрута в экранных dp. Первая линия (i=0) идёт по центру (0), остальные
 * раскладываются ±6,±12,±18… попарно (нечётные в +, чётные в −), чтобы встречные/параллельные
 * полилинии одного этажа не слипались в одну линию.
 */
internal fun laneOffsetDp(i: Int): Float =
    if (i == 0) 0f else ((i + 1) / 2) * 6f * if (i % 2 == 1) 1f else -1f

// ---------- UI конструктора (Y8: ровно две точки А и Б) ----------

/**
 * Y8: панель конструктора = два цифровых поля номера кабинета (А и Б) + «ПОСТРОИТЬ».
 * Построил — маршрут сразу активен; сброс — кнопка «Отменить маршрут» под поиском (Y10).
 * Список waypoints, drag-reorder, autonav и «Очистить» удалены по ТЗ заказчика.
 */
@Composable
fun RoutePairPanel(
    valueA: String,
    onValueA: (String) -> Unit,
    valueB: String,
    onValueB: (String) -> Unit,
    onBuild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = ColorBgMain,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorderLight),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PairField(label = "Откуда (А — пусто = вход)", value = valueA, onValueChange = onValueA, modifier = Modifier.weight(1f))
                PairField(label = "Куда (Б)", value = valueB, onValueChange = onValueB, modifier = Modifier.weight(1f))
            }
            Surface(
                shape = RoundedCornerShape(3.dp),
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

/** Y8: цифровое поле номера кабинета конструктора (как SearchField, без крестика). */
@Composable
private fun PairField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = ColorSurfaceVariantLight,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorderLight),
        modifier = modifier.height(46.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = label, style = TextStyle(fontSize = 10.sp, color = ColorTextMuted), maxLines = 1)
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


