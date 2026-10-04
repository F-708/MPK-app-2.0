package com.politex.ui.screens.materials

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialAnnotationEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.util.bouncyClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * M4: просмотрщик набора изображений — HorizontalPager + Coil AsyncImage (обе зависимости
 * уже в проекте), зум/пан (1…4×), поворот на 90° кнопкой.
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
 * V8 (свайп): detectTransformGestures заменён на awaitEachGesture — прежний жест потреблял
 * ЛЮБОЙ одиночный свайп и pager было не листнуть. Теперь handled = zoom>1.05f (рассматриваем —
 * одиночный палец двигает картинку) ИЛИ пальцев ≥2; при zoom==1 одиночный свайп НЕ потребляем
 * и его забирает HorizontalPager. На границе пана события тоже не потребляем — листание доступно.
 *
 * V9 (рисование): AnnotationLayer внутри graphicsLayer страницы — штрихи в нормализованных
 * координатах 0..1 переживают зум/поворот и листание; сохранение — как в PdfViewer (дебаунс
 * 500мс + синхронный flush перед выходом).
 */
@Composable
fun MaterialImageViewer(
    item: MaterialItemEntity,
    dao: MaterialDao,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val paths = remember(item.uriPath) { item.imagePaths }

    if (paths.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().background(ColorSurfaceHighlight)) {
            MaterialViewerTopBar(title = item.title, testTag = "image_top_bar", onBack = onBack)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // N12: не тупик — выходы «Назад» и «Удалить запись» (запись есть, файлы пропали)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = "Изображения не найдены: файлы материала удалены с диска.",
                        style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BrandActionButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "Назад",
                        testTag = "image_deleted_back",
                        filled = false,
                        onClick = onBack,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BrandActionButton(
                        icon = Icons.Default.Delete,
                        label = "Удалить запись",
                        testTag = "image_deleted_delete",
                        filled = true,
                        onClick = onDelete,
                    )
                }
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

    // ── V9: рисование (тот же механизм, что в PdfViewer) ─────────────────────
    var drawMode by remember { mutableStateOf(false) }
    var drawColor by remember { mutableIntStateOf(ANNOTATION_COLORS.first()) }
    var drawWidth by remember { mutableFloatStateOf(4f) }
    // X5: ластик — касание удаляет ЦЕЛЫЙ штрих (hit-test в AnnotationOverlay)
    var eraserActive by remember { mutableStateOf(false) }

    val annotations by dao.getAnnotationsFlow(item.id).collectAsState(initial = emptyList())
    val pageStrokes = remember(item.id) { mutableStateMapOf<Int, List<Stroke>>() }
    val dirtyPages = remember(item.id) { mutableStateListOf<Int>() }
    var saveVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(annotations) {
        annotations.forEach { if (it.pageIndex !in dirtyPages) pageStrokes[it.pageIndex] = Stroke.parse(it.strokesJson) }
    }

    suspend fun persistPages(pages: List<Int>) {
        pages.forEach { p ->
            dao.upsertAnnotation(
                MaterialAnnotationEntity(
                    itemId = item.id,
                    pageIndex = p,
                    strokesJson = Stroke.serialize(pageStrokes[p].orEmpty()),
                ),
            )
        }
    }

    fun markDirty(page: Int) {
        if (page !in dirtyPages) dirtyPages += page
        saveVersion++
    }

    LaunchedEffect(saveVersion) {
        if (saveVersion == 0) return@LaunchedEffect
        delay(500)
        val pages = dirtyPages.toList()
        dirtyPages.clear()
        persistPages(pages)
    }

    val currentPage = pagerState.currentPage
    // QA#14: flush БЕЗ runBlocking на main — upsert уходит корутиной на IO, выход сразу
    // после дописи. Флаг flushing держит BackHandler включённым, пока идёт flush:
    // второй «назад» не pop'нет композицию (и не отменит scope) до записи штрихов.
    var flushing by remember { mutableStateOf(false) }
    val flushAndBack: () -> Unit = {
        if (!flushing) {
            flushing = true
            val pages = dirtyPages.toList()
            dirtyPages.clear()
            scope.launch {
                try {
                    if (pages.isNotEmpty()) withContext(Dispatchers.IO) { persistPages(pages) }
                } finally {
                    onBack()
                }
            }
        }
    }
    BackHandler(enabled = dirtyPages.isNotEmpty() || flushing) { flushAndBack() }

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        MaterialViewerTopBar(
            title = item.title,
            testTag = "image_top_bar",
            onBack = flushAndBack,
        ) {
            // V9: карандаш ПЕРЕД кнопкой поворота
            ViewerAction(
                icon = Icons.Default.Edit,
                description = "Рисование",
                testTag = "image_draw_btn",
                active = drawMode,
                onClick = {
                    drawMode = !drawMode
                    if (!drawMode) eraserActive = false
                },
            )
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

        // V9: саб-тулбар рисования — под хедером, пока активен режим
        if (drawMode) {
            AnnotationToolbar(
                color = drawColor,
                widthDp = drawWidth,
                eraserActive = eraserActive,
                onEraserToggle = { eraserActive = !eraserActive },
                onColor = { drawColor = it },
                onWidth = { drawWidth = it },
                onUndo = {
                    val s = pageStrokes[currentPage].orEmpty()
                    if (s.isNotEmpty()) {
                        pageStrokes[currentPage] = s.dropLast(1)
                        markDirty(currentPage)
                    }
                },
                onClear = {
                    pageStrokes[currentPage] = emptyList()
                    dirtyPages.remove(currentPage)
                    scope.launch { dao.deletePageAnnotations(item.id, currentPage) }
                },
                onExit = { drawMode = false },
            )
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
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                var currentStroke by remember(page) { mutableStateOf<Stroke?>(null) }
                // M4: поворот через graphicsLayer + вписывающий масштаб.
                //
                // Просто `rotationZ` крутит уже свёрстанное полотно: портретный кадр в альбомном
                // боксе после поворота на 90° вылезает за границы и обрезается. Поэтому при
                // повороте на 90/270 дополнительно умножаем масштаб на min(w/h, h/w) — повёрнутая
                // картинка гарантированно влезает в бокс.
                val fit =
                    if (rotation % 180 != 0 && boxW > 0f && boxH > 0f) {
                        min(boxW / boxH, boxH / boxW)
                    } else {
                        1f
                    }
                Box(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            // V8: одиночный палец при zoom==1 НЕ потребляем — pager листает набор;
                            // при zoom>1.05 или ≥2 пальцев жест наш (пан/зум картинки).
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var handled = zoom > 1.05f
                                do {
                                    val event = awaitPointerEvent()
                                    if (!handled && event.changes.count { it.pressed } >= 2) handled = true
                                    if (!handled) continue
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val zooming = zoomChange != 1f
                                    val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                    val maxX = max(0f, (newZoom - 1f) * size.width / 2f)
                                    val maxY = max(0f, (newZoom - 1f) * size.height / 2f)
                                    val newX = (pan.x + panChange.x).coerceIn(-maxX, maxX)
                                    val newY = (pan.y + panChange.y).coerceIn(-maxY, maxY)
                                    val movedY = newY != pan.y
                                    val zoomed = newZoom > 1.01f
                                    // Границы пана: не двигаем картинку — не потребляем,
                                    // свайп утекает в pager; горизонталь при зуме — всегда наша.
                                    if (zooming || movedY || (zoomed && panChange.x != 0f)) {
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        if (newZoom < 1.05f) {
                                            zoom = 1f
                                            pan = Offset.Zero
                                        } else {
                                            zoom = newZoom
                                            pan = Offset(newX, newY)
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }.graphicsLayer {
                            rotationZ = rotation.toFloat()
                            scaleX = zoom * fit
                            scaleY = zoom * fit
                            translationX = pan.x
                            translationY = pan.y
                        },
                ) {
                    AsyncImage(
                        model = File(paths[page]),
                        contentDescription = "Изображение ${page + 1} из ${paths.size}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // V9: слой рисования внутри graphicsLayer — штрихи масштабируются/крутятся
                    // вместе со страницей; pageIndex = страница pager'а.
                    AnnotationLayer(
                        strokes = pageStrokes[page].orEmpty(),
                        current = currentStroke,
                        drawMode = drawMode,
                        strokeColor = drawColor,
                        strokeWidthDp = drawWidth,
                        onCurrentChange = { currentStroke = it },
                        onStrokeFinished = { stroke ->
                            pageStrokes[page] = pageStrokes[page].orEmpty() + stroke
                            markDirty(page)
                        },
                        eraserActive = eraserActive,
                        transformKey = rotation,
                        onEraseAt = { p ->
                            // X5: hit-test в нормализованных координатах бокса страницы
                            // (координаты жеста приходят уже в повёрнутом/нескалированном
                            // пространстве слоя — там же, где живут штрихи).
                            if (boxW > 1f && boxH > 1f) {
                                val aspect = boxH / boxW
                                val current = pageStrokes[page].orEmpty()
                                val remaining =
                                    current.filterNot { st ->
                                        val th = with(density) { (st.widthDp / 2f + ERASER_TOUCH_EXTRA_DP).dp.toPx() } / boxW
                                        eraserHitTest(st, p, th, aspect)
                                    }
                                if (remaining.size != current.size) {
                                    pageStrokes[page] = remaining
                                    markDirty(page)
                                }
                            }
                        },
                        transformGestures = { panChange, zoomChange ->
                            // X32: слой внутри graphicsLayer (rotation + scale=zoom*fit) —
                            // локальный panChange пальца переводим в экранный сдвиг:
                            // повернуть на rotation и умножить на суммарный масштаб.
                            val scale = zoom * fit
                            val rad = Math.toRadians(rotation.toDouble())
                            val cs = kotlin.math.cos(rad).toFloat()
                            val sn = kotlin.math.sin(rad).toFloat()
                            val screenPan =
                                Offset(
                                    (panChange.x * cs - panChange.y * sn) * scale,
                                    (panChange.x * sn + panChange.y * cs) * scale,
                                )
                            val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                            val maxX = max(0f, (newZoom - 1f) * boxW / 2f)
                            val maxY = max(0f, (newZoom - 1f) * boxH / 2f)
                            if (newZoom < 1.05f) {
                                zoom = 1f
                                pan = Offset.Zero
                            } else {
                                zoom = newZoom
                                pan =
                                    Offset(
                                        (pan.x + screenPan.x).coerceIn(-maxX, maxX),
                                        (pan.y + screenPan.y).coerceIn(-maxY, maxY),
                                    )
                            }
                        },
                        modifier = Modifier.matchParentSize(),
                    )
                }
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
