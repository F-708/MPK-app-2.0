package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CollegeMap
import com.example.data.model.RoomPin
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorSurfaceVariantLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.util.bouncyClickable
import kotlinx.coroutines.launch

/**
 * Карта колледжа: планы этажей и поиск кабинета.
 *
 * Открывается двумя путями:
 * - из расписания по нажатию на кабинет — сразу нужный этаж и подсветка,
 *   строка поиска при этом не нужна и не показывается;
 * - из меню «Другое» — со строкой поиска, чтобы найти любой кабинет.
 *
 * Кабинет подсвечивается рамкой по контуру и слегка пульсирует, чтобы его
 * было видно на плотном чертеже. Увести карту за пределы экрана нельзя.
 */
@Composable
fun CollegeMapScreen(
    initialRoom: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Из расписания приходят с готовым кабинетом — поиск тогда лишний
    val cameFromSchedule = !initialRoom.isNullOrBlank()

    var searchInput by remember { mutableStateOf("") }
    var focusedRoom by remember { mutableStateOf(initialRoom?.trim().orEmpty()) }
    var notFound by remember { mutableStateOf(false) }
    var floor by remember {
        mutableStateOf(CollegeMap.floorForRoom(focusedRoom) ?: CollegeMap.Floor.FIRST)
    }

    val pin = remember(focusedRoom, floor) {
        CollegeMap.roomPins[focusedRoom]?.takeIf { it.floor == floor }
    }

    LaunchedEffect(focusedRoom) {
        CollegeMap.floorForRoom(focusedRoom)?.let { floor = it }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        // Шапка: только стрелка и название, без пояснений
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
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
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Карта колледжа",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ColorTextTitle)
            )
        }

        // Поиск: только если карту открыли из меню, а не по конкретному кабинету
        if (!cameFromSchedule) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { input ->
                        searchInput = input.filter { it.isDigit() || it.isLetter() }.take(6)
                        notFound = false
                    },
                    singleLine = true,
                    placeholder = { Text("Кабинет, например 214", fontSize = 13.sp) },
                    textStyle = TextStyle(fontSize = 15.sp, color = ColorTextTitle),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    shape = RoundedCornerShape(2.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorBrandBlue,
                        unfocusedBorderColor = ColorBorderLight,
                        focusedContainerColor = ColorSurfaceVariantLight,
                        unfocusedContainerColor = ColorSurfaceVariantLight,
                        cursorColor = ColorBrandBlue
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Кнопка запускает поиск: без неё непонятно, когда он сработает
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    border = BorderStroke(1.dp, ColorBrandFill),
                    modifier = Modifier
                        .height(52.dp)
                        .bouncyClickable {
                            val query = searchInput.trim()
                            if (query.isBlank()) {
                                notFound = false
                                focusedRoom = ""
                            } else {
                                focusedRoom = query
                                notFound = CollegeMap.roomPins[query] == null
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "НАЙТИ",
                            softWrap = false,
                            maxLines = 1,
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            if (notFound) {
                Text(
                    text = "Кабинет $focusedRoom не размечен. Открыт нужный этаж — номер видно на плане.",
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Этажи
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CollegeMap.Floor.entries.forEach { item ->
                val selected = floor == item
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = if (selected) ColorBrandFill else ColorBgMain,
                    border = BorderStroke(1.dp, if (selected) ColorBrandFill else ColorBorderLight),
                    modifier = Modifier
                        .weight(1f)
                        .bouncyClickable { floor = item }
                ) {
                    Text(
                        text = item.title,
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
                            .padding(vertical = 9.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        MapCanvas(
            floor = floor,
            room = focusedRoom.takeIf { CollegeMap.looksLikeRoomNumber(it) },
            pin = pin,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

/**
 * План этажа: зум, перетаскивание и анимация наезда на кабинет.
 *
 * Перемещение ограничено так, чтобы план всегда занимал экран — улететь
 * в пустоту и потерять карту нельзя.
 */
@Composable
private fun MapCanvas(
    floor: CollegeMap.Floor,
    room: String?,
    pin: RoomPin?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }

    val imagePainter = painterResource(id = mapResId(floor))
    // Пропорции берём у самой картинки: планы меняются, и жёсткое число
    // разъехалось бы с новым изображением
    val imageAspect = imagePainter.intrinsicSize.let { size ->
        if (size.height > 0f && size.width > 0f) size.width / size.height else 1.414f
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val imageW = viewW
        val imageH = imageW / imageAspect

        // Держим план в границах окна: меньше окна — центрируем,
        // больше — не даём утащить дальше его края
        fun clampX(value: Float, s: Float): Float {
            val content = imageW * s
            return if (content <= viewW) (viewW - content) / 2f
            else value.coerceIn(viewW - content, 0f)
        }

        fun clampY(value: Float, s: Float): Float {
            val content = imageH * s
            return if (content <= viewH) (viewH - content) / 2f
            else value.coerceIn(viewH - content, 0f)
        }

        LaunchedEffect(floor, room, pin, viewW, viewH) {
            if (viewW <= 0f || viewH <= 0f) return@LaunchedEffect

            if (pin == null) {
                scope.launch { scale.animateTo(1f, tween(250)) }
                scope.launch { offsetX.animateTo(clampX(0f, 1f), tween(250)) }
                scope.launch { offsetY.animateTo(clampY(0f, 1f), tween(250)) }
                return@LaunchedEffect
            }

            val px = pin.x * imageW
            val py = pin.y * imageH

            // Фаза 1 — приближение к кабинету
            val zoom = 2.6f
            scope.launch { scale.animateTo(zoom, tween(420, easing = LinearOutSlowInEasing)) }
            scope.launch {
                offsetX.animateTo(
                    clampX(viewW / 2f - px * zoom, zoom),
                    tween(420, easing = LinearOutSlowInEasing)
                )
            }
            scope.launch {
                offsetY.animateTo(
                    clampY(viewH / 2f - py * zoom, zoom),
                    tween(420, easing = LinearOutSlowInEasing)
                )
            }
            kotlinx.coroutines.delay(580)

            // Фаза 2 — отъезд: видно весь этаж, подсветка остаётся
            scope.launch { scale.animateTo(1f, tween(520)) }
            scope.launch { offsetX.animateTo(clampX(0f, 1f), tween(520)) }
            scope.launch { offsetY.animateTo(clampY(0f, 1f), tween(520)) }
        }

        // Пульсация подсветки: рамка дышит, но не бросается в глаза
        LaunchedEffect(pin) {
            if (pin == null) return@LaunchedEffect
            while (true) {
                pulse.animateTo(1f, tween(700, easing = LinearOutSlowInEasing))
                pulse.animateTo(0.45f, tween(700))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewW, viewH) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scope.launch {
                            val next = (scale.value * zoom).coerceIn(1f, 5f)
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
                    painter = imagePainter,
                    contentDescription = "План: ${floor.title}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(with(density) { imageW.toDp() })
                        .height(with(density) { imageH.toDp() })
                )

                // Подсветка кабинета: рамка по контуру, а не круглая метка —
                // так видно сам кабинет, а не точку рядом с ним
                if (pin != null) {
                    val boxW = pin.w * imageW
                    val boxH = pin.h * imageH
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) { (pin.x * imageW - boxW / 2f).toDp() },
                                y = with(density) { (pin.y * imageH - boxH / 2f).toDp() }
                            )
                            .size(
                                width = with(density) { boxW.toDp() },
                                height = with(density) { boxH.toDp() }
                            )
                    ) {
                        // Заливка — чтобы кабинет читался даже на пёстром плане
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ColorBrandBlue.copy(alpha = 0.18f * pulse.value))
                                .border(
                                    2.dp,
                                    ColorBrandBlue.copy(alpha = pulse.value),
                                    RoundedCornerShape(1.dp)
                                )
                        )
                        if (!room.isNullOrBlank()) {
                            Text(
                                text = room,
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorBrandBlue
                                ),
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(y = (-11).dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun mapResId(floor: CollegeMap.Floor): Int = when (floor) {
    CollegeMap.Floor.FIRST -> com.example.R.drawable.map_floor1
    CollegeMap.Floor.SECOND -> com.example.R.drawable.map_floor2
    CollegeMap.Floor.THIRD_FOURTH -> com.example.R.drawable.map_floor34
}
