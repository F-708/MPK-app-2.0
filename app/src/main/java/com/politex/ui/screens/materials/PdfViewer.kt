package com.politex.ui.screens.materials

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialAnnotationEntity
import com.politex.data.local.entity.MaterialBookmarkEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.util.bouncyClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import android.graphics.Color as AndroidColor

/**
 * M2: просмотрщик PDF на `android.graphics.pdf.PdfRenderer` (платформа, ноль новых зависимостей).
 *
 * ВЫБОР РАСКЛАДКИ (задание предлагало вертикаль или горизонтальный pager): ВЕРТИКАЛЬНЫЙ LazyColumn.
 * Методичку/конспект листают сверху вниз и читают «по диагонали»; вертикаль даёт непрерывный скролл,
 * а «первая видимая страница» = естественный lastPage. Горизонтальный pager хорош для слайдов,
 * но ломает семантику lastPage и тянет предзагрузку соседей.
 *
 * Рендер: bitmap по мере появления страницы, LruCache на 1/8 кучи (3–5 страниц A4 на типовом
 * телефоне). `PdfRenderer` НЕ потокобезопасен и держит открытой ровно одну страницу — рендер
 * сериализован мьютексом и уходит на Dispatchers.Default.
 *
 * Вытесненные битмапы НЕ `.recycle()`: Compose может ещё рисовать этот ImageBitmap. С Android 8
 * пиксели лежат в native-куче под NativeAllocationRegistry, их забирает GC вместе с записью кэша.
 *
 * `lastPage` пишется на КАЖДОЙ смене первой видимой страницы (distinctUntilChanged), а не «при
 * выходе»: при выходе compose-scope уже отменён и запись могла бы не состояться.
 */
@Composable
fun MaterialPdfViewer(
    item: MaterialItemEntity,
    dao: MaterialDao,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val file = remember(item.uriPath) { File(item.uriPath) }

    // Один файл при входе на экран: открытие дешёвое, зато pageCount известен сразу.
    val handle =
        remember(file) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pfd to PdfRenderer(pfd)
            }.getOrNull()
        }

    DisposableEffect(handle) {
        onDispose {
            handle?.let { (pfd, renderer) ->
                runCatching { renderer.close() }
                runCatching { pfd.close() }
            }
        }
    }

    if (handle == null) {
        // W16: и в аварийном пути — полноэкранный режим (баннер/навигация уже скрыты),
        // поэтому шапка обязана сама нарисовать отступ под статусбар
        Column(modifier = modifier.fillMaxSize().background(ColorSurfaceHighlight)) {
            PdfReaderBar(
                title = item.title,
                currentPage = 0,
                pageCount = 0,
                bookmarkListActive = false,
                drawActive = false,
                keepZoomActive = false,
                onKeepZoomToggle = {},
                onBack = onBack,
                onPageClick = {},
                onBookmarks = {},
                onDrawToggle = {},
            )
            UnsupportedFormatPlaque(ext = "pdf", fileName = item.fileName.ifBlank { file.name })
        }
        return
    }

    val renderer = handle.second
    val pageCount = renderer.pageCount
    val bookmarks by dao.getBookmarksFlow(item.id).collectAsState(initial = emptyList())
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = item.lastPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        )
    val renderLock = remember(renderer) { Mutex() }
    val pageCache = remember(renderer) { newPageBitmapCache() }

    var showBookmarkList by remember { mutableStateOf(false) }
    var bookmarkDraftPage by remember { mutableStateOf<Int?>(null) }
    var showPageJump by remember { mutableStateOf(false) }

    // ── V9: рисование поверх страниц ──────────────────────────────────────────
    var drawMode by remember { mutableStateOf(false) }
    var drawColor by remember { mutableIntStateOf(ANNOTATION_COLORS.first()) }
    var drawWidth by remember { mutableFloatStateOf(4f) }
    // X5: ластик — режим саб-тулбара, касание удаляет целый штрих
    var eraserActive by remember { mutableStateOf(false) }

    // ── X34: «держать зум страницы» — общий зум/пан всех страниц + prefs на материал ──
    val context = LocalContext.current
    var keepZoom by remember(item.id) { mutableStateOf(PdfZoomStore.keep(context, item.id)) }
    val sharedZoomState = remember(item.id) { mutableFloatStateOf(1f) }
    val sharedPanState = remember(item.id) { mutableStateOf(Offset.Zero) }

    // Тумблер переключает и СРАЗУ применяет сохранённое (вкл) / сбрасывает (выкл).
    LaunchedEffect(item.id, keepZoom) {
        if (keepZoom) {
            PdfZoomStore.loadZoom(context, item.id)?.let { (z, p) ->
                sharedZoomState.floatValue = z
                sharedPanState.value = p
            }
        } else {
            PdfZoomStore.clearZoom(context, item.id)
            sharedZoomState.floatValue = 1f
            sharedPanState.value = Offset.Zero
        }
    }
    // Дебаунс 400мс: серия pinch-событий — одна запись prefs (как у аннотаций).
    LaunchedEffect(keepZoom, sharedZoomState.floatValue, sharedPanState.value) {
        if (!keepZoom) return@LaunchedEffect
        delay(400)
        PdfZoomStore.saveZoom(context, item.id, sharedZoomState.floatValue, sharedPanState.value)
    }

    val annotations by dao.getAnnotationsFlow(item.id).collectAsState(initial = emptyList())
    // Источник истины для рендера: стартует из БД, правки копятся локально и пишутся дебаунсом.
    val pageStrokes = remember(item.id) { mutableStateMapOf<Int, List<Stroke>>() }
    val dirtyPages = remember(item.id) { mutableStateListOf<Int>() }
    var saveVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(annotations) {
        // Грязные страницы НЕ перетираем: эмиссия потока может доехать позже локальной правки.
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

    // Дебаунс 500мс: серия штрихов одной страницы — один upsert (unique itemId+pageIndex).
    LaunchedEffect(saveVersion) {
        if (saveVersion == 0) return@LaunchedEffect
        delay(500)
        val pages = dirtyPages.toList()
        dirtyPages.clear()
        persistPages(pages)
    }

    // V9: синхронный flush ДО pop — иначе штрих, нарисованный <500мс назад, теряется:
    // при выходе compose-scope отменяется и дебаунс-сохранение не успевает.
    val flushAndBack: () -> Unit = {
        val pages = dirtyPages.toList()
        dirtyPages.clear()
        if (pages.isNotEmpty()) runBlocking(Dispatchers.IO) { persistPages(pages) }
        // X34: зум, изменённый <400мс назад, тоже обязан пережить выход
        if (keepZoom) PdfZoomStore.saveZoom(context, item.id, sharedZoomState.floatValue, sharedPanState.value)
        onBack()
    }
    BackHandler(enabled = dirtyPages.isNotEmpty()) { flushAndBack() }

    // M2: запоминаем первую видимую страницу — «где остановился» переживает смерть процесса.
    LaunchedEffect(listState, item.id) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { page -> dao.setLastPage(item.id, page) }
    }

    val currentPage = listState.firstVisibleItemIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    Column(modifier = modifier.fillMaxSize().background(ColorSurfaceHighlight)) {
        // W16: сверху ВМЕСТО баннера — синяя строка: назад, «12/340» (тап = переход),
        // закладки. Она же оставляет место под строку времени/иконок (statusBarsPadding).
        PdfReaderBar(
            title = item.title,
            currentPage = currentPage,
            pageCount = pageCount,
            bookmarkListActive = showBookmarkList,
            drawActive = drawMode,
            keepZoomActive = keepZoom,
            onKeepZoomToggle = {
                keepZoom = !keepZoom
                PdfZoomStore.setKeep(context, item.id, keepZoom)
            },
            onBack = flushAndBack,
            onPageClick = { showPageJump = true },
            onBookmarks = { showBookmarkList = !showBookmarkList },
            onDrawToggle = {
                drawMode = !drawMode
                if (drawMode) showBookmarkList = false else eraserActive = false
            },
        )

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

        if (showBookmarkList) {
            BookmarkPanel(
                bookmarks = bookmarks,
                onJump = { page ->
                    showBookmarkList = false
                    scope.jumpToPage(listState, page, pageCount)
                },
                onDelete = { id -> scope.launch { dao.deleteBookmarkById(id) } },
            )
        }

        // V9: clipToBounds — увеличенная страница (и слой рисования на ней) не налезает на хедер.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            val density = LocalDensity.current
            val renderWidthPx = with(density) { (maxWidth - 8.dp).roundToPx() }.coerceIn(320, 1400)

            LazyColumn(
                state = listState,
                modifier =
                Modifier
                    .fillMaxSize()
                    .testTag("pdf_page_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(count = pageCount, key = { it }) { index ->
                    PdfPageRow(
                        renderer = renderer,
                        pageIndex = index,
                        cache = pageCache,
                        renderLock = renderLock,
                        renderWidthPx = renderWidthPx,
                        bookmarked = bookmarks.any { it.page == index },
                        // W16: повторный тап по закрашенной закладке УБИРАЕТ её; по пустой —
                        // открывает диалог с подписью (как раньше)
                        onBookmarkClick = { bookmarked ->
                            if (bookmarked) {
                                scope.launch {
                                    bookmarks.filter { it.page == index }.forEach { dao.deleteBookmarkById(it.id) }
                                }
                            } else {
                                bookmarkDraftPage = index
                            }
                        },
                        strokes = pageStrokes[index].orEmpty(),
                        drawMode = drawMode,
                        drawColor = drawColor,
                        drawWidthDp = drawWidth,
                        eraserActive = eraserActive,
                        keepZoom = keepZoom,
                        sharedZoomState = sharedZoomState,
                        sharedPanState = sharedPanState,
                        onStrokeFinished = { stroke ->
                            pageStrokes[index] = pageStrokes[index].orEmpty() + stroke
                            markDirty(index)
                        },
                        onStrokesReplaced = { list ->
                            pageStrokes[index] = list
                            markDirty(index)
                        },
                    )
                }
            }
        }
    }

    // M2: закладка сразу с подписью («Формулы», «Определения») — иначе список закладок
    // превращается в безликие номера страниц.
    bookmarkDraftPage?.let { page ->
        AddBookmarkDialog(
            page = page,
            onDismiss = { bookmarkDraftPage = null },
            onConfirm = { label ->
                bookmarkDraftPage = null
                scope.launch {
                    dao.insertBookmark(MaterialBookmarkEntity(itemId = item.id, page = page, label = label))
                }
            },
        )
    }

    if (showPageJump) {
        PageJumpDialog(
            pageCount = pageCount,
            onDismiss = { showPageJump = false },
            onJump = { page ->
                showPageJump = false
                scope.jumpToPage(listState, page, pageCount)
            },
        )
    }
}

/** Прыжок на страницу: номер приводим к диапазону (битый PDF может дать pageCount = 0). */
private fun kotlinx.coroutines.CoroutineScope.jumpToPage(
    state: LazyListState,
    page: Int,
    pageCount: Int,
) {
    val target = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    launch { state.scrollToItem(target) }
}

/**
 * X34: «держать зум страницы». SharedPreferences `mpk_prefs`, ключи на материал:
 * `pdf_keep_zoom_<itemId>` (Boolean) — флаг тумблера, `pdf_zoom_<itemId>` (Float) и
 * `pdf_pan_<itemId>` («x,y») — сохранённый зум/пан. serializePan/parsePan — чистые
 * функции, покрыты unit-тестом; битая строка → null (зум остаётся сохранённым, пан нулевым).
 */
object PdfZoomStore {
    private const val PREFS = "mpk_prefs"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun keep(ctx: Context, itemId: Long): Boolean = prefs(ctx).getBoolean("pdf_keep_zoom_$itemId", false)

    fun setKeep(ctx: Context, itemId: Long, on: Boolean) {
        prefs(ctx).edit().putBoolean("pdf_keep_zoom_$itemId", on).apply()
    }

    fun saveZoom(ctx: Context, itemId: Long, zoom: Float, pan: Offset) {
        prefs(ctx).edit().putFloat("pdf_zoom_$itemId", zoom).putString("pdf_pan_$itemId", serializePan(pan)).apply()
    }

    /** null — сохранять нечего (zoom≈1); вызывающий оставляет зум страниц по умолчанию. */
    fun loadZoom(ctx: Context, itemId: Long): Pair<Float, Offset>? {
        val p = prefs(ctx)
        val z = p.getFloat("pdf_zoom_$itemId", 1f)
        if (z <= 1.01f) return null
        return z to (parsePan(p.getString("pdf_pan_$itemId", null)) ?: Offset.Zero)
    }

    fun clearZoom(ctx: Context, itemId: Long) {
        prefs(ctx).edit().remove("pdf_zoom_$itemId").remove("pdf_pan_$itemId").apply()
    }

    fun serializePan(p: Offset): String = "${p.x},${p.y}"

    fun parsePan(s: String?): Offset? =
        s?.split(",")?.let { parts ->
            if (parts.size != 2) return@let null
            val x = parts[0].trim().toFloatOrNull() ?: return@let null
            val y = parts[1].trim().toFloatOrNull() ?: return@let null
            Offset(x, y)
        }
}

/**
 * W16: синяя строка чтения ВМЕСТО баннера (просмотрщик самодостаточен, баннер и нижняя
 * навигация в MainScreen скрыты). Состав: [стрелка назад 48dp] [«12/340» — тап открывает
 * диалог перехода] … [кнопка списка закладок]. statusBarsPadding оставляет место под строку
 * времени и иконок; высота строки 52dp. pageCount = 0 — аварийный путь (файл не открылся):
 * вместо номера показываем заголовок, закладок нет.
 */
@Composable
private fun PdfReaderBar(
    title: String,
    currentPage: Int,
    pageCount: Int,
    bookmarkListActive: Boolean,
    drawActive: Boolean,
    keepZoomActive: Boolean,
    onKeepZoomToggle: () -> Unit,
    onBack: () -> Unit,
    onPageClick: () -> Unit,
    onBookmarks: () -> Unit,
    onDrawToggle: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorTopBar,
        modifier = Modifier.fillMaxWidth().testTag("pdf_top_bar"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).testTag("pdf_top_bar_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White,
                )
            }
            if (pageCount > 0) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    modifier =
                    Modifier
                        .heightIn(min = 44.dp)
                        .bouncyClickable(onClick = onPageClick)
                        .testTag("pdf_page_indicator"),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    ) {
                        Text(
                            text = "${currentPage + 1}/$pageCount",
                            maxLines = 1,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White),
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                // X34: тумблер «держать зум страницы» — зум/пан переживают листание и выход
                ViewerAction(
                    icon = Icons.Default.ZoomIn,
                    description = "Держать зум страницы",
                    testTag = "pdf_keep_zoom",
                    active = keepZoomActive,
                    onClick = onKeepZoomToggle,
                )
                // V9: карандаш ПЕРЕД закладками — режим рисования включают чаще, чем список
                ViewerAction(
                    icon = Icons.Default.Edit,
                    description = "Рисование",
                    testTag = "pdf_draw_btn",
                    active = drawActive,
                    onClick = onDrawToggle,
                )
                ViewerAction(
                    icon = Icons.Default.Bookmark,
                    description = "Закладки",
                    testTag = "pdf_bookmarks_btn",
                    active = bookmarkListActive,
                    onClick = onBookmarks,
                )
            } else {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White),
                )
            }
        }
    }
}

/**
 * Кэш растров страниц: 1/8 кучи приложения, размер считается в байтах битмапа.
 * На типовом телефоне это 3–5 страниц A4 — ровно диапазон из задания.
 */
private fun newPageBitmapCache(): LruCache<Int, Bitmap> {
    val maxBytes = (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024)
    return object : LruCache<Int, Bitmap>(maxBytes) {
        override fun sizeOf(
            key: Int,
            value: Bitmap,
        ): Int = value.byteCount
    }
}

/**
 * Одна страница: растр из кэша или рендер по мере появления. Плейсхолдер пропорций A4 —
 * иначе высота элемента скачет при дорисовке и список «прыгает» под пальцем.
 *
 * V7/V8/X33: pinch-to-zoom 1..4× с паном и двойным тапом-сбросом. При zoom==1 одиночный
 * палец — всегда скролл LazyColumn. При zoom>1 одиночный палец СНАЧАЛА двигает pan страницы
 * (consume), а на границе пана события НЕ потребляем — жест утекает в список и листает
 * документ (X33: «сначала пан внутри зума, у границы — страница»). Горизонталь при зуме
 * потребляем всегда. Зум-out ниже 1.05× дощёлкивает до ровно 1f и сбрасывает pan.
 *
 * V9: поверх растра — AnnotationLayer (внутри graphicsLayer, штрихи масштабируются со страницей).
 */
@Composable
private fun PdfPageRow(
    renderer: PdfRenderer,
    pageIndex: Int,
    cache: LruCache<Int, Bitmap>,
    renderLock: Mutex,
    renderWidthPx: Int,
    bookmarked: Boolean,
    onBookmarkClick: (bookmarked: Boolean) -> Unit,
    strokes: List<Stroke>,
    drawMode: Boolean,
    drawColor: Int,
    drawWidthDp: Float,
    eraserActive: Boolean,
    keepZoom: Boolean,
    sharedZoomState: MutableFloatState,
    sharedPanState: MutableState<Offset>,
    onStrokeFinished: (Stroke) -> Unit,
    onStrokesReplaced: (List<Stroke>) -> Unit,
) {
    var bitmap by remember(pageIndex) { mutableStateOf(cache.get(pageIndex)) }
    // X34: keepZoom вкл → страница читает/пишет ОБЩЕЕ состояние (его же пишем в prefs),
    // выкл → своё remember(pageIndex): прежний сброс при уходе страницы из композиции.
    val localZoomState = remember(pageIndex) { mutableFloatStateOf(1f) }
    val localPanState = remember(pageIndex) { mutableStateOf(Offset.Zero) }
    val zoomState = if (keepZoom) sharedZoomState else localZoomState
    val panState = if (keepZoom) sharedPanState else localPanState
    var zoom by zoomState
    var pan by panState
    var currentStroke by remember(pageIndex) { mutableStateOf<Stroke?>(null) }
    var boxSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    // X5: жестовый closure AnnotationLayer не перезапускается на каждый штрих —
    // ластик обязан видеть АКТУАЛЬНЫЙ список (rememberUpdatedState).
    val latestStrokes by rememberUpdatedState(strokes)

    LaunchedEffect(pageIndex, renderer) {
        if (bitmap != null) return@LaunchedEffect
        val rendered =
            withContext(Dispatchers.Default) {
                renderLock.withLock { renderPageBitmap(renderer, pageIndex, renderWidthPx) }
            }
        if (rendered != null) {
            cache.put(pageIndex, rendered)
            bitmap = rendered
        }
    }

    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("pdf_page_$pageIndex")
            .onSizeChanged { boxSize = it }
            .pointerInput(pageIndex, keepZoom) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // X33: при zoom>1 одиночный палец СНУТРЫ панорамирует страницу; consume
                    // снимаем только на границе пана (movedY=false) — там жест забирает
                    // LazyColumn и листает документ. При zoom==1 одиночный палец — сразу
                    // скролл списка (handled=false, событий не потребляем).
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
                        // Nested scroll: consume только когда жест реально двигает страницу
                        // (вертикаль по границам) или зумит; горизонталь при зуме — всегда.
                        // Иначе НЕ consume → LazyColumn листает страницы.
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
            }
            .pointerInput(pageIndex, keepZoom) {
                // W16: двойной тап — сброс зума (жест заметнее кнопки, кнопку не дублируем)
                detectTapGestures(
                    onDoubleTap = {
                        zoom = 1f
                        pan = Offset.Zero
                    },
                )
            }
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = pan.x
                translationY = pan.y
            },
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Страница ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Плейсхолдер пропорций A4 (1:1.414) через aspectRatio, а НЕ «пиксели как dp»:
            // renderWidthPx — это пиксели, и .dp из него дал бы на плотном экране заглушку
            // втрое выше настоящей страницы, из-за чего список прыгал бы при дорисовке.
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f)
                    .background(ColorBgMain, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                )
            }
        }

        // V9: слой рисования — ВНУТРИ graphicsLayer страницы (штрихи масштабируются с зумом),
        // между растром и кнопками страницы. !drawMode — прозрачен для событий.
        AnnotationLayer(
            strokes = strokes,
            current = currentStroke,
            drawMode = drawMode,
            strokeColor = drawColor,
            strokeWidthDp = drawWidthDp,
            onCurrentChange = { currentStroke = it },
            onStrokeFinished = onStrokeFinished,
            eraserActive = eraserActive,
            transformKey = keepZoom,
            onEraseAt = { p ->
                // X5: hit-test в нормализованных координатах с учётом aspect бокса страницы;
                // порог на штрих = полутолщина + допуск, dp→px→нормаль по ширине.
                val bw = boxSize.width.toFloat()
                val bh = boxSize.height.toFloat()
                if (bw > 1f && bh > 1f) {
                    val aspect = bh / bw
                    val remaining =
                        latestStrokes.filterNot { st ->
                            val th = with(density) { (st.widthDp / 2f + ERASER_TOUCH_EXTRA_DP).dp.toPx() } / bw
                            eraserHitTest(st, p, th, aspect)
                        }
                    if (remaining.size != latestStrokes.size) onStrokesReplaced(remaining)
                }
            },
            transformGestures = { panChange, zoomChange ->
                val z = zoom
                val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                val maxX = max(0f, (newZoom - 1f) * boxSize.width / 2f)
                val maxY = max(0f, (newZoom - 1f) * boxSize.height / 2f)
                if (newZoom < 1.05f) {
                    zoom = 1f
                    pan = Offset.Zero
                } else {
                    zoom = newZoom
                    // X32: слой внутри graphicsLayer(scaleX/Y=zoom) — локальные координаты
                    // пальца в zoom раз мельче экранных. Умножаем pan на zoom, иначе пан
                    // «применялся», но двигал страницу в разы медленнее пальцев.
                    pan =
                        Offset(
                            (pan.x + panChange.x * z).coerceIn(-maxX, maxX),
                            (pan.y + panChange.y * z).coerceIn(-maxY, maxY),
                        )
                }
            },
            modifier = Modifier.matchParentSize(),
        )

        // V11: кнопка закладки 26dp (иконка 14dp) в ЛЕВОМ верхнем углу; повторный тап
        // по закрашенной убирает её (см. onBookmarkClick в MaterialPdfViewer)
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = if (bookmarked) ColorBrandBlue else ColorBgMain.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, ColorBorderLight),
            modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(26.dp)
                .bouncyClickable(onClick = { onBookmarkClick(bookmarked) })
                .testTag("pdf_bookmark_add_$pageIndex"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Закладка на странице ${pageIndex + 1}",
                    tint = if (bookmarked) Color.White else ColorBrandBlue,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Номер страницы — в правом верхнем углу (чтобы не пересекаться с закладкой)
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = ColorBgMain.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, ColorBorderLight),
            modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .testTag("pdf_page_num_$pageIndex"),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorTextBody),
                )
            }
        }
    }
}

/**
 * Рендер одной страницы. ВЫЗЫВАТЬ ПОД МЬЮТЕКСОМ: PdfRenderer допускает ровно одну открытую
 * страницу и не потокобезопасен. Сбой страницы (битый PDF) — null, страница останется плейсхолдером.
 */
private fun renderPageBitmap(
    renderer: PdfRenderer,
    pageIndex: Int,
    widthPx: Int,
): Bitmap? = runCatching {
    renderer.openPage(pageIndex).use { page ->
        val w = widthPx.coerceAtLeast(1)
        val h = (w.toFloat() * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.eraseColor(AndroidColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }
}.getOrNull()

/** M2: список закладок в top-bar — тап прыгает на страницу, крестик удаляет. */
@Composable
private fun BookmarkPanel(
    bookmarks: List<MaterialBookmarkEntity>,
    onJump: (Int) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("pdf_bookmark_panel"),
    ) {
        if (bookmarks.isEmpty()) {
            Text(
                text = "Закладок нет. Нажмите значок закладки на нужной странице.",
                style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                items(count = bookmarks.size, key = { bookmarks[it].id }) { i ->
                    val bm = bookmarks[i]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .bouncyClickable { onJump(bm.page) }
                            .padding(start = 12.dp, end = 2.dp)
                            .testTag("pdf_bookmark_${bm.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bm.label.ifBlank { "Страница ${bm.page + 1}" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitle),
                            )
                            Text(
                                text = "Стр. ${bm.page + 1}",
                                style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                            )
                        }
                        IconButton(onClick = { onDelete(bm.id) }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить закладку",
                                tint = ColorTextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddBookmarkDialog(
    page: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var label by remember { mutableStateOf("Страница ${page + 1}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = {
            Text(
                text = "Закладка на странице ${page + 1}",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle),
            )
        },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Подпись", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(2.dp),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("bookmark_label_field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label.trim()) }) {
                Text("СОХРАНИТЬ", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorBrandBlue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ОТМЕНА", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorTextBody))
            }
        },
    )
}

@Composable
private fun PageJumpDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var raw by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = {
            Text(
                text = "Перейти на страницу",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle),
            )
        },
        text = {
            OutlinedTextField(
                value = raw,
                onValueChange = { v -> raw = v.filter { it.isDigit() }.take(5) },
                label = { Text("Номер (1…$pageCount)", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(2.dp),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("page_jump_field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { (raw.toIntOrNull() ?: return@TextButton).let { onJump(it - 1) } }) {
                Text("ПЕРЕЙТИ", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorBrandBlue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ОТМЕНА", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorTextBody))
            }
        },
    )
}
