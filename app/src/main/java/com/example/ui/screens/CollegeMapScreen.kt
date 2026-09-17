package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
 * Карта колледжа: план этажа с поиском кабинета.
 *
 * При открытии с указанным кабинетом проигрывается короткая анимация: камера
 * наезжает на кабинет, затем отъезжает и показывает план целиком — так сразу
 * видно и сам кабинет, и где он находится относительно всего этажа. Метка
 * остаётся и пульсирует.
 *
 * Планы — сканы пожарных планов эвакуации, те самые, по которым в колледже
 * и ориентируются. Пинч и перетаскивание работают в любой момент.
 */
@Composable
fun CollegeMapScreen(
    initialRoom: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var roomQuery by remember { mutableStateOf(initialRoom.orEmpty()) }

    // Кабинет, к которому надо перейти: либо из расписания, либо введён вручную
    var focusedRoom by remember { mutableStateOf(initialRoom?.trim().orEmpty()) }
    var floor by remember {
        mutableStateOf(CollegeMap.floorForRoom(initialRoom.orEmpty()) ?: CollegeMap.Floor.FIRST)
    }

    val pin = remember(focusedRoom) { CollegeMap.roomPins[focusedRoom] }

    // Если кабинет известен по номеру — переключаемся на его этаж
    LaunchedEffect(focusedRoom) {
        CollegeMap.floorForRoom(focusedRoom)?.let { floor = it }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        // Шапка
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .bouncyClickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Карта колледжа",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitle)
                )
                Text(
                    text = floor.hint,
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                    maxLines = 1
                )
            }
        }

        // Поиск кабинета
        OutlinedTextField(
            value = roomQuery,
            onValueChange = { input ->
                roomQuery = input.filter { it.isDigit() || it.isLetter() }.take(6)
            },
            singleLine = true,
            placeholder = { Text("Номер кабинета, например 214", fontSize = 13.sp) },
            textStyle = TextStyle(fontSize = 15.sp, color = ColorTextTitle),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(2.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ColorBrandBlue,
                unfocusedBorderColor = ColorBorderLight,
                focusedContainerColor = ColorSurfaceVariantLight,
                unfocusedContainerColor = ColorSurfaceVariantLight,
                cursorColor = ColorBrandBlue
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Этажи
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CollegeMap.Floor.entries.forEach { item ->
                val selected = floor == item
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = if (selected) ColorBrandFill else ColorBgMain,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) ColorBrandFill else ColorBorderLight
                    ),
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
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Сам план
        MapCanvas(
            floor = floor,
            highlightedRoom = focusedRoom.takeIf { CollegeMap.looksLikeRoomNumber(it) },
            pin = pin?.takeIf { it.floor == floor },
            onRoomTapped = { found -> roomQuery = found; focusedRoom = found },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Подсказка внизу: что искать и почему не видно метки
        val hint = when {
            focusedRoom.isBlank() -> "Введите номер кабинета — покажем, где он находится"
            pin == null -> "Кабинет $focusedRoom — на этом плане. Метка для него ещё не отмечена, " +
                "найдите номер на плане или уточните его у нас."
            else -> "Кабинет $focusedRoom отмечен на плане"
        }
        Text(
            text = hint,
            style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

/**
 * План этажа с зумом, перетаскиванием и анимацией наезда на кабинет.
 */
@Composable
private fun MapCanvas(
    floor: CollegeMap.Floor,
    highlightedRoom: String?,
    pin: RoomPin?,
    onRoomTapped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    // Картинка плана. Пропорции у всех трёх сканов одинаковые, поэтому
    // считаем размер один раз: от него зависят координаты метки.
    val imageAspect = 1.414f
    val imagePainter = painterResource(id = mapResId(floor))

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val imageW = viewW
        val imageH = imageW / imageAspect

        fun focusOn(p: RoomPin, targetScale: Float): Triple<Float, Float, Float> {
            val px = p.x * imageW
            val py = p.y * imageH
            return Triple(targetScale, viewW / 2f - px * targetScale, viewH / 2f - py * targetScale)
        }

        // При смене этажа или кабинета: наезд на кабинет, затем отъезд к плану целиком
        LaunchedEffect(floor, highlightedRoom, pin, viewW, viewH) {
            if (viewW <= 0f || viewH <= 0f) return@LaunchedEffect

            if (pin == null) {
                scope.launch { scale.animateTo(1f, tween(250)) }
                scope.launch { offsetX.animateTo(0f, tween(250)) }
                scope.launch { offsetY.animateTo(0f, tween(250)) }
                return@LaunchedEffect
            }

            // Фаза 1 — приближение к кабинету
            val zoom = 2.6f
            val (zs, zx, zy) = focusOn(pin, zoom)
            scope.launch { scale.animateTo(zs, tween(420, easing = LinearOutSlowInEasing)) }
            scope.launch { offsetX.animateTo(zx, tween(420, easing = LinearOutSlowInEasing)) }
            scope.launch { offsetY.animateTo(zy, tween(420, easing = LinearOutSlowInEasing)) }
            kotlinx.coroutines.delay(580)

            // Фаза 2 — отъезд: виден весь этаж, метка остаётся
            scope.launch { scale.animateTo(1f, tween(520)) }
            scope.launch { offsetX.animateTo(0f, tween(520)) }
            scope.launch { offsetY.animateTo(0f, tween(520)) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scope.launch {
                            scale.snapTo((scale.value * zoom).coerceIn(1f, 5f))
                            offsetX.snapTo(offsetX.value + pan.x)
                            offsetY.snapTo(offsetY.value + pan.y)
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
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
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

            // Метка кабинета — пульсирует, чтобы её было видно поверх чертежа
            if (pin != null) {
                val pulse = remember { Animatable(1f) }
                LaunchedEffect(pin) {
                    while (true) {
                        pulse.animateTo(1.9f, tween(900, easing = LinearOutSlowInEasing))
                        pulse.snapTo(1f)
                    }
                }

                val markerPx = with(density) { 22.dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (pin.x * imageW - markerPx / 2f).toDp() },
                            y = with(density) { (pin.y * imageH - markerPx / 2f).toDp() }
                        )
                        .size(22.dp)
                ) {
                    // Расходящийся круг
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = pulse.value
                                scaleY = pulse.value
                                alpha = (2.0f - pulse.value).coerceIn(0f, 0.5f)
                            }
                            .background(ColorBrandBlue, androidx.compose.foundation.shape.CircleShape)
                    )
                    // Ядро метки
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(ColorBrandFill, androidx.compose.foundation.shape.CircleShape)
                            .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = highlightedRoom ?: "",
                            style = TextStyle(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1
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
