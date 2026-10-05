package com.politex.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.model.CollegeMap
import com.politex.data.model.CollegeMapData
import com.politex.data.model.CollegeRouteFinder
import com.politex.data.model.FloorMap
import com.politex.data.model.MapPoint
import com.politex.data.model.MapRoom
import com.politex.data.repository.ScheduleRepository
import com.politex.ui.components.AppCard
import com.politex.ui.components.RoomScheduleDialog
import com.politex.ui.components.currentRoomLessonNumber
import com.politex.ui.components.filterRoomLessonsForDate
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.util.Dow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val HIGHLIGHT_COLOR = ColorActiveBlue
private val ROOM_FILL = Color(0x331976D2)
private val ROOM_BORDER = Color(0xFF0057A8)
private val DEAD_COLOR = Color(0xFFC0392B)

@Composable
fun CollegeMapScreen(
    initialRoom: String?,
    onBack: () -> Unit,

    onOpenTeacher: (String) -> Unit = { },

    fromRoom: String? = null,

    showRoute: Boolean = false,

    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapData = remember { CollegeMapData.load(context) }

    val scheduleRepository = remember { com.politex.AppGraph.scheduleRepository }

    val dayKey = Calendar.getInstance().let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
    val today = remember(dayKey) { Calendar.getInstance() }
    val todayDateString =
        remember(today) {
            "%02d.%02d.%04d".format(
                today.get(Calendar.DAY_OF_MONTH),
                today.get(Calendar.MONTH) + 1,
                today.get(Calendar.YEAR),
            )
        }
    val todayDow =
        remember(today) {
            Dow.of(today)
        }

    var query by rememberSaveable { mutableStateOf(initialRoom?.trim().orEmpty()) }

    var searchFailed by rememberSaveable { mutableStateOf(false) }

    var target by rememberSaveable { mutableStateOf(initialRoom?.trim().orEmpty()) }
    var floorId by rememberSaveable {
        mutableStateOf(CollegeMap.floorForRoom(initialRoom.orEmpty())?.id ?: "1")
    }

    // AA7: инкремент при смене этапа маршрута (тап чипа) → zoom-to-fit этапа
    var stageFocus by rememberSaveable { mutableIntStateOf(0) }

    // EE4: выбранный ШАГ маршрута (индекс этапа после склейки соседних этажей)
    var stageIndex by rememberSaveable { mutableIntStateOf(0) }

    var roomScheduleFor by remember { mutableStateOf<String?>(null) }

    var roomDialogTomorrow by remember { mutableStateOf(false) }

    var syncedToday by remember { mutableStateOf(false) }
    var syncedTomorrow by remember { mutableStateOf(false) }
    var roomReloadTick by remember { mutableIntStateOf(0) }

    val tomorrowCal =
        remember(today) {
            (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        }
    val tomorrowDateString =
        remember(tomorrowCal) {
            "%02d.%02d.%04d".format(
                tomorrowCal.get(Calendar.DAY_OF_MONTH),
                tomorrowCal.get(Calendar.MONTH) + 1,
                tomorrowCal.get(Calendar.YEAR),
            )
        }
    val tomorrowDow =
        remember(tomorrowCal) {
            Dow.of(tomorrowCal)
        }

    val dialogDateString = if (roomDialogTomorrow) tomorrowDateString else todayDateString
    val dialogDow = if (roomDialogTomorrow) tomorrowDow else todayDow

    LaunchedEffect(roomScheduleFor, roomDialogTomorrow, syncedToday, syncedTomorrow) {

        if (roomScheduleFor == null || scheduleRepository.catalog().isEmpty()) {
            return@LaunchedEffect
        }
        val alreadySynced = if (roomDialogTomorrow) syncedTomorrow else syncedToday
        if (alreadySynced) return@LaunchedEffect

        runCatching { scheduleRepository.syncDayAllGroupsCached(dialogDateString, dialogDow, force = true) }
        roomReloadTick++
        if (roomDialogTomorrow) syncedTomorrow = true else syncedToday = true
    }

    val roomScheduleLessons by produceState<List<LessonEntity>>(
        initialValue = emptyList(),
        key1 = roomScheduleFor,
        key2 = roomReloadTick,
        key3 = dialogDateString,
    ) {
        val room = roomScheduleFor ?: return@produceState
        runCatching {
            scheduleRepository.getLessonsForRoomOnDate(room, dialogDateString, dialogDow)
        }.getOrNull()?.collect { value = it }
    }

    // R2-дубль1: общий минутный тикер из TodayProvider вместо своего цикла
    val nowMinutes = com.politex.ui.util.rememberNowMinutes()

    val roomScheduleCurrentLesson =
        remember(nowMinutes, dialogDow, roomDialogTomorrow) {
            if (roomDialogTomorrow) null else currentRoomLessonNumber(nowMinutes, dialogDow)
        }

    val floor: FloorMap = remember(floorId, mapData) { mapData.floor(floorId) }
    val rooms = remember(floor) { floor.rooms.filter { it.status != "nonexistent" } }
    val targetRoom = remember(floor, target) { floor.room(target) }

    LaunchedEffect(target) {
        CollegeMap.floorForRoom(target)?.let { floorId = it.id }
    }

    var routesTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) routesTick++
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val routesEnabled =
        remember(routesTick) {
            com.politex.widget.WidgetUpdateHelper.getPrefs(context)
                .getBoolean("map_routes_enabled", true)
        }

    var autoCancelled by rememberSaveable { mutableStateOf(false) }
    val autoActive = routesEnabled && !autoCancelled && (showRoute || fromRoom != null)

    val floors = remember(mapData) { CollegeMap.Floor.entries.associate { it.id to mapData.floor(it.id) } }
    val route =
        remember(autoActive, target, fromRoom, mapData, floors) {
            if (!autoActive || target.isBlank()) {
                null
            } else {
                run {
                    val to = findRoomEverywhere(mapData, target) ?: return@run null
                    val from =
                        if (!fromRoom.isNullOrBlank()) {
                            findRoomEverywhere(mapData, fromRoom)
                        } else {
                            mainEntranceRoom(mapData)
                        }
                    from?.let { CollegeRouteFinder.findMultiFloorRoute(it, to, floors) }
                }
            }
        }

    var builderActive by rememberSaveable { mutableStateOf(false) }
    var pairA by rememberSaveable { mutableStateOf("") }
    var pairB by rememberSaveable { mutableStateOf("") }
    var manualA by rememberSaveable { mutableStateOf<String?>(null) }
    var manualB by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(builderActive) {
        // R2#10: глобальный флаг трогает только экземпляр, включивший режим построения;
        // вторая карта в стеке (builder выключен) больше не сбрасывает его при выходе
        if (builderActive) RouteBuilderMode.active = true
        onDispose { if (builderActive) RouteBuilderMode.active = false }
    }

    val routeCache = remember { HashMap<Pair<String, String>, CollegeRouteFinder.MultiFloorRoute?>() }

    val manualRoute =
        remember(manualA, manualB, mapData, floors, routeCache) {
            if (manualA == null || manualB == null) null else cachedRoute(manualA!!, manualB!!, mapData, floors, routeCache)
        }

    val routeActive = autoActive || manualA != null

    val activeRoute = manualRoute ?: route

    val routePoints =
        remember(activeRoute, floorId, floor) {
            activeRoute
                ?.floorPaths
                ?.filter { it.floorId == floorId }
                ?.map { CollegeRouteFinder.routePolyline(it.points, floor) }
                ?.takeIf { it.isNotEmpty() }
        }

    val routeOffsetsDp =
        remember(routePoints) { routePoints?.mapIndexed { i, _ -> laneOffsetDp(i) } ?: emptyList() }
    val routeFinishHere =
        remember(activeRoute, floorId) { activeRoute?.floorPaths?.lastOrNull()?.floorId == floorId }

    // BB8: шильдик «через N этаж» убран; BB9: соседние одинаковые этажи схлопнуты.
    // EE4: шаги = стадии после склейки; полоса видна всегда при активном маршруте (и с 1 шагом).
    val routeStages =
        remember(activeRoute) {
            dedupeStages(activeRoute?.floorPaths?.map { it.floorId } ?: emptyList())
        }

    // EE4: подсвечен тапнутый шаг; если этаж сменили извне (чипы этажей, поиск,
    // авто-переключение на target) — первый шаг на текужном этаже.
    val selectedStage =
        if (routeStages.getOrNull(stageIndex) == floorId) stageIndex else routeStages.indexOfFirst { it == floorId }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {

        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                Modifier
                    .size(44.dp)
                    .bouncyClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(22.dp),
                )
            }
            SearchField(
                value = query,
                onValueChange = {
                    query = it
                    searchFailed = false
                },
                notFound = searchFailed,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                modifier =
                Modifier
                    .height(Tokens.ButtonHeight)
                    .bouncyClickable {
                        val found = query.trim()
                        if (found.isNotBlank()) {
                            manualA = null
                            manualB = null
                            target = found
                            searchFailed = findRoomEverywhere(mapData, found) == null
                        }
                    },
            ) {
                Text(
                    text = "НАЙТИ",
                    softWrap = false,
                    maxLines = 1,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        // BB10: «Отменить» показывается и при открытой форме (даже с пустыми полями)
        RouteActionButton(active = routeActive || builderActive) {
            if (routeActive || builderActive) {
                manualA = null
                manualB = null
                autoCancelled = true
                builderActive = false
                pairA = ""
                pairB = ""
            } else {
                builderActive = true
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        FloorRow(
            current = floorId,
            onSelect = { floorId = it },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // BB10: форма конструктора — СРАЗУ под чипами этажей. Приложение edge-to-edge
        // без ime-insets: клавиатура перекрывает только низ экрана, форма вверху видна.
        if (builderActive) {
            RoutePairPanel(
                valueA = pairA,
                onValueA = { pairA = it },
                valueB = pairB,
                onValueB = { pairB = it },
                onBuild = {
                    val a = pairA.trim()
                    val b = pairB.trim()
                    val ra = if (a.isEmpty()) mainEntranceRoom(mapData) else findRoomEverywhere(mapData, a)
                    val rb = findRoomEverywhere(mapData, b)
                    val keyA = if (a.isEmpty()) MAIN_ENTRANCE_KEY else ra?.number
                    when {
                        b.isEmpty() || rb == null ->
                            Toast.makeText(context, "Кабинет «${b.ifBlank { "…" }}» не найден на плане", Toast.LENGTH_SHORT).show()
                        ra == null || keyA == null ->
                            Toast.makeText(context, "Кабинет «$a» не найден на плане", Toast.LENGTH_SHORT).show()
                        else -> {
                            val r = cachedRoute(keyA, rb.number, mapData, floors, routeCache)
                            if (r == null) {
                                Toast.makeText(context, "${rb.number} недоступен из ${if (a.isEmpty()) "главного входа" else a}", Toast.LENGTH_SHORT).show()
                            } else {

                                manualA = keyA
                                manualB = rb.number
                                builderActive = false
                                target = rb.number
                            }
                        }
                    }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        MapCanvas(
            floorId = floorId,
            floor = floor,
            targetRoom = targetRoom,

            onRoomTap = { room ->
                manualA = null
                manualB = null
                searchFailed = false
                target = room.number
            },
            routePoints = routePoints,
            routeOffsetsDp = routeOffsetsDp,
            routeFinishHere = routeFinishHere,
            routeStages = routeStages,
            selectedStage = selectedStage,
            onSelectStage = { i ->
                // EE4: тап шага — выбрать шаг и переключить этаж;
                // AA7: zoom-to-fit bbox'а этапа только при смене этажа
                stageIndex = i
                val fid = routeStages[i]
                if (fid != floorId) {
                    stageFocus++
                    floorId = fid
                }
            },
            stageFocus = stageFocus,
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        if (!builderActive && targetRoom != null) {
            RoomPanel(
                room = targetRoom,
                onShowSchedule = { roomScheduleFor = targetRoom.number },
            )
        }
    }

    roomScheduleFor?.let { roomNumber ->
        RoomScheduleDialog(
            roomNumber = roomNumber,

            dateTitle = "$dialogDateString, ${roomDayTitle(dialogDow)}",
            lessons = filterRoomLessonsForDate(roomScheduleLessons, roomNumber, dialogDateString),
            currentLessonNumber = roomScheduleCurrentLesson,

            tomorrowSelected = roomDialogTomorrow,
            onDayChange = { roomDialogTomorrow = it },
            onTeacherClick = onOpenTeacher,
            onDismissRequest = { roomScheduleFor = null },
        )
    }
}

@Composable
private fun RoomPanel(
    room: MapRoom?,
    onShowSchedule: (String) -> Unit = { },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (room == null) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                color = ColorSurfaceVariantLight,
            ) {
                Text(
                    text = "Выберите этаж или введите номер кабинета — покажем, где он находится.",
                    style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                    modifier = Modifier.padding(14.dp),
                )
            }
            return@Column
        }

        AppCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = room.number,
                            style =
                            TextStyle(
                                fontWeight = FontWeight.Normal,
                                fontSize = 17.sp,
                                color = Color.White,
                            ),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.title.ifBlank { "Кабинет ${room.number}" },
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle),
                        maxLines = 2,
                    )
                    val sub = room.title.ifBlank { "" }
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, ColorBrandBlue),
            color = ColorBrandFill,
            modifier =
            Modifier
                .fillMaxWidth()
                .bouncyClickable { onShowSchedule(room.number) },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.EventNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ПОДРОБНЕЕ — РАСПИСАНИЕ КАБИНЕТА",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
                )
            }
        }

        if (room.note.isNotBlank()) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                color = ColorSurfaceVariantLight,
            ) {
                Text(
                    text = room.note,
                    style = TextStyle(fontSize = 13.sp, color = ColorTextBody),
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

internal fun findRoomEverywhere(
    mapData: CollegeMapData,
    number: String,
): MapRoom? = listOf("1", "2", "3-4").firstNotNullOfOrNull { mapData.floor(it).room(number) }

@Composable
private fun RouteActionButton(
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (active) ColorSurfaceVariantLight else ColorBrandFill,
        border = BorderStroke(1.dp, if (active) ColorBorderLight else ColorBrandFill),
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .bouncyClickable(onClick = onClick),
    ) {
        Text(
            text = if (active) "ОТМЕНИТЬ МАРШРУТ" else "ПРОЛОЖИТЬ МАРШРУТ",
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (active) ColorTextBody else Color.White),
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
}

private fun roomDayTitle(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "понедельник"
    2 -> "вторник"
    3 -> "среда"
    4 -> "четверг"
    5 -> "пятница"
    6 -> "суббота"
    else -> "воскресенье"
}

private const val TAP_SLOP_PX = 24f

private const val PAN_MARGIN = 0.25f

fun hitTestRoom(
    floor: FloorMap,
    xNorm: Float,
    yNorm: Float,
    slopNorm: Float,
): MapRoom? {
    val rooms = floor.rooms.filter { it.number.isNotBlank() && it.w > 0f && it.h > 0f }
    if (rooms.isEmpty()) return null

    fun inside(
        r: MapRoom,
        slop: Float,
    ): Boolean {
        val l = r.x - r.w / 2f - slop
        val t = r.y - r.h / 2f - slop
        return xNorm >= l &&
            xNorm <= l + r.w + slop * 2f &&
            yNorm >= t &&
            yNorm <= t + r.h + slop * 2f
    }

    rooms
        .filter { inside(it, 0f) }
        .minByOrNull { it.w * it.h }
        ?.let { return it }

    return rooms
        .filter { inside(it, slopNorm) }
        .minByOrNull { r -> (xNorm - r.x) * (xNorm - r.x) + (yNorm - r.y) * (yNorm - r.y) }
}

fun mapTapToRoom(
    pos: Offset,
    targetScale: Float,
    targetOffsetX: Float,
    targetOffsetY: Float,
    imgW: Float,
    imgH: Float,
    floor: FloorMap,
): MapRoom? {
    if (imgW <= 0f || imgH <= 0f || targetScale == 0f) return null
    val xNorm = ((pos.x - targetOffsetX) / targetScale) / imgW
    val yNorm = ((pos.y - targetOffsetY) / targetScale) / imgH
    val slop = TAP_SLOP_PX / targetScale / imgW
    return hitTestRoom(floor, xNorm, yNorm, slop)
}

data class GroupRoomResult(
    val room: String,
    val lessonNumber: Int,
    val isNow: Boolean,
)

fun findGroupRoomNow(
    lessons: List<com.politex.data.local.entity.LessonEntity>,
    currentMinutes: Int,
    dayOfWeek: Int,
): GroupRoomResult? {
    if (lessons.isEmpty()) return null
    val withRoom =
        lessons
            .filter { (it.roomFirst.isNotBlank() || it.roomSecond.isNotBlank()) && it.lessonNumber > 0 }
            .sortedBy { it.lessonNumber }
    if (withRoom.isEmpty()) return null

    val slot =
        CollegeBellSchedule
            .getBellsForDay(dayOfWeek)
            .firstOrNull { currentMinutes in it.startMinutes..it.endMinutes }
            ?.takeIf { it.lessonNumber > 0 }

    val current = slot?.let { s -> withRoom.firstOrNull { it.lessonNumber == s.lessonNumber } }
    if (current != null) {
        return GroupRoomResult(

            room = current.roomFirst.ifBlank { current.roomSecond },
            lessonNumber = current.lessonNumber,
            isNow = true,
        )
    }

    val endOfCurrent = slot?.endMinutes ?: currentMinutes
    val next =
        withRoom.firstOrNull { l ->
            val lessonStart =
                CollegeBellSchedule
                    .getBellsForDay(dayOfWeek)
                    .firstOrNull { it.lessonNumber == l.lessonNumber }
                    ?.startMinutes
                    ?: l.timeStart.split(":").let { p ->
                        if (p.size == 2) (p[0].toIntOrNull() ?: 0) * 60 + (p[1].toIntOrNull() ?: 0) else -1
                    }
            lessonStart > endOfCurrent
        }
    return next?.let { GroupRoomResult(it.roomFirst.ifBlank { it.roomSecond }, it.lessonNumber, isNow = false) }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    notFound: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (notFound) Color(0xFFFDECEC) else ColorSurfaceVariantLight,
        border =
        BorderStroke(
            if (notFound) 2.dp else 1.dp,
            if (notFound) DEAD_COLOR else ColorBorderLight,
        ),
        modifier = modifier.height(Tokens.ButtonHeight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (notFound) DEAD_COLOR else ColorTextMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(
                        text = "Номер кабинета, например 214",
                        style = TextStyle(fontSize = 14.sp, color = ColorTextMuted),
                        maxLines = 1,
                    )
                } else if (notFound) {
                    Text(
                        text = "Кабинета $value нет на плане",
                        style = TextStyle(fontSize = 14.sp, color = DEAD_COLOR),
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = { input ->
                        onValueChange(input.filter { it.isDigit() || it.isLetter() }.take(6))
                    },
                    singleLine = true,
                    textStyle =
                    TextStyle(
                        fontSize = 15.sp,
                        color = if (notFound) Color.Transparent else ColorTextTitle,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotBlank()) {

                Box(
                    modifier =
                    Modifier
                        .size(Tokens.HitTarget)
                        .bouncyClickable { onValueChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Очистить",
                        tint = ColorTextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FloorRow(
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CollegeMap.Floor.entries.forEach { f ->
            val selected = current == f.id
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = if (selected) ColorTopBar else ColorBgMain,
                border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
                modifier =
                Modifier
                    .weight(1f)
                    .bouncyClickable { onSelect(f.id) },
            ) {
                Text(
                    text = f.title,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    style =
                    TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (selected) Color.White else ColorTextBody,
                    ),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun MapCanvas(
    floorId: String,
    floor: FloorMap,
    targetRoom: MapRoom?,

    onRoomTap: (MapRoom) -> Unit = {},

    routePoints: List<List<MapPoint>>? = null,
    routeFinishHere: Boolean = false,
    routeStages: List<String> = emptyList(),
    selectedStage: Int = -1,
    onSelectStage: (Int) -> Unit = {},
    stageFocus: Int = 0,

    routeOffsetsDp: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val painter = painterResource(id = mapRes(floorId))
    val aspect =
        painter.intrinsicSize.let { s ->
            if (s.height > 0f && s.width > 0f) s.width / s.height else 1.7917f
        }

    val pulse by rememberInfiniteTransition(label = "подсветка").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "пульс",
    )

    val tickPhase by rememberInfiniteTransition(label = "галочки").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
        infiniteRepeatable(
            tween((ROUTE_TICK_STEP_PX / ROUTE_TICK_SPEED_PX_PER_SEC * 1000f).toInt(), easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "tickPhase",
    )

    BoxWithConstraints(
        modifier =
        modifier

            .clipToBounds()
            .background(ColorBgMain),
    ) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val imgW = viewW
        val imgH = imgW / aspect

        val viewWState = rememberUpdatedState(viewW)
        val viewHState = rememberUpdatedState(viewH)
        val imgWState = rememberUpdatedState(imgW)
        val imgHState = rememberUpdatedState(imgH)

        val measurer = rememberTextMeasurer()

        val baseFontPx = 22f
        val numberLayouts: Map<String, TextLayoutResult> =
            remember(floorId, floor, density) {
                floor.rooms.filter { it.number.isNotBlank() }.associate { r ->
                    r.id to
                        measurer.measure(
                            text = r.number,
                            style =
                            TextStyle(
                                fontSize = with(density) { baseFontPx.toSp() },
                                fontWeight = FontWeight.Normal,
                                color = ColorBrandBlue,
                            ),
                            maxLines = 1,
                        )
                }
            }

        fun clampX(
            v: Float,
            s: Float,
        ): Float {
            val vw = viewWState.value
            val c = imgWState.value * s
            if (c <= vw) return (vw - c) / 2f
            val margin = vw * PAN_MARGIN
            return v.coerceIn(vw - c - margin, margin)
        }

        fun clampY(
            v: Float,
            s: Float,
        ): Float {
            val vh = viewHState.value
            val c = imgHState.value * s
            if (c <= vh) return (vh - c) / 2f
            val margin = vh * PAN_MARGIN
            return v.coerceIn(vh - c - margin, margin)
        }

        fun restZoom(): Float = (viewHState.value / imgHState.value * 0.72f).coerceIn(1f, 2.2f)

        // AA7: счётчик смены этапа — zoom-to-fit выполняем только при смене этапа,
        // ручной зум пользователя после него не трогаем.
        val handledStage = remember { mutableIntStateOf(0) }

        LaunchedEffect(floorId, targetRoom?.id, stageFocus) {
            val vw = viewWState.value
            val vh = viewHState.value
            if (vw <= 0f || vh <= 0f) return@LaunchedEffect
            if (stageFocus != handledStage.intValue) {
                handledStage.intValue = stageFocus
                val fit = fitStageViewport(routePoints?.flatten().orEmpty(), vw, vh)
                if (fit != null) {
                    val (s, ox, oy) = fit
                    scope.launch { scale.animateTo(s, tween(420, easing = LinearOutSlowInEasing)) }
                    scope.launch { offsetX.animateTo(clampX(ox, s), tween(420, easing = LinearOutSlowInEasing)) }
                    scope.launch { offsetY.animateTo(clampY(oy, s), tween(420, easing = LinearOutSlowInEasing)) }
                    return@LaunchedEffect
                }
            }
            val iw = imgWState.value
            val ih = imgHState.value
            val rest = restZoom()
            val room = targetRoom
            if (room == null) {
                scope.launch { scale.animateTo(rest, tween(250)) }
                scope.launch { offsetX.animateTo(clampX(vw / 2f - iw * rest / 2f, rest), tween(250)) }
                scope.launch { offsetY.animateTo(clampY(vh / 2f - ih * rest / 2f, rest), tween(250)) }
                return@LaunchedEffect
            }
            val px = room.x * iw
            val py = room.y * ih
            val zoom = 2.6f

            scope.launch { scale.animateTo(zoom, tween(420, easing = LinearOutSlowInEasing)) }
            scope.launch { offsetX.animateTo(clampX(vw / 2f - px * zoom, zoom), tween(420, easing = LinearOutSlowInEasing)) }
            scope.launch { offsetY.animateTo(clampY(vh / 2f - py * zoom, zoom), tween(420, easing = LinearOutSlowInEasing)) }
        }

        LaunchedEffect(viewW, viewH) {
            offsetX.snapTo(clampX(offsetX.value, scale.value))
            offsetY.snapTo(clampY(offsetY.value, scale.value))
        }

        Box(
            modifier =
            Modifier
                .fillMaxSize()

                .pointerInput(floorId) {
                    detectTapGestures { pos ->

                        mapTapToRoom(
                            pos = pos,
                            targetScale = scale.targetValue,
                            targetOffsetX = offsetX.targetValue,
                            targetOffsetY = offsetY.targetValue,
                            imgW = imgWState.value,
                            imgH = imgHState.value,
                            floor = floor,
                        )?.let(onRoomTap)
                    }
                }.pointerInput(floorId) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        scope.launch {
                            val next = (scale.value * zoom).coerceIn(0.8f, 8f)

                            val bx = (centroid.x - offsetX.value) / scale.value
                            val by = (centroid.y - offsetY.value) / scale.value
                            scale.snapTo(next)
                            offsetX.snapTo(clampX(centroid.x - bx * next + pan.x, next))
                            offsetY.snapTo(clampY(centroid.y - by * next + pan.y, next))
                        }
                    }
                },
        ) {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        translationX = offsetX.value
                        translationY = offsetY.value
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                Image(
                    painter = painter,
                    contentDescription = "План этажа",
                    contentScale = ContentScale.Fit,
                    modifier =
                    Modifier
                        .width(with(density) { imgW.toDp() })
                        .height(with(density) { imgH.toDp() }),
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val k = scale.value
                    drawMap(
                        floor = floor,
                        imgW = imgW,
                        imgH = imgH,
                        targetRoom = targetRoom,
                        pulse = pulse,
                        zoom = k,
                        numberLayouts = numberLayouts,
                        minScreenPxPerDp = with(density) { 1.dp.toPx() },
                        baseFontPx = baseFontPx,
                    )

                    val polylines = routePoints
                    if (!polylines.isNullOrEmpty()) {
                        drawRoutePath(
                            polylines = polylines,
                            imgW = imgW,
                            imgH = imgH,
                            zoom = k,
                            pxPerDp = with(density) { 1.dp.toPx() },
                            pulse = pulse,
                            drawFinish = routeFinishHere,
                            tickPhase = tickPhase,
                            offsetsDp = routeOffsetsDp,
                        )
                    }
                }
            }
        }

        Column(
            modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ZoomButton(Icons.Default.ZoomOutMap) {
                scope.launch {
                    val z = restZoom()
                    scale.animateTo(z, tween(250))
                    offsetX.animateTo(clampX((viewW - imgW * z) / 2f, z), tween(250))
                    offsetY.animateTo(clampY((viewH - imgH * z) / 2f, z), tween(250))
                }
            }
        }

        // EE4: чипы ШАГОВ маршрута («Шаг 1..N») у самого верха карты — без подписей
        // этажей (этаж и так виден в чипах этажей сверху). Видны всегда при активном
        // маршруте, CircleShape на широком контенте даёт пилюлю.
        if (routeStages.isNotEmpty()) {
            Row(
                modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                stepLabels(routeStages).forEachIndexed { i, label ->
                    val selected = i == selectedStage
                    Surface(
                        shape = CircleShape,
                        color = if (selected) ColorTopBar else ColorBgMain,
                        border =
                        BorderStroke(
                            1.dp,
                            if (selected) ColorTopBar else ColorBorderLight,
                        ),
                        modifier =
                        Modifier
                            .height(Tokens.ChipScreen)
                            .bouncyClickable { onSelectStage(i) },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                softWrap = false,
                                style =
                                TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (selected) Color.White else ColorTextBody,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier

            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ColorBrandBlue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun DrawScope.drawMap(
    floor: FloorMap,
    imgW: Float,
    imgH: Float,
    targetRoom: MapRoom?,
    pulse: Float,
    zoom: Float,
    numberLayouts: Map<String, TextLayoutResult>,
    minScreenPxPerDp: Float,
    baseFontPx: Float,
) {
    val unit = 1f / zoom

    if (targetRoom != null) {
        val x = (targetRoom.x - targetRoom.w / 2f) * imgW
        val y = (targetRoom.y - targetRoom.h / 2f) * imgH
        val w = targetRoom.w * imgW
        val h = targetRoom.h * imgH
        val corner = 5f * unit

        drawRoundRect(
            color = HIGHLIGHT_COLOR.copy(alpha = 0.16f * pulse),
            topLeft = Offset(x - 5f * unit, y - 5f * unit),
            size = Size(w + 10f * unit, h + 10f * unit),
            cornerRadius =
            androidx.compose.ui.geometry
                .CornerRadius(corner + 3f * unit),
        )
        drawRoundRect(
            color = ROOM_FILL,
            topLeft = Offset(x, y),
            size = Size(w, h),
            cornerRadius =
            androidx.compose.ui.geometry
                .CornerRadius(corner),
        )
        drawRoundRect(
            color = if (targetRoom.status == "nonexistent") DEAD_COLOR else ROOM_BORDER,
            topLeft = Offset(x, y),
            size = Size(w, h),
            cornerRadius =
            androidx.compose.ui.geometry
                .CornerRadius(corner),
            style = Stroke(2.5f * unit),
        )
    }

    val rawFits = HashMap<String, Float>()
    floor.rooms.forEach { r ->
        val layout = numberLayouts[r.id] ?: return@forEach
        if (r.number.isBlank()) return@forEach
        val wPx = r.w * imgW
        val hPx = r.h * imgH
        val vertical = r.h > r.w * 1.6f
        val fit =
            if (vertical) {
                (wPx * 0.62f) / layout.size.height.toFloat()
            } else {
                minOf(
                    (hPx * 0.52f) / layout.size.height.toFloat(),
                    (wPx * 0.88f) / layout.size.width.toFloat(),
                )
            }
        if (fit > 0f) rawFits[r.id] = fit
    }
    val sortedFits = rawFits.values.toFloatArray().also { it.sort() }
    val medianFit = if (sortedFits.isEmpty()) 1f else sortedFits[sortedFits.size / 2]

    val minNumberPx = minScreenPxPerDp * 10f

    floor.rooms.forEach { r ->
        val layout = numberLayouts[r.id] ?: return@forEach
        if (r.number.isBlank()) return@forEach
        val raw = rawFits[r.id] ?: return@forEach

        val fit =
            roomNumberFit(
                raw = raw,
                median = medianFit,
                minReadable = minNumberPx / layout.size.height.toFloat(),
            ) ?: return@forEach

        val cx = r.x * imgW
        val cy = r.y * imgH
        val vertical = r.h > r.w * 1.6f
        val color = if (r.status == "nonexistent") DEAD_COLOR else ColorBrandBlue

        withTransform({
            if (vertical) rotate(90f, Offset(cx, cy))
            scale(fit, fit, Offset(cx, cy))
        }) {
            val topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f)

            if (layout.size.height * fit >= minNumberPx * 1.25f) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = topLeft,
                    color = Color.White,
                    drawStyle = Stroke(width = layout.size.height * 0.14f, join = StrokeJoin.Round),
                )
            }
            drawText(
                textLayoutResult = layout,
                topLeft = topLeft,
                color = color,
            )
        }
    }
}

internal fun roomNumberFit(
    raw: Float,
    median: Float,
    minReadable: Float,
): Float? {
    if (raw <= 0f) return null
    val base = minOf(median * 1.15f, raw)

    return minOf(maxOf(base, minReadable), raw)
}

internal fun mainEntranceRoom(mapData: CollegeMapData): MapRoom? = mapData
    .floor(CollegeMap.Floor.FIRST.id)
    .entrances
    .firstOrNull { it.kind == "main" }
    ?.let {
        MapRoom(
            id = "route_start_main_entrance",
            number = "100",
            title = "Главный вход",
            note = "",
            status = "normal",
            x = it.x,
            y = it.y,
            w = 0.02f,
            h = 0.02f,
        )
    }

// Бегущие шевроны «^» (BB7). Все величины ЭКРАННЫЕ: при рисовании внутри
// масштабируемого graphicsLayer домножаются на unit = 1/zoom, поэтому размер,
// шаг и скорость галочек постоянны на экране при любом зуме (0.8..8).
private const val ROUTE_TICK_STEP_PX = 56f // шаг между галочками, экранных px
private const val ROUTE_TICK_SPEED_PX_PER_SEC = 12f // скорость вдоль пути, экранных px/сек
private const val ROUTE_TICK_SIZE_DP = 10f // половина габарита галочки, dp
private const val ROUTE_TICK_STROKE_DP = 2f // внутренний (белый) штрих, dp
private const val ROUTE_LINE_WIDTH_DP = 2.5f // линия маршрута (тоньше прежних 4dp)

private fun DrawScope.drawRoutePath(
    polylines: List<List<MapPoint>>,
    imgW: Float,
    imgH: Float,
    zoom: Float,
    pxPerDp: Float,
    pulse: Float,
    drawFinish: Boolean,
    tickPhase: Float = 0f,
    offsetsDp: List<Float> = emptyList(),
) {
    if (imgW <= 0f || imgH <= 0f) return
    val unit = 1f / zoom
    val color = ColorBrandBlue
    var finish: MapPoint? = null

    // Экранные константы, компенсированные на unit: после graphicsLayer(scale=zoom)
    // дают одинаковый физический размер/шаг/скорость при любом зуме.
    val stepPx = ROUTE_TICK_STEP_PX // шаг между галочками, экранных px
    val offsetPx = tickPhase * stepPx // бегущее смещение, экранных px, [0, stepPx)
    // AA1: метки не ближе половины габарита к концам пути и не раньше start+step/2 —
    // плавно «въезжают/выезжают» за границей отступа вместо мигания на старте/финише.
    val marginPx = maxOf(stepPx, ROUTE_TICK_SIZE_DP * pxPerDp) / 2f
    val lineW = ROUTE_LINE_WIDTH_DP * pxPerDp * unit
    val tickSize = ROUTE_TICK_SIZE_DP * pxPerDp * unit
    val tickStroke = ROUTE_TICK_STROKE_DP * pxPerDp * unit

    // AA1: ОДИН общий контур — накопленная длина всех полилиний этапа с общей базой,
    // фаза одна на контур: плотность меток не ломается на стыках полилиний.
    val contour = ArrayList<Triple<Float, List<Offset>, FloatArray>>(polylines.size) // base, screen, cum
    var totalImg = 0f
    for ((pi, raw) in polylines.withIndex()) {
        if (raw.size < 2) continue

        val off = (offsetsDp.getOrNull(pi) ?: 0f) * pxPerDp * unit
        val points = if (off != 0f) offsetPolyline(raw, imgW, imgH, off) else raw
        finish = points.last()
        val screen = ArrayList<Offset>(points.size)
        points.forEach { screen += Offset(it.x * imgW, it.y * imgH) }

        val line =
            Path().apply {
                moveTo(screen[0].x, screen[0].y)
                for (i in 1 until screen.size) lineTo(screen[i].x, screen[i].y)
            }
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = lineW, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val cum = FloatArray(screen.size)
        var len = 0f
        for (i in 1 until screen.size) {
            len += hypot(screen[i].x - screen[i - 1].x, screen[i].y - screen[i - 1].y)
            cum[i] = len
        }
        contour += Triple(totalImg, screen, cum)
        totalImg += len
    }

    // Метки каждые stepPx ЭКРАННЫХ px вдоль общей длины контура (totalImg*zoom);
    // шаг пересчитывается в долю длины каждый кадр — при зуме метки текут непрерывно.
    val positions = routeTickPositions(totalImg * zoom, stepPx, offsetPx, marginPx)
    if (positions.isNotEmpty()) {
        var idx = 0
        for ((base, screen, cum) in contour) {
            if (idx >= positions.size) break
            for (i in 1 until screen.size) {
                if (idx >= positions.size) break
                val segStart = base + cum[i - 1]
                val segEnd = base + cum[i]
                val segLen = segEnd - segStart
                if (segLen <= 0f) continue
                val a = screen[i - 1]
                val b = screen[i]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val angle = atan2(dy, dx)
                while (idx < positions.size) {
                    val pImg = positions[idx] * unit // экранные px -> image-px
                    if (pImg >= segEnd) break // метка следующему сегменту (не дублируем)
                    if (pImg >= segStart) {
                        val t = (pImg - segStart) / segLen
                        drawDirectionChevron(
                            center = Offset(a.x + dx * t, a.y + dy * t),
                            angle = angle,
                            size = tickSize,
                            stroke = tickStroke,
                            outline = color,
                        )
                    }
                    idx++
                }
            }
        }
    }

    if (drawFinish) {
        val f = finish ?: return
        val c = Offset(f.x * imgW, f.y * imgH)
        drawCircle(
            color = color.copy(alpha = 0.18f + 0.22f * pulse),
            radius = (11f + 7f * pulse) * pxPerDp * unit,
            center = c,
        )
        drawCircle(color = color, radius = 5f * pxPerDp * unit, center = c)
        drawCircle(color = Color.White, radius = 2f * pxPerDp * unit, center = c)
    }
}

/**
 * Расстояния (px) для бегущих галочек вдоль пути длиной [totalLenPx]: старт с
 * фазы [offsetPx] (заворачивается в [0, stepPx)) от границы отступа и далее каждые
 * [stepPx]. Метки не рисуются ближе [marginPx] к концам (первая не раньше
 * start+step/2, последняя не позже end-step/2 при marginPx = step/2) — на концах
 * они плавно «въезжают/выезжают», а не мигают. Путь короче 2*marginPx+stepPx меток
 * не получает. Чистая функция, однородная по масштабированию всех аргументов:
 * при zoom×2 (все длины в экранных px удвоены) позиции по ДОЛЕ длины те же.
 */
internal fun routeTickPositions(
    totalLenPx: Float,
    stepPx: Float,
    offsetPx: Float,
    marginPx: Float = 0f,
): List<Float> {
    if (totalLenPx <= 0f || stepPx <= 0f) return emptyList()
    val lo = marginPx.coerceAtLeast(0f)
    val hi = totalLenPx - lo
    if (hi - lo < stepPx) return emptyList()
    var phase = offsetPx % stepPx
    if (phase < 0f) phase += stepPx
    val out = ArrayList<Float>()
    var d = lo + phase
    while (d <= hi) {
        out += d
        d += stepPx
    }
    return out
}

/**
 * AA7 zoom-to-fit этапа маршрута: [points] — план-координаты (0..1) точек этапа,
 * [viewW]/[viewH] — экран в px. scale = min(viewW/bw, viewH/bh)*0.85*0.8 (BB11:
 * дополнительное отдаление ~20%; clamp в [restZoom, 8] как у ручного зума),
 * offset — чтобы центр bbox встал в центр экрана.
 * План занимает imgW=viewW, imgH=viewW/PLAN_ASPECT (все планы 1376×768).
 * Чистая функция — тестируется без Compose.
 */
internal fun fitStageViewport(
    points: List<MapPoint>,
    viewW: Float,
    viewH: Float,
): Triple<Float, Float, Float>? {
    if (points.isEmpty() || viewW <= 0f || viewH <= 0f) return null
    val imgH = viewW / CollegeRouteFinder.PLAN_ASPECT
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    points.forEach {
        val px = it.x * viewW
        val py = it.y * imgH
        if (px < minX) minX = px
        if (px > maxX) maxX = px
        if (py < minY) minY = py
        if (py > maxY) maxY = py
    }
    val bw = (maxX - minX).coerceAtLeast(1f)
    val bh = (maxY - minY).coerceAtLeast(1f)
    val rest = (viewH / imgH * 0.72f).coerceIn(1f, 2.2f)
    // BB11: итоговый scale ещё ×0.8 — отдалить этап, clamp сохранён
    val scale = (minOf(viewW / bw, viewH / bh) * 0.85f * 0.8f).coerceIn(rest, 8f)
    return Triple(
        scale,
        viewW / 2f - (minX + maxX) / 2f * scale,
        viewH / 2f - (minY + maxY) / 2f * scale,
    )
}

// BB7: плечи шеврона под ±35° от направления движения
private const val CHEVRON_COS35 = 0.8192f
private const val CHEVRON_SIN35 = 0.5736f

/**
 * BB7: бегущий симметричный шеврон «^» вдоль пути: вершина впереди, два РАВНЫХ
 * плеча под ±35° к оси сегмента ([angle] в радианах — направление движения),
 * симметрично относительно оси. Белая заливка + синяя обводка — читается и на
 * линии маршрута, и на любом фоне карты. Все размеры в image-px (уже компенсированы).
 */
private fun DrawScope.drawDirectionChevron(
    center: Offset,
    angle: Float,
    size: Float,
    stroke: Float,
    outline: Color,
) {
    val c = cos(angle)
    val s = sin(angle)

    fun rot(
        x: Float,
        y: Float,
    ) = Offset(center.x + x * c - y * s, center.y + x * s + y * c)

    // локальные координаты: +x — по ходу движения
    val arm = 0.75f * size
    val apex = rot(0.30f * size, 0f)
    val backX = 0.30f * size - arm * CHEVRON_COS35
    val spread = arm * CHEVRON_SIN35
    val e1 = rot(backX, spread)
    val e2 = rot(backX, -spread)

    drawLine(outline, apex, e1, strokeWidth = stroke * 1.9f, cap = StrokeCap.Round)
    drawLine(outline, apex, e2, strokeWidth = stroke * 1.9f, cap = StrokeCap.Round)
    drawLine(Color.White, apex, e1, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(Color.White, apex, e2, strokeWidth = stroke, cap = StrokeCap.Round)
}

/** BB9: схлопывает ПОДРЯД идущие одинаковые floorId этапов («3-4, 3-4» → «3-4»). Чистая функция. */
internal fun dedupeStages(list: List<String>): List<String> = list.filterIndexed { i, id -> i == 0 || id != list[i - 1] }

/** EE4: подписи шагов маршрута — «Шаг 1..N» по порядку этапов после склейки. Чистая функция. */
internal fun stepLabels(stages: List<String>): List<String> = stages.mapIndexed { i, _ -> "Шаг ${i + 1}" }

private fun mapRes(floorId: String): Int = when (floorId) {
    "2" -> com.politex.R.drawable.map_floor2
    "3-4" -> com.politex.R.drawable.map_floor34
    else -> com.politex.R.drawable.map_floor1
}
