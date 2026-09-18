package com.example.ui.screens

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CollegeMap
import com.example.data.model.CollegeMapData
import com.example.data.model.CollegeRouteFinder
import com.example.data.model.FloorMap
import com.example.data.model.MapEntrance
import com.example.data.model.MapPoint
import com.example.data.model.MapRoom
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorSurfaceHighlight
import com.example.ui.theme.ColorSurfaceVariantLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.util.bouncyClickable
import kotlinx.coroutines.launch

private val ROUTE_COLOR = Color(0xFF0072CE)
private val ROUTE_OUTLINE = Color(0xFFFFFFFF)
private val ROOM_FILL = Color(0x331976D2)
private val ROOM_BORDER = Color(0xFF0057A8)
private val DEAD_COLOR = Color(0xFFC0392B)
private val START_COLOR = Color(0xFF16A34A)

/**
 * Карта колледжа: план этажа, подсветка кабинета и маршрут.
 *
 * Открывается двумя путями:
 * - **из расписания** по нажатию на кабинет, вместе с кабинетом текущего урока —
 *   тогда рисуется маршрут от него до нужного кабинета;
 * - **из меню «Другое»** — без маршрута: кабинет просто подсвечивается,
 *   чтобы можно было найти его на плане.
 *
 * Разметку готовит заказчик в `tools/editor.html`, приложение читает
 * `assets/college_map.json`.
 */
@Composable
fun CollegeMapScreen(
    initialRoom: String?,
    onBack: () -> Unit,
    /** Кабинет, от которого строить маршрут (текущий урок). Без него маршрута нет. */
    fromRoom: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapData = remember { CollegeMapData.load(context) }

    val cameFromSchedule = !initialRoom.isNullOrBlank()
    var query by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(initialRoom?.trim().orEmpty()) }
    var floorId by remember {
        mutableStateOf(CollegeMap.floorForRoom(initialRoom.orEmpty())?.id ?: "1")
    }

    val floor: FloorMap = remember(floorId, mapData) { mapData.floor(floorId) }
    val rooms = remember(floor) { floor.rooms.filter { it.status != "nonexistent" } }
    val targetRoom = remember(floor, target) { floor.room(target) }

    // Маршрут строим только когда есть откуда — то есть пришли из расписания
    val route = remember(floor, targetRoom, fromRoom) {
        val start = fromRoom?.let { floor.room(it) }
        if (start == null || targetRoom == null || start.id == targetRoom.id) null
        else CollegeRouteFinder.findRoute(start, targetRoom, floor)
    }
    val startRoom = remember(floor, fromRoom) { fromRoom?.let { floor.room(it) } }

    LaunchedEffect(target) {
        CollegeMap.floorForRoom(target)?.let { floorId = it.id }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        MapHeader(
            subtitle = when {
                route != null && startRoom != null -> "Маршрут: ${startRoom.number} → ${targetRoom?.number}"
                targetRoom != null -> "Кабинет ${targetRoom.number}"
                else -> "Планы этажей"
            },
            onBack = onBack
        )

        if (!cameFromSchedule) {
            Spacer(modifier = Modifier.height(4.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                onFound = { found ->
                    target = found
                    found.toIntOrNull()?.let { }
                },
                knownRooms = remember(mapData) {
                    buildList {
                        listOf("1", "2", "3-4").forEach { f -> addAll(mapData.floor(f).rooms.map { it.number }) }
                    }.filter { it.isNotBlank() }
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        FloorRow(
            current = floorId,
            onSelect = { floorId = it },
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Карта занимает всё свободное место — это «полотно», как в навигаторе.
        // План лежит по центру, вокруг него фон, чтобы пустота читалась
        // как поле карты, а не как недоделанный экран.
        MapCanvas(
            floorId = floorId,
            floor = floor,
            targetRoom = targetRoom,
            routePoints = route?.points,
            startRoom = startRoom,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        RoomPanel(
            room = targetRoom,
            startRoom = startRoom,
            route = route,
            hasRouteGraph = floor.corridors.isNotEmpty() || floor.nodes.isNotEmpty()
        )
    }
}

/**
 * Карточка под планом: что за кабинет, где он и как до него дойти.
 * Заполняет место, которое иначе оставалось пустым.
 */
@Composable
private fun RoomPanel(
    room: MapRoom?,
    startRoom: MapRoom?,
    route: CollegeRouteFinder.Route?,
    hasRouteGraph: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (room == null) {
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Выберите этаж или введите номер кабинета — покажем, где он находится.",
                    style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                    modifier = Modifier.padding(14.dp)
                )
            }
            return@Column
        }

        Surface(
            shape = RoundedCornerShape(3.dp),
            color = ColorBgMain,
            border = BorderStroke(1.dp, ColorBorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = ColorBrandFill,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = room.number,
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color.White
                            ),
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.title.ifBlank { "Кабинет ${room.number}" },
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle),
                        maxLines = 2
                    )
                    val sub = when (room.status) {
                        "nonexistent" -> "Кабинета нет или в него не попасть"
                        "closed" -> "Закрыт"
                        "service" -> "Служебное помещение"
                        else -> statusText(route, startRoom, room, hasRouteGraph)
                    }
                    Text(
                        text = sub,
                        style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                        maxLines = 2
                    )
                }
            }
        }

        if (room.note.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = room.note,
                    style = TextStyle(fontSize = 13.sp, color = ColorTextBody),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

private fun statusText(
    route: CollegeRouteFinder.Route?,
    startRoom: MapRoom?,
    room: MapRoom,
    hasRouteGraph: Boolean
): String = when {
    route != null && startRoom != null -> {
        val steps = (route.points.size - 2).coerceAtLeast(0)
        "Маршрут от кабинета ${startRoom.number} — по коридорам, поворотов: $steps"
    }
    startRoom != null && startRoom.id == room.id -> "Вы здесь — это ваш текущий кабинет"
    hasRouteGraph -> "Путь по коридорам не проложен — кабинет подсвечен на плане"
    else -> ""
}

// ---------------------------------------------------------------------------
//  Шапка, поиск, этажи, подвал
// ---------------------------------------------------------------------------

@Composable
private fun MapHeader(subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .bouncyClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = ColorBrandBlue,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Карта колледжа",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColorTextTitle),
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Поле поиска кабинета. Своя реализация вместо стандартного поля: у того
 * высота и ширина зависят от содержимого, из-за чего вёрстка разъезжалась.
 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onFound: (String) -> Unit,
    knownRooms: List<String>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = ColorSurfaceVariantLight,
            border = BorderStroke(1.dp, ColorBorderLight),
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = ColorTextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(
                            text = "Номер кабинета, например 214",
                            style = TextStyle(fontSize = 14.sp, color = ColorTextMuted),
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { input ->
                            onValueChange(input.filter { it.isDigit() || it.isLetter() }.take(6))
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 15.sp, color = ColorTextTitle),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (value.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Очистить",
                        tint = ColorTextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .bouncyClickable { onValueChange("") }
                    )
                }
            }
        }

        // ВАЖНО: без fillMaxSize внутри. В строке без ограничения ширины
        // fillMaxSize растягивает кнопку на весь экран и выдавливает поле
        // поиска в ноль — ровно из-за этого вёрстка и разваливалась.
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = ColorBrandFill,
            modifier = Modifier
                .height(46.dp)
                .bouncyClickable { if (value.isNotBlank()) onFound(value.trim()) }
        ) {
            Text(
                text = "НАЙТИ",
                softWrap = false,
                maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun FloorRow(current: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CollegeMap.Floor.entries.forEach { f ->
            val selected = current == f.id
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = if (selected) ColorBrandFill else ColorBgMain,
                border = BorderStroke(1.dp, if (selected) ColorBrandFill else ColorBorderLight),
                modifier = Modifier
                    .weight(1f)
                    .bouncyClickable { onSelect(f.id) }
            ) {
                Text(
                    text = f.title,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (selected) Color.White else ColorTextBody
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  План с подсветкой и маршрутом
// ---------------------------------------------------------------------------

@Composable
private fun MapCanvas(
    floorId: String,
    floor: FloorMap,
    targetRoom: MapRoom?,
    routePoints: List<MapPoint>?,
    startRoom: MapRoom?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val painter = painterResource(id = mapRes(floorId))
    val aspect = painter.intrinsicSize.let { s ->
        if (s.height > 0f && s.width > 0f) s.width / s.height else 1.7917f
    }

    // Мягкая пульсация подсветки — видно, куда смотреть, но не мешает
    val pulse by rememberInfiniteTransition(label = "подсветка").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "пульс"
    )

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFFEDF2F7))
    ) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val imgW = viewW
        val imgH = imgW / aspect

    // Номера кабинетов рисуем поверх плана: на новых планах их нет.
    //
    // ВАЖНО: размеры считаем в координатах ПОЛОТНА (imgW x imgH), а не исходной
    // картинки. Раньше здесь стояли 1376 и 768 — номера выходили вчетверо
    // крупнее кабинета и налезали друг на друга.
    val measurer = rememberTextMeasurer()
    val numberLayouts: Map<String, TextLayoutResult> = remember(floorId, floor, imgW, imgH) {
        floor.rooms.filter { it.number.isNotBlank() }.associate { r ->
            val wPx = r.w * imgW
            val hPx = r.h * imgH
            val fontPx = (minOf(hPx * 0.34f, wPx * 0.24f)).coerceIn(7f, 24f)
            r.id to measurer.measure(
                text = r.number,
                style = TextStyle(
                    fontSize = with(density) { fontPx.toSp() },
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0B3564)
                ),
                maxLines = 1
            )
        }
    }


        fun clampX(v: Float, s: Float): Float {
            val c = imgW * s
            return if (c <= viewW) (viewW - c) / 2f else v.coerceIn(viewW - c, 0f)
        }
        fun clampY(v: Float, s: Float): Float {
            val c = imgH * s
            return if (c <= viewH) (viewH - c) / 2f else v.coerceIn(viewH - c, 0f)
        }

        // Спокойный масштаб: план широкий, а экран высокий, поэтому при «весь
        // план целиком» он выглядит мелким. Подбираем так, чтобы он занимал
        // заметную часть полотна; больше 2.2 не приближаем, иначе теряется
        // понимание, где кабинет относительно всего этажа.
        fun restZoom(): Float =
            (viewH / imgH * 0.72f).coerceIn(1f, 2.2f)

        // Наезд на кабинет и отъезд к плану целиком
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
            scope.launch { scale.animateTo(zoom, tween(420, easing = LinearOutSlowInEasing)) }
            scope.launch { offsetX.animateTo(clampX(viewW / 2f - px * zoom, zoom), tween(420, easing = LinearOutSlowInEasing)) }
            scope.launch { offsetY.animateTo(clampY(viewH / 2f - py * zoom, zoom), tween(420, easing = LinearOutSlowInEasing)) }
            kotlinx.coroutines.delay(600)
            scope.launch { scale.animateTo(rest, tween(540)) }
            scope.launch { offsetX.animateTo(clampX(viewW / 2f - imgW * rest / 2f, rest), tween(540)) }
            scope.launch { offsetY.animateTo(clampY(viewH / 2f - imgH * rest / 2f, rest), tween(540)) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewW, viewH) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scope.launch {
                            val next = (scale.value * zoom).coerceIn(0.8f, 8f)
                            scale.snapTo(next)
                            offsetX.snapTo(clampX(offsetX.value + pan.x, next))
                            offsetY.snapTo(clampY(offsetY.value + pan.y, next))
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        translationX = offsetX.value
                        translationY = offsetY.value
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                Image(
                    painter = painter,
                    contentDescription = "План этажа",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(with(density) { imgW.toDp() })
                        .height(with(density) { imgH.toDp() })
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val k = scale.value           // чтобы толщины не зависели от зума
                    drawMap(
                        floor = floor,
                        imgW = imgW,
                        imgH = imgH,
                        targetRoom = targetRoom,
                        startRoom = startRoom,
                        routePoints = routePoints,
                        pulse = pulse,
                        zoom = k,
                        numberLayouts = numberLayouts,
                        minScreenPxPerDp = with(density) { 1.dp.toPx() }
                    )
                }
            }
        }

        // Кнопки зума — поверх полотна, в правом нижнем углу
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ZoomButton("+") {
                scope.launch {
                    val z = (scale.value * 1.4f).coerceAtMost(8f)
                    scale.animateTo(z, tween(200))
                    offsetX.snapTo(clampX(offsetX.value, z))
                    offsetY.snapTo(clampY(offsetY.value, z))
                }
            }
            ZoomButton("−") {
                scope.launch {
                    val z = (scale.value / 1.4f).coerceAtLeast(restZoom())
                    scale.animateTo(z, tween(200))
                    offsetX.snapTo(clampX(offsetX.value, z))
                    offsetY.snapTo(clampY(offsetY.value, z))
                }
            }
            ZoomButton("⤢") {
                scope.launch {
                    val z = restZoom()
                    scale.animateTo(z, tween(250))
                    offsetX.animateTo(clampX((viewW - imgW * z) / 2f, z), tween(250))
                    offsetY.animateTo(clampY((viewH - imgH * z) / 2f, z), tween(250))
                }
            }
        }
    }
}

/** Круглая кнопка зума поверх плана. */
@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier
            .size(38.dp)
            .bouncyClickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ColorBrandBlue)
            )
        }
    }
}

/** Вся отрисовка разметки: кабинет, маршрут, входы, подписи. */
private fun DrawScope.drawMap(
    floor: FloorMap,
    imgW: Float,
    imgH: Float,
    targetRoom: MapRoom?,
    startRoom: MapRoom?,
    routePoints: List<MapPoint>?,
    pulse: Float,
    zoom: Float,
    numberLayouts: Map<String, TextLayoutResult>,
    /** Сколько пикселей в одном dp — чтобы задать порог «кабинет мелкий» в dp. */
    minScreenPxPerDp: Float
) {
    val unit = 1f / zoom     // компенсация зума, чтобы линии на экране не толстели

    // --- Маршрут: сначала белая подложка, потом цветная линия ---
    if (!routePoints.isNullOrEmpty()) {
        val path = Path().apply {
            moveTo(routePoints[0].x * imgW, routePoints[0].y * imgH)
            routePoints.drop(1).forEach { lineTo(it.x * imgW, it.y * imgH) }
        }
        drawPath(path, ROUTE_OUTLINE, style = Stroke(9f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, ROUTE_COLOR, style = Stroke(4.5f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // --- Конец маршрута: подсвеченный кабинет ---
    if (targetRoom != null) {
        val x = (targetRoom.x - targetRoom.w / 2f) * imgW
        val y = (targetRoom.y - targetRoom.h / 2f) * imgH
        val w = targetRoom.w * imgW
        val h = targetRoom.h * imgH
        val corner = 5f * unit

        // Мягкое свечение вокруг
        drawRoundRect(
            color = ROUTE_COLOR.copy(alpha = 0.16f * pulse),
            topLeft = Offset(x - 5f * unit, y - 5f * unit),
            size = Size(w + 10f * unit, h + 10f * unit),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner + 3f * unit)
        )
        drawRoundRect(
            color = ROOM_FILL,
            topLeft = Offset(x, y),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner)
        )
        drawRoundRect(
            color = if (targetRoom.status == "nonexistent") DEAD_COLOR else ROOM_BORDER,
            topLeft = Offset(x, y),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
            style = Stroke(2.5f * unit)
        )
    }

    // --- Номера кабинетов ---
    // Показываем только те, что влезают в кабинет на экране: иначе при мелком
    // масштабе шесть десятков номеров налезают друг на друга в кашу.
    // Приблизил — номеров становится больше.
    val minRoomPx = 22f * minScreenPxPerDp
    floor.rooms.forEach { r ->
        val layout = numberLayouts[r.id] ?: return@forEach
        if (r.w * imgW * zoom < minRoomPx) return@forEach
        val cx = r.x * imgW
        val cy = r.y * imgH
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                cx - layout.size.width / 2f,
                cy - layout.size.height / 2f
            ),
            color = if (r.status == "nonexistent") DEAD_COLOR else Color(0xFF0B3564)
        )
    }

    // --- Начало маршрута: зелёная точка ---
    if (startRoom != null && routePoints != null) {
        val cx = startRoom.x * imgW
        val cy = startRoom.y * imgH
        drawCircle(ROUTE_OUTLINE, 8f * unit, Offset(cx, cy))
        drawCircle(START_COLOR, 5f * unit, Offset(cx, cy))
    }

    // --- Входы: стрелки ---
    floor.entrances.forEach { e -> drawEntrance(e, imgW, imgH, unit) }
}

private fun DrawScope.drawEntrance(e: MapEntrance, imgW: Float, imgH: Float, unit: Float) {
    val color = when (e.kind) {
        "main" -> Color(0xFF0D9488)
        "stairs" -> Color(0xFF7C3AED)
        else -> Color(0xFF0EA5E9)
    }
    val x = e.x * imgW
    val y = e.y * imgH
    val a = Math.toRadians(e.dir.toDouble())
    val c = Math.cos(a).toFloat()
    val s = Math.sin(a).toFloat()
    val len = 16f * unit
    val tip = Offset(x + c * len, y + s * len)
    val tail = Offset(x - c * len, y - s * len)
    val head = Offset(tip.x - c * 8f * unit, tip.y - s * 8f * unit)
    val hw = 5f * unit

    drawLine(color, tail, head, strokeWidth = 3.5f * unit, cap = StrokeCap.Round)
    drawPath(
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(head.x - s * hw, head.y + c * hw)
            lineTo(head.x + s * hw, head.y - c * hw)
            close()
        },
        color
    )
}

private fun mapRes(floorId: String): Int = when (floorId) {
    "2" -> com.example.R.drawable.map_floor2
    "3-4" -> com.example.R.drawable.map_floor34
    else -> com.example.R.drawable.map_floor1
}
