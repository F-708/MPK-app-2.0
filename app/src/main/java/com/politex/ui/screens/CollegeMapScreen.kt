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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.politex.ui.components.RoomScheduleDialog
import com.politex.ui.components.currentRoomLessonNumber
import com.politex.ui.components.filterRoomLessonsForDate
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable
import com.politex.util.Dow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val HIGHLIGHT_COLOR = Color(0xFF0072CE)
private val ROOM_FILL = Color(0x331976D2)
private val ROOM_BORDER = Color(0xFF0057A8)
private val DEAD_COLOR = Color(0xFFC0392B)

@Composable
fun CollegeMapScreen(
    initialRoom: String?,
    onBack: () -> Unit,
    // Т6: переход к карточке преподавателя из расписания кабинета
    onOpenTeacher: (String) -> Unit = { },
    // R1: точка входа маршрута из расписания/виджета. НЕ null (включая "") = запрос
    // пришёл «показать маршрут»: "" — старт от главного входа, "214" — старт от кабинета.
    // Обычный вход с карты/меню — null, маршрута нет.
    fromRoom: String? = null,
    // R1: явный запрос маршрута (виджет NowNext). E4: приходит nav-аргументом route=
    // (buildMapRoute) вместо одноразового флага AppUiState.showRouteOnMap.
    showRoute: Boolean = false,
    // E4: экран — запись backstack'а Navigation-Compose. Выбранный кабинет живёт в
    // rememberSaveable записи: переход в карточку преподавателя и «назад» восстанавливают
    // его же (Б8/N15 без подъёма onRoomChanged в родителя — аргумент room стабилен в записи).
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapData = remember { CollegeMapData.load(context) }

    // Т5/Б3/E1: репозиторий — единый из AppGraph (каталог + teacherLessonDao + общий кэш),
    // вместо локального создания без teacherLessonDao (офлайн-преподаватели не работали)
    val scheduleRepository = remember { com.politex.AppGraph.scheduleRepository }
    // MAP-05: ключ по номеру дня — если экран пережил полночь, следующая recomposition
    // перечитает «сегодня» (раньше remember{Calendar.getInstance()} навсегда застревал на дне
    // открытия экрана). Calendar вместо java.time — minSdk 24 без desugaring.
    // ponytail: перечёт по recomposition, не по таймеру; ON_RESUME-обсервер — если карта будет висеть через полночь
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
    val scope = rememberCoroutineScope()

    var searchFailed by rememberSaveable { mutableStateOf(false) }
    // MAP-01: target — ЕДИНСТВЕННЫЙ источник истины на экране. initialRoom читаем только
    // в инициализаторе. E4: rememberSaveable — состояние выбранного кабинета/этажа живёт
    // в saved-state записи backstack'а: map → teacher → back восстанавливает тот же кабинет
    // (приёмка N15) без эха onRoomChanged родителю.
    var target by rememberSaveable { mutableStateOf(initialRoom?.trim().orEmpty()) }
    var floorId by rememberSaveable {
        mutableStateOf(CollegeMap.floorForRoom(initialRoom.orEmpty())?.id ?: "1")
    }

    // Т6: расписание выбранного кабинета
    var roomScheduleFor by remember { mutableStateOf<String?>(null) }
    // Б8: диалог кабинета показывает сегодня или завтра — по умолчанию сегодня
    var roomDialogTomorrow by remember { mutableStateOf(false) }
    // O3b: в БД лежат уроки только выбранной группы, поэтому расписание кабинета по всем
    // группам появится лишь после догрузки дня целиком. Делаем это один раз на сессию
    // экрана, не блокируя открытие диалога: сначала показываем что есть, потом дополняем.
    // Б8: догрузка дня по всем группам — отдельно для сегодня и для завтра
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

    // Активная дата диалога: завтра — когда переключатель в положении «Завтра»
    val dialogDateString = if (roomDialogTomorrow) tomorrowDateString else todayDateString
    val dialogDow = if (roomDialogTomorrow) tomorrowDow else todayDow

    LaunchedEffect(roomScheduleFor, roomDialogTomorrow, syncedToday, syncedTomorrow) {
        // W6: гейт каталога (catalogCodes из поиска групп удалён) — заменён на проверку
        // самого каталога репозитория: пустой = синк кабинета не имеет смысла
        if (roomScheduleFor == null || scheduleRepository.catalog().isEmpty()) {
            return@LaunchedEffect
        }
        val alreadySynced = if (roomDialogTomorrow) syncedTomorrow else syncedToday
        if (alreadySynced) return@LaunchedEffect
        // Б3: force=true — кабинет открывают вручную, и пользователь ждёт ПОЛНОЕ
        // расписание кабинета, а не «что успело попасть в базу». Если день уже разобран
        // по большинству групп, сеть не понадобится.
        runCatching { scheduleRepository.syncDayAllGroupsCached(dialogDateString, dialogDow, force = true) }
        roomReloadTick++ // перечитать Flow кабинета с догруженными группами
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
    // O3-b2: текущая минута тикает раз в минуту — метка «СЕЙЧАС» переезжает на новую пару без переоткрытия диалога
    var nowMinutes by remember {
        mutableStateOf(
            Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) },
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            nowMinutes =
                Calendar
                    .getInstance()
                    .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        }
    }
    // «СЕЙЧАС» — только для сегодняшнего дня: у завтра идущего урока быть не может
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

    // R1: режим маршрута. Запрос «показать маршрут» приходит nav-аргументами (E4):
    //  - route=true (виджет NowNext → MainActivity → buildMapRoute(showRoute=true));
    //  - непустой/пустой from (!= null): тап по кабинету в расписании, "" = от главного входа.
    // showRoute читается прямо из аргумента записи backstack'а (одноразовый VM-флаг удалён).
    // W36: глобальный тогл «Маршруты по карте» (настройки): выкл = кнопки кабинетов
    // открывают карту без построения пути (сам кабинет выбирается как раньше)
    // V21/V24: перечитываем на ON_RESUME — remember{} без ключа застревал навсегда, и тогл
    // из настроек применялся только к переоткрытой карте. routeActive — единственное
    // choke-point авто-маршрутов (V24): route и всё производное гейтятся им; ручной
    // RouteBuilder (V25) тоглом НЕ гейтится.
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
    val routeActive = routesEnabled && (showRoute || fromRoom != null)

    // R1: мультиэтажный маршрут (CollegeRouteFinder на реальных данных). Ключи — только
    // входы маршрута: пан/зум пересчёта не вызывают.
    val floors = remember(mapData) { CollegeMap.Floor.entries.associate { it.id to mapData.floor(it.id) } }
    val route =
        remember(routeActive, target, fromRoom, mapData, floors) {
            if (!routeActive || target.isBlank()) {
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
    // V25: ручной мультимаршрут (RouteBuilder) — НЕ гейтится map_routes_enabled.
    // Состояние живёт в записи backstack'а (rememberSaveable), как target/floorId.
    var builderActive by rememberSaveable { mutableStateOf(false) }
    var waypoints by rememberSaveable(stateSaver = waypointListSaver) { mutableStateOf(listOf<Waypoint>()) }
    var loopMode by rememberSaveable { mutableStateOf(false) }
    var stageIndex by rememberSaveable { mutableIntStateOf(-1) }
    var ordered by rememberSaveable { mutableStateOf(false) }
    var autoOrdering by remember { mutableStateOf(false) }
    // Закоммиченный маршрут («Завершить») — рисуется после закрытия конструктора,
    // пока новый кабинет (тап/поиск) его не заменит
    var manualStages by remember { mutableStateOf<List<RouteStage>?>(null) }
    // Кэш маршрутов пар: Дейкстра на пару считается один раз (его же ест автонавигация)
    val routeCache = remember { HashMap<Pair<String, String>, CollegeRouteFinder.MultiFloorRoute?>() }

    val builderLegs =
        remember(builderActive, waypoints, loopMode, mapData, floors) {
            if (!builderActive || waypoints.isEmpty()) {
                emptyList()
            } else {
                buildLegs(waypoints, loopMode, mapData, floors, routeCache)
            }
        }
    val builderStages = remember(builderLegs, mapData) { legsToStages(builderLegs, mapData) }

    // V25: недостижимая пара → тост «X недоступен из Y», точка уходит в конец списка
    LaunchedEffect(builderLegs) {
        if (!builderActive) return@LaunchedEffect
        val bad = builderLegs.firstOrNull { it.route == null } ?: return@LaunchedEffect
        Toast.makeText(context, "${bad.to.label} недоступен из ${bad.from.label}", Toast.LENGTH_SHORT).show()
        if (bad.isReturn) return@LaunchedEffect // возврат в старт — старт закреплён, не двигаем
        val idx = waypoints.indexOfFirst { it.key == bad.to.key }
        if (idx >= 0 && idx != waypoints.lastIndex) {
            waypoints = waypoints.toMutableList().also { it.add(it.removeAt(idx)) }
        }
    }

    // Этапы ручного маршрута замещают авто-маршрут (при правке и после «Завершить»)
    val activeStages: List<RouteStage>? =
        if (builderActive) builderStages.takeIf { it.isNotEmpty() } else manualStages

    // W39c: полилинии маршрута на ТЕКУЩЕМ этаже, ортогонализованные (диагонали → L-уголки
    // мимо комнат). Полилиний может быть несколько: сегмент «3-4» рвётся на переходе
    // по лестнице (подход у 335 / площадка 4-го этажа).
    val routePoints =
        remember(activeStages, route, floorId, floor) {
            if (activeStages != null) {
                activeStages
                    .filter { it.floorId == floorId }
                    .map { it.points }
                    .takeIf { it.isNotEmpty() }
            } else {
                route?.floorPaths
                    ?.filter { it.floorId == floorId }
                    // V23: ЕДИНСТВЕННОЕ применение simplifyRoute — чистит крюки после orthogonalize
                    ?.map { CollegeRouteFinder.simplifyRoute(CollegeRouteFinder.orthogonalize(it.points, floor.rooms)) }
                    ?.takeIf { it.isNotEmpty() }
            }
        }
    // V25: круговой маршрут — сдвиг полилиний текущего этажа в экранных dp (туда +3, обратно −3)
    val routeOffsetsDp =
        remember(activeStages, floorId, loopMode) {
            if (activeStages == null || !loopMode) {
                emptyList()
            } else {
                activeStages.filter { it.floorId == floorId }.map { if (it.isReturn) -3f else 3f }
            }
        }
    val routeFinishHere =
        remember(activeStages, route, floorId) {
            if (activeStages != null) activeStages.last().floorId == floorId else route?.floorPaths?.lastOrNull()?.floorId == floorId
        }
    // Плашка «Этаж N: подъем по лестнице у каб. X» — про подъём с ТЕКУЩЕГО этажа
    val routeBanner =
        remember(activeStages, route, floorId, mapData) {
            if (activeStages != null) return@remember null // конструктор: подписи живут в чипах этапов
            val fps = route?.takeIf { it.crossesFloors }?.floorPaths ?: return@remember null
            val i = fps.indexOfFirst { it.floorId == floorId }
            if (i < 0 || i >= fps.size - 1) return@remember null
            val stair = fps[i].points.lastOrNull() ?: return@remember null
            // V25: логика «ближайший кабинет» вынесена в nearestRoomNumber (общая с этапами)
            val near = nearestRoomNumber(mapData.floor(fps[i].floorId), stair.x, stair.y)
            val where = near?.let { " у каб. $it" } ?: ""
            "${CollegeMap.Floor.from(fps[i].floorId).title}: подъем по лестнице$where"
        }
    // Сегменты маршрута по этажам (точки-кнопки переключения)
    val routeSegments =
        remember(activeStages, route) {
            if (activeStages != null) return@remember emptyList() // конструктор: чипы этапов вместо точек
            route?.takeIf { it.crossesFloors }?.floorPaths?.map { it.floorId } ?: emptyList()
        }
    // V25: выбранный чип этапа. На одном плане бывает несколько этапов («3-4»), поэтому
    // индекс; если этаж сменили кнопками — подсвечиваем первый этап этого этажа.
    val selectedStage =
        if (activeStages == null) {
            -1
        } else if (stageIndex in activeStages.indices && activeStages[stageIndex].floorId == floorId) {
            stageIndex
        } else {
            activeStages.indexOfFirst { it.floorId == floorId }
        }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        MapHeader(
            subtitle =
            when {
                targetRoom != null -> "Кабинет ${targetRoom.number}"
                else -> "Планы этажей"
            },
            onBack = onBack,
        )

        Spacer(modifier = Modifier.height(4.dp))
        SearchField(
            value = query,
            onValueChange = {
                query = it
                searchFailed = false
            },
            onFound = { found ->
                manualStages = null // V25: новый кабинет замещает ручной маршрут
                target = found
                searchFailed = found.isNotBlank() && findRoomEverywhere(mapData, found) == null
            },
            notFound = searchFailed,
        )
        // W6: поиск по ГРУППЕ убран с карты (для этого есть вкладка «Расписание
        // группы» — заказчик). Освободившееся место отдаётся карте.

        // V25: вход в конструктор ручного маршрута — всегда виден, тогл маршрутов не проверяет
        Spacer(modifier = Modifier.height(6.dp))
        RouteBuilderEntry(active = builderActive) {
            builderActive = !builderActive
            if (builderActive) manualStages = null // возврат к правке — коммит убираем
        }

        Spacer(modifier = Modifier.height(10.dp))

        FloorRow(
            current = floorId,
            onSelect = { floorId = it },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (builderActive) {
            Spacer(modifier = Modifier.height(8.dp))
            BuilderAddChips(
                floorId = floorId,
                mapData = mapData,
                onAdd = { wp ->
                    // дубль подряд → пропуск
                    if (waypoints.lastOrNull()?.key != wp.key) waypoints = waypoints + wp
                },
            )
        }

        // V25: этапы маршрута — чипы над картой (тап = переход на этаж этапа)
        if (activeStages != null) {
            Spacer(modifier = Modifier.height(8.dp))
            StageChipsRow(
                stages = activeStages,
                selectedIndex = selectedStage,
                onSelect = { i ->
                    stageIndex = i
                    floorId = activeStages[i].floorId
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        MapCanvas(
            floorId = floorId,
            floor = floor,
            targetRoom = targetRoom,
            // O3-d: тап по кабинету — тот же путь, что ввод номера в поиск;
            // V25: в конструкторе тап ДОБАВЛЯЕТ точку маршрута (дубль подряд → пропуск)
            onRoomTap = { room ->
                if (builderActive) {
                    if (waypoints.lastOrNull()?.key != room.number) {
                        waypoints = waypoints + Waypoint(room.number, "каб. ${room.number}")
                    }
                } else {
                    manualStages = null // V25: новый кабинет замещает ручной маршрут
                    searchFailed = false
                    target = room.number
                }
            },
            routePoints = routePoints,
            routeOffsetsDp = routeOffsetsDp,
            routeFinishHere = routeFinishHere,
            routeBanner = routeBanner,
            routeSegments = routeSegments,
            onSelectRouteSegment = { floorId = it },
            showFinish = builderActive,
            onFinish = {
                // «Завершить» — БЕЗ подтверждения: коммитим этапы, цель = последняя точка
                manualStages = builderStages.takeIf { it.isNotEmpty() }
                builderActive = false
                builderStages.lastOrNull()?.let { floorId = it.floorId }
                waypoints.lastOrNull()?.takeIf { !it.isEntrance }?.let { target = it.key }
            },
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        if (builderActive) {
            RouteWaypointsPanel(
                waypoints = waypoints,
                autoOrdering = autoOrdering,
                ordered = ordered,
                loop = loopMode,
                onRemove = { i ->
                    waypoints = waypoints.filterIndexed { idx, _ -> idx != i }
                    ordered = false
                },
                onSwap = { a, b ->
                    waypoints =
                        waypoints.toMutableList().also {
                            val t = it[a]
                            it[a] = it[b]
                            it[b] = t
                        }
                },
                onAutoOrder = {
                    if (waypoints.size > 2 && !autoOrdering) {
                        autoOrdering = true
                        val wps = waypoints
                        scope.launch {
                            // greedy по кэшу дистанций; Дейкстра тяжёлая — вне main
                            val rest =
                                withContext(Dispatchers.Default) {
                                    greedyOrder(wps[0], wps.drop(1)) { a, b ->
                                        cachedRoute(a, b, mapData, floors, routeCache)?.distance ?: Float.MAX_VALUE
                                    }
                                }
                            waypoints = listOf(wps[0]) + rest
                            ordered = true
                            autoOrdering = false
                        }
                    }
                },
                onToggleLoop = { loopMode = !loopMode },
                onClear = {
                    waypoints = emptyList()
                    ordered = false
                    manualStages = null
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else if (targetRoom != null) {
            RoomPanel(
                room = targetRoom,
                onShowSchedule = { roomScheduleFor = targetRoom.number },
            )
        }
    }

    // Т6: расписание кабинета на сегодня
    roomScheduleFor?.let { roomNumber ->
        RoomScheduleDialog(
            roomNumber = roomNumber,
            // O3-b: в заголовке — сама дата и день недели (без версии APK из строкового ресурса)
            dateTitle = "$dialogDateString, ${roomDayTitle(dialogDow)}",
            lessons = filterRoomLessonsForDate(roomScheduleLessons, roomNumber, dialogDateString),
            currentLessonNumber = roomScheduleCurrentLesson,
            // Б8: заказчик — «нужно посмотреть на сегодня, так и на завтра»
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
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Выберите этаж или введите номер кабинета — покажем, где он находится.",
                    style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                    modifier = Modifier.padding(14.dp),
                )
            }
            return@Column
        }

        Surface(
            shape = RoundedCornerShape(3.dp),
            color = ColorBgMain,
            border = BorderStroke(1.dp, ColorBorderLight),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
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

        // Т6: подробнее — расписание кабинета на сегодня
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
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun MapHeader(
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
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
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Карта колледжа",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColorTextTitle),
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun findRoomEverywhere(
    mapData: CollegeMapData,
    number: String,
): MapRoom? = listOf("1", "2", "3-4").firstNotNullOfOrNull { mapData.floor(it).room(number) }

// V25: кнопка-вход в конструктор ручного маршрута (под SearchField, всегда видна)
@Composable
private fun RouteBuilderEntry(
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = if (active) ColorSurfaceVariantLight else ColorBrandFill,
        border = BorderStroke(1.dp, if (active) ColorBorderLight else ColorBrandFill),
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .bouncyClickable(onClick = onClick),
    ) {
        Text(
            text = if (active) "ЗАКРЫТЬ КОНСТРУКТОР МАРШРУТА" else "ПРОЛОЖИТЬ МАРШРУТ",
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (active) ColorTextBody else Color.White),
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
}

// День недели подписью — в заголовок диалога кабинета (dayOfWeek 1=Пн..7=Вс)
private fun roomDayTitle(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "понедельник"
    2 -> "вторник"
    3 -> "среда"
    4 -> "четверг"
    5 -> "пятница"
    6 -> "суббота"
    else -> "воскресенье"
}

// O3-d: тап по карте. Координаты нормализованы (0..1, как MapRoom.x/y) — на экране их
// получаем обратным преобразованием graphicsLayer: (screen - offset) / scale / размер картинки.
private const val TAP_SLOP_PX = 24f

// N11: запас панорамирования — по 25% размера вида с каждой стороны (~1.5× свободы),
// чтобы крайние кабинеты не упирались в край экрана и их не закрывали иконки зума.
private const val PAN_MARGIN = 0.25f

/**
 * O3-d: кабинет под точкой тапа. Сначала точное попадание в прямоугольник, затем — ближайший
 * кабинет в радиусе допуска (тап пальцем редко попадает в мелкий прямоугольник).
 */
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
    // точное попадание (если прямоугольников несколько — берём меньший, он «сверху»)
    rooms
        .filter { inside(it, 0f) }
        .minByOrNull { it.w * it.h }
        ?.let { return it }
    // промах пальцем: ближайший кабинет в радиусе допуска
    return rooms
        .filter { inside(it, slopNorm) }
        .minByOrNull { r -> (xNorm - r.x) * (xNorm - r.x) + (yNorm - r.y) * (yNorm - r.y) }
}

/**
 * MAP-02: чистая функция hit-test тапа. Принимает ЦЕЛЕВЫЕ (покойные) значения зума/смещения,
 * а не анимируемые `.value`, — поэтому клик попадает даже во время зум-анимации. Нормализованные
 * координаты: (pos - offset) / scale / размер_картинки, как в обратном преобразовании graphicsLayer.
 */
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

// Т5: где группа сейчас. isNow=false → следующая пара («к N уроку»)
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

    // Текущий слот по звонкам (dayOfWeek — 1..7, как в ScheduleScreen)
    val slot =
        CollegeBellSchedule
            .getBellsForDay(dayOfWeek)
            .firstOrNull { currentMinutes in it.startMinutes..it.endMinutes }
            ?.takeIf { it.lessonNumber > 0 }

    val current = slot?.let { s -> withRoom.firstOrNull { it.lessonNumber == s.lessonNumber } }
    if (current != null) {
        return GroupRoomResult(
            // ponytail: подгруппу split-пары не определяем — берём roomFirst (потолок: не в 2 кабинетах сразу)
            room = current.roomFirst.ifBlank { current.roomSecond },
            lessonNumber = current.lessonNumber,
            isNow = true,
        )
    }

    // Следующая пара: по звонкам, фолбэк — timeStart строки
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
    onFound: (String) -> Unit,
    notFound: Boolean,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = if (notFound) Color(0xFFFDECEC) else ColorSurfaceVariantLight,
            border =
            BorderStroke(
                if (notFound) 2.dp else 1.dp,
                if (notFound) DEAD_COLOR else ColorBorderLight,
            ),
            modifier =
            Modifier
                .weight(1f)
                .height(46.dp),
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
                    // M6: крестик 18dp в зоне 48dp
                    Box(
                        modifier =
                        Modifier
                            .size(48.dp)
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

        Surface(
            shape = RoundedCornerShape(3.dp),
            color = ColorBrandFill,
            modifier =
            Modifier
                .height(46.dp)
                .bouncyClickable { if (value.isNotBlank()) onFound(value.trim()) },
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
}

// Т5: поиск по группе. Запрашивает сегодняшие пары, считает «где группа сейчас»
// и открывает карту на этом кабинете; результат — либо кабинет, либо сообщение.
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
                shape = RoundedCornerShape(3.dp),
                color = if (selected) ColorBrandFill else ColorBgMain,
                border = BorderStroke(1.dp, if (selected) ColorBrandFill else ColorBorderLight),
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
    // O3-d: тап по кабинету = тот же выбор, что ввод в поиск
    onRoomTap: (MapRoom) -> Unit = {},
    // R1: путь маршрута на ТЕКУЩЕМ этаже (null — этаж без пути), пульс точки финиша,
    // плашка подъёма по лестнице и сегменты-этажи (точки-кнопки переключения).
    // W39c: полилинии текущего этажа (сегмент «3-4» рвётся на переходе по лестнице)
    routePoints: List<List<MapPoint>>? = null,
    routeFinishHere: Boolean = false,
    routeBanner: String? = null,
    routeSegments: List<String> = emptyList(),
    onSelectRouteSegment: (String) -> Unit = {},
    // V25: сдвиг каждой полилинии в экранных dp (круговой маршрут: туда +3, обратно −3)
    // и кнопка «Завершить» поверх карты (конструктор маршрута)
    routeOffsetsDp: List<Float> = emptyList(),
    showFinish: Boolean = false,
    onFinish: () -> Unit = {},
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

    // V22: фаза «бегущих» стрелок — сетка стрелок течёт вдоль маршрута (цикл 1400 мс)
    val arrowPhase by rememberInfiniteTransition(label = "стрелки").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "arrowPhase",
    )

    BoxWithConstraints(
        modifier =
        modifier
            // N9: фон карты однороден с фоном вкладки (раньше Color(0xFFEDF2F7) отличался от ColorBgMain)
            .clipToBounds()
            .background(ColorBgMain),
    ) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val imgW = viewW
        val imgH = imgW / aspect

        // MAP-03: свежие размеры — через State, чтобы детекторы жестов НЕ держали их в ключах
        // pointerInput (иначе смена высоты при показе RoomPanel перезапускала детектор и тап терялся).
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
                                color = Color(0xFF0B3564),
                            ),
                            maxLines = 1,
                        )
                }
            }

        // MAP-03/N11: читаем размеры через State — функции захватываются детекторами жестов,
        // которые больше не перезапускаются при смене размера (значит замыкание должно быть свежим).
        // N11: pannable-область расширена на margin с каждой стороны (~1.5× свободы).
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

        LaunchedEffect(floorId, targetRoom?.id, viewW, viewH) {
            if (viewW <= 0f || viewH <= 0f) return@LaunchedEffect
            val rest = restZoom()
            val room = targetRoom
            if (room == null) {
                scope.launch { scale.animateTo(rest, tween(250)) }
                scope.launch { offsetX.animateTo(clampX(viewW / 2f - imgW * rest / 2f, rest), tween(250)) }
                scope.launch { offsetY.animateTo(clampY(viewH / 2f - imgH * rest / 2f, rest), tween(250)) }
                return@LaunchedEffect
            }
            val px = room.x * imgW
            val py = room.y * imgH
            val zoom = 2.6f
            // N10: тап по кабинету приближает к нему и ОСТАЁТСЯ приближённым (убран отъезд обратно)
            scope.launch { scale.animateTo(zoom, tween(420, easing = LinearOutSlowInEasing)) }
            scope.launch { offsetX.animateTo(clampX(viewW / 2f - px * zoom, zoom), tween(420, easing = LinearOutSlowInEasing)) }
            scope.launch { offsetY.animateTo(clampY(viewH / 2f - py * zoom, zoom), tween(420, easing = LinearOutSlowInEasing)) }
        }

        Box(
            modifier =
            Modifier
                .fillMaxSize()
                // O3-d: тап рядом с жестами (не взамен) — иначе сломается зум/пан.
                // detectTapGestures ждёт проигрыша соседних детекторов, поэтому пан/зум не страдают.
                // MAP-03: viewW/viewH убраны из ключей — детектор НЕ перезапускается при показе
                // RoomPanel (смена высоты), тап больше не теряется. Размеры читаем через State внутри.
                .pointerInput(floorId) {
                    detectTapGestures { pos ->
                        // MAP-02: hit-test по ЦЕЛЕВЫМ (покойным) значениям зума/смещения, а не
                        // анимируемым `.value` — клик попадает даже во время зум-анимации.
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
                    // R1: маршрут поверх плана; толщины/размеры компенсируют зум (экранные).
                    // W39c: полилиний может быть несколько (разрыв на лестнице внутри этажа 3-4)
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
                            arrowPhase = arrowPhase,
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
            fun zoomTo(z: Float) {
                val target = z.coerceIn(restZoom(), 8f)
                val cx = viewW / 2f
                val cy = viewH / 2f
                val bx = (cx - offsetX.value) / scale.value
                val by = (cy - offsetY.value) / scale.value
                scope.launch {
                    scale.animateTo(target, tween(220))
                }
                scope.launch {
                    offsetX.animateTo(clampX(cx - bx * target, target), tween(220))
                }
                scope.launch {
                    offsetY.animateTo(clampY(cy - by * target, target), tween(220))
                }
            }

            ZoomButton(Icons.Default.Add) { zoomTo(scale.value * 1.5f) }
            ZoomButton(Icons.Default.Remove) { zoomTo(scale.value / 1.5f) }
            ZoomButton(Icons.Default.ZoomOutMap) {
                scope.launch {
                    val z = restZoom()
                    scale.animateTo(z, tween(250))
                    offsetX.animateTo(clampX((viewW - imgW * z) / 2f, z), tween(250))
                    offsetY.animateTo(clampY((viewH - imgH * z) / 2f, z), tween(250))
                }
            }
        }

        // V25: «Завершить» — коммит ручного маршрута БЕЗ подтверждения (видимые 32dp,
        // touch-зона 44dp по M2)
        if (showFinish) {
            Box(
                modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(44.dp)
                    .bouncyClickable { onFinish() },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = ColorBrandFill,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Завершить маршрут",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // R1: межэтажный маршрут — плашка «Этаж N: подъем по лестнице у каб. X»
        // и точки-кнопки сегментов (переключение этажа по сегментам пути)
        if (routeBanner != null || routeSegments.isNotEmpty()) {
            Column(
                modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                routeBanner?.let { text ->
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = ColorBrandFill,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = text,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                if (routeSegments.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        routeSegments.forEach { fid ->
                            val selected = fid == floorId
                            Surface(
                                shape = CircleShape,
                                color = if (selected) ColorBrandFill else ColorBgMain,
                                border =
                                BorderStroke(
                                    1.dp,
                                    if (selected) ColorBrandFill else ColorBorderLight,
                                ),
                                modifier =
                                Modifier
                                    .size(32.dp)
                                    .bouncyClickable { onSelectRouteSegment(fid) },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = fid,
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
    }
}

@Composable
private fun ZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier =
        Modifier
            // M2: зона 48dp, видимый квадрат 42dp — кнопки-«подушки» растут наружу
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

    // Кабинеты: единый масштаб цифр в пределах этажа + белый ореол (читаемость поверх стен)
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

    // C1: минимум читаемости цифры (в экранных px изображения). Ниже — номер не рисуем
    // вовсе: лучше пусто, чем каша за рамкой. Раньше этот порог наоборот РАЗДУВАЛ цифру.
    val minNumberPx = minScreenPxPerDp * 10f

    floor.rooms.forEach { r ->
        val layout = numberLayouts[r.id] ?: return@forEach
        if (r.number.isBlank()) return@forEach
        val raw = rawFits[r.id] ?: return@forEach

        // C1: единый размер по этажу, никогда не больше влезающего (raw).
        // Раньше loFit = maxOf(median*0.70, minFit) мог превышать raw, и coerceIn
        // принудительно раздувал цифру — она вылезала за рамку кабинета.
        val fit =
            roomNumberFit(
                raw = raw,
                median = medianFit,
                minReadable = minNumberPx / layout.size.height.toFloat(),
            ) ?: return@forEach

        val cx = r.x * imgW
        val cy = r.y * imgH
        val vertical = r.h > r.w * 1.6f
        val color = if (r.status == "nonexistent") DEAD_COLOR else Color(0xFF0B3564)

        withTransform({
            if (vertical) rotate(90f, Offset(cx, cy))
            scale(fit, fit, Offset(cx, cy))
        }) {
            val topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f)
            // O3-a: ореол тонкий (0.14 вместо 0.22) и совсем отключается у самых мелких цифр —
            // иначе он съедает просветы между штрихами, и номер выглядит «жирным курсивом»
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

/**
 * C1: размер номера кабинета.
 *
 * Заказчик: цифры были разнокалиберные и вылезали за рамки кабинетов, а после первой
 * правки — пропали почти везде. Номер обязан быть ВИДЕН всегда: пустой кабинет хуже
 * мелкой цифры, поэтому null здесь только при нулевом запасе места.
 *
 * Правила:
 *  - [raw] — максимальный размер, при котором цифра ВЛЕЗАЕТ в кабинет (жёсткий потолок);
 *  - база — [median] (единый размер по этажу), но не больше [raw] — отсюда единообразие;
 *  - крупные кабинеты получают «немножечко больше» (до 115% медианы, всё ещё в пределах raw);
 *  - мелкие — ужимаются ровно до [raw], оставаясь видимыми;
 *  - [minReadable] — желаемый минимум, но он НИКОГДА не поднимает размер выше [raw]
 *    (иначе цифра вылезала бы за рамку — исходный баг).
 */
internal fun roomNumberFit(
    raw: Float,
    median: Float,
    minReadable: Float,
): Float? {
    if (raw <= 0f) return null
    val base = minOf(median * 1.15f, raw)
    // Минимум читаемости поднимает мелкую цифру, но строго в пределах влезающего размера
    return minOf(maxOf(base, minReadable), raw)
}

/**
 * R1: старт маршрута без fromRoom — главный вход 1 этажа (entrance kind="main",
 * координаты 0.481/0.203). Псевдо-кабинет "100": CollegeMap.floorForRoom("100")
 * даёт этаж 1, как требует findMultiFloorRoute.
 */
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

/** Шаг стрелок направления вдоль пути, в нормализованных единицах плана. */
private const val ROUTE_ARROW_STEP_NORM = 0.06f

/**
 * R1: линия маршрута поверх плана. Всё рисуется внутри graphicsLayer (план масштабирован),
 * поэтому толщины/радиусы умножаются на 1/zoom — на экране они постоянны (~4dp линия).
 * W39c: [polylines] — уже ортогонализованные линии CollegeRouteFinder.orthogonalize
 * (диагонали заменены L-уголками мимо комнат); полилиний несколько, когда сегмент этажа
 * разорван переходом по лестнице. Стрелки-треугольники направления — каждые
 * [ROUTE_ARROW_STEP_NORM] нормализованных единиц; пульсирующая точка — на финише
 * (когда текущий этаж несёт конец маршрута).
 * V22: [arrowPhase] (0..1) — сетка стрелок течёт вдоль обхода пути (бегущие стрелки).
 * V25: [offsetsDp] — сдвиг каждой полилинии перпендикулярно ходу в ЭКРАННЫХ dp
 * (круговой маршрут: туда +3, обратно −3); сдвиг через offsetPolyline, стрелки — по
 * сдвинутым точкам.
 */
private fun DrawScope.drawRoutePath(
    polylines: List<List<MapPoint>>,
    imgW: Float,
    imgH: Float,
    zoom: Float,
    pxPerDp: Float,
    pulse: Float,
    drawFinish: Boolean,
    arrowPhase: Float = 0f,
    offsetsDp: List<Float> = emptyList(),
) {
    if (imgW <= 0f || imgH <= 0f) return
    val unit = 1f / zoom
    val color = ColorBrandBlue
    var finish: MapPoint? = null

    for ((pi, raw) in polylines.withIndex()) {
        if (raw.size < 2) continue
        // экранные dp → px плана: на экране сдвиг постоянен при любом зуме
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
            style = Stroke(width = 4f * pxPerDp * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Стрелки направления: равномерный шаг в нормализованных единицах, «перенос» остатка
        // между сегментами, угол — по экранным осям (план анизотропен: imgW != imgH).
        // V22: вся сетка сдвинута на phase*STEP — за цикл фазы стрелки проходят один шаг,
        // после Restart сетка совпадает сама с собой → непрерывное течение вдоль пути.
        val s = 4.5f * pxPerDp * unit
        val shift = arrowPhase * ROUTE_ARROW_STEP_NORM
        var carry = 0f
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val nLen = hypot(dx, dy)
            if (nLen < 1e-5f) continue
            val angle = atan2(dy * imgH, dx * imgW)
            var d = (ROUTE_ARROW_STEP_NORM - carry + shift) % ROUTE_ARROW_STEP_NORM
            if (d <= 0f) d += ROUTE_ARROW_STEP_NORM
            while (d <= nLen) {
                val t = d / nLen
                drawArrowHead(
                    center = Offset((a.x + dx * t) * imgW, (a.y + dy * t) * imgH),
                    angle = angle,
                    size = s,
                    color = color,
                )
                d += ROUTE_ARROW_STEP_NORM
            }
            carry = (carry + nLen) % ROUTE_ARROW_STEP_NORM
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

/** Треугольник-стрелка: вершина в направлении [angle] (рад), размер — экранный (уже с unit). */
private fun DrawScope.drawArrowHead(
    center: Offset,
    angle: Float,
    size: Float,
    color: Color,
) {
    val back = angle + PI.toFloat()
    val tip = Offset(center.x + cos(angle) * size, center.y + sin(angle) * size)
    val left = Offset(center.x + cos(back + 0.5f) * size, center.y + sin(back + 0.5f) * size)
    val right = Offset(center.x + cos(back - 0.5f) * size, center.y + sin(back - 0.5f) * size)
    drawPath(
        path =
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(left.x, left.y)
            lineTo(right.x, right.y)
            close()
        },
        color = color,
    )
}

private fun mapRes(floorId: String): Int = when (floorId) {
    "2" -> com.politex.R.drawable.map_floor2
    "3-4" -> com.politex.R.drawable.map_floor34
    else -> com.politex.R.drawable.map_floor1
}
