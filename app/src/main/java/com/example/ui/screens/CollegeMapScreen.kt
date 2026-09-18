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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomOutMap
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
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

@Composable
fun CollegeMapScreen(
    initialRoom: String?,
    onBack: () -> Unit,

    fromRoom: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapData = remember { CollegeMapData.load(context) }

    val cameFromSchedule = !initialRoom.isNullOrBlank()
    var query by remember { mutableStateOf("") }

    var searchFailed by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(initialRoom?.trim().orEmpty()) }
    var floorId by remember {
        mutableStateOf(CollegeMap.floorForRoom(initialRoom.orEmpty())?.id ?: "1")
    }

    val floor: FloorMap = remember(floorId, mapData) { mapData.floor(floorId) }
    val rooms = remember(floor) { floor.rooms.filter { it.status != "nonexistent" } }
    val targetRoom = remember(floor, target) { floor.room(target) }

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
                onValueChange = { query = it; searchFailed = false },
                onFound = { found ->
                    target = found
                    searchFailed = found.isNotBlank() && findRoomEverywhere(mapData, found) == null
                },
                notFound = searchFailed
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        FloorRow(
            current = floorId,
            onSelect = { floorId = it },
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

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

        if (targetRoom != null) {
            RoomPanel(
                room = targetRoom,
                startRoom = startRoom,
                route = route,
                hasRouteGraph = floor.corridors.isNotEmpty() || floor.nodes.isNotEmpty()
            )
        }
    }
}

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
                    val sub = room.title.ifBlank { "" }
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                            maxLines = 2
                        )
                    }
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

private fun findRoomEverywhere(mapData: CollegeMapData, number: String): MapRoom? =
    listOf("1", "2", "3-4").firstNotNullOfOrNull { mapData.floor(it).room(number) }

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onFound: (String) -> Unit,
    notFound: Boolean
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
            color = if (notFound) Color(0xFFFDECEC) else ColorSurfaceVariantLight,
            border = BorderStroke(
                if (notFound) 2.dp else 1.dp,
                if (notFound) DEAD_COLOR else ColorBorderLight
            ),
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
                    tint = if (notFound) DEAD_COLOR else ColorTextMuted,
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
                    } else if (notFound) {
                        Text(
                            text = "$value — такого кабинета нет",
                            style = TextStyle(fontSize = 14.sp, color = DEAD_COLOR),
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { input ->
                            onValueChange(input.filter { it.isDigit() || it.isLetter() }.take(6))
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = if (notFound) Color.Transparent else ColorTextTitle
                        ),
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

    val measurer = rememberTextMeasurer()

    val baseFontPx = 22f
    val numberLayouts: Map<String, TextLayoutResult> = remember(floorId, floor, density) {
        floor.rooms.filter { it.number.isNotBlank() }.associate { r ->
            r.id to measurer.measure(
                text = r.number,
                style = TextStyle(
                    fontSize = with(density) { baseFontPx.toSp() },
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

        fun restZoom(): Float =
            (viewH / imgH * 0.72f).coerceIn(1f, 2.2f)

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
                    val k = scale.value
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
                        minScreenPxPerDp = with(density) { 1.dp.toPx() },
                        baseFontPx = baseFontPx
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
    }
}

@Composable
private fun ZoomButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier
            .size(42.dp)
            .bouncyClickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ColorBrandBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

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

    minScreenPxPerDp: Float,

    baseFontPx: Float
) {
    val unit = 1f / zoom

    if (!routePoints.isNullOrEmpty()) {
        val path = Path().apply {
            moveTo(routePoints[0].x * imgW, routePoints[0].y * imgH)
            routePoints.drop(1).forEach { lineTo(it.x * imgW, it.y * imgH) }
        }
        drawPath(path, ROUTE_OUTLINE, style = Stroke(12f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, ROUTE_COLOR, style = Stroke(6f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    if (targetRoom != null) {
        val x = (targetRoom.x - targetRoom.w / 2f) * imgW
        val y = (targetRoom.y - targetRoom.h / 2f) * imgH
        val w = targetRoom.w * imgW
        val h = targetRoom.h * imgH
        val corner = 5f * unit

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

    floor.rooms.forEach { r ->
        val layout = numberLayouts[r.id] ?: return@forEach
        if (r.number.isBlank()) return@forEach

        val wPx = r.w * imgW
        val hPx = r.h * imgH
        val vertical = r.h > r.w * 1.6f

        val fit = if (vertical) {
            (wPx * 0.60f) / layout.size.height.toFloat()
        } else {
            minOf(
                (hPx * 0.50f) / layout.size.height.toFloat(),
                (wPx * 0.85f) / layout.size.width.toFloat()
            )
        }
        if (fit <= 0f) return@forEach

        val cx = r.x * imgW
        val cy = r.y * imgH
        val color = if (r.status == "nonexistent") DEAD_COLOR else Color(0xFF0B3564)

        withTransform({
            if (vertical) rotate(90f, Offset(cx, cy))
            scale(fit, fit, Offset(cx, cy))
        }) {
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f),
                color = color
            )
        }
    }

    if (startRoom != null && routePoints != null) {
        val cx = startRoom.x * imgW
        val cy = startRoom.y * imgH
        drawCircle(ROUTE_OUTLINE, 8f * unit, Offset(cx, cy))
        drawCircle(START_COLOR, 5f * unit, Offset(cx, cy))
    }

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
    val len = 13f * unit
    val tip = Offset(x + c * len, y + s * len)
    val tail = Offset(x - c * len, y - s * len)
    val head = Offset(tip.x - c * 7f * unit, tip.y - s * 7f * unit)
    val hw = 4.5f * unit

    drawLine(ROUTE_OUTLINE, tail, head, strokeWidth = 5.5f * unit, cap = StrokeCap.Round)
    drawLine(color, tail, head, strokeWidth = 3f * unit, cap = StrokeCap.Round)
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
