package com.politex.ui.screens.materials

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.util.bouncyClickable
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * M4: просмотрщик набора изображений — HorizontalPager + Coil AsyncImage (обе зависимости
 * уже в проекте), зум/пан `detectTransformGestures` (1…4×), поворот на 90° кнопкой.
 *
 * ПОВОРОТ делается `graphicsLayer { rotationZ }` плюс вписывающий масштаб min(w/h, h/w) при
 * повороте на 90/270: без него повёрнутое полотно вылезает за границы бокса и обрезается.
 * (Coil-трансформацией не вышло — в coil-base 2.7 есть только CircleCrop и RoundedCorners.)
 *
 * `rotationDeg` хранится в строке материала и применяется ко ВСЕМ страницам набора. Так сделано
 * осознанно: реалистичный сценарий — преподаватель скинул 12 фото доски, снятых одним и тем же
 * «боком», и крутить каждое отдельно смысла нет. Листание поворот не сбрасывает (состояние живёт
 * вне страницы), а вот зум/пан при смене страницы обнуляются — рассматривали одну страницу,
 * следующую хотим видеть целиком.
 *
 * ponytail: per-image поворот потребовал бы отдельной таблицы/JSON-колонки на страницу;
 * добавить, если наборы окажутся разноориентированными.
 *
 * pan ограничен габаритами контейнера: иначе картинку можно увезти за экран и потерять.
 */
@Composable
fun MaterialImageViewer(
    item: MaterialItemEntity,
    dao: MaterialDao,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val paths = remember(item.uriPath) { item.imagePaths }

    if (paths.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().background(ColorSurfaceHighlight)) {
            MaterialViewerTopBar(title = item.title, testTag = "image_top_bar", onBack = onBack)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Изображения не найдены: файлы материала удалены с диска.",
                    style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        return
    }

    var rotation by remember(item.id) { mutableStateOf(item.rotationDeg) }
    var zoom by remember(item.id) { mutableStateOf(1f) }
    var pan by remember(item.id) { mutableStateOf(Offset.Zero) }
    val pagerState = rememberPagerState(pageCount = { paths.size })

    // Листнули страницу — рассматривание предыдущей не переносим на следующую.
    LaunchedEffect(pagerState.currentPage) {
        zoom = 1f
        pan = Offset.Zero
    }

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        MaterialViewerTopBar(
            title = item.title,
            testTag = "image_top_bar",
            onBack = onBack,
        ) {
            ViewerAction(
                icon = Icons.Default.RotateRight,
                description = "Повернуть на 90°",
                testTag = "image_rotate_btn",
                onClick = {
                    rotation = (rotation + 90) % 360
                    scope.launch { dao.setRotationDeg(item.id, rotation) }
                },
            )
            Surface(
                shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(2.dp),
                color = Color.Transparent,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${paths.size}",
                        maxLines = 1,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
                        modifier = Modifier.testTag("image_page_counter"),
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("image_pager"),
        ) { page ->
            BoxWithConstraints(
                modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val boxW = with(LocalDensity.current) { maxWidth.toPx() }
                val boxH = with(LocalDensity.current) { maxHeight.toPx() }
                // M4: поворот через graphicsLayer + вписывающий масштаб.
                //
                // Просто `rotationZ` крутит уже свёрстанное полотно: портретный кадр в альбомном
                // боксе после поворота на 90° вылезает за границы и обрезается. Поэтому при
                // повороте на 90/270 дополнительно умножаем масштаб на min(w/h, h/w) — повёрнутая
                // картинка гарантированно влезает в бокс.
                //
                // Coil-трансформации здесь не помощник: в coil-base 2.7 лежат только CircleCrop и
                // RoundedCorners, RotateTransformation нет, а тянуть отдельный модуль ради поворота
                // дороже, чем четыре строки математики.
                val fit =
                    if (rotation % 180 != 0 && boxW > 0f && boxH > 0f) {
                        min(boxW / boxH, boxH / boxW)
                    } else {
                        1f
                    }
                AsyncImage(
                    model = File(paths[page]),
                    contentDescription = "Изображение ${page + 1} из ${paths.size}",
                    contentScale = ContentScale.Fit,
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                // Границы сдвига: чем больше зум, тем дальше можно уехать
                                val maxX = max(0f, (newZoom - 1f) * size.width / 2f)
                                val maxY = max(0f, (newZoom - 1f) * size.height / 2f)
                                zoom = newZoom
                                pan =
                                    Offset(
                                        (pan.x + gesturePan.x).coerceIn(-maxX, maxX),
                                        (pan.y + gesturePan.y).coerceIn(-maxY, maxY),
                                    )
                            }
                        }.graphicsLayer {
                            rotationZ = rotation.toFloat()
                            scaleX = zoom * fit
                            scaleY = zoom * fit
                            translationX = pan.x
                            translationY = pan.y
                        },
                )
            }
        }

        // Двойной тап не используем: сброс зума — кнопкой, она заметнее жеста
        if (zoom > 1.01f) {
            Surface(
                shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(2.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .bouncyClickable {
                        zoom = 1f
                        pan = Offset.Zero
                    }.testTag("image_reset_zoom_btn"),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "СБРОСИТЬ МАСШТАБ",
                        maxLines = 1,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White),
                    )
                }
            }
        }
    }
}
