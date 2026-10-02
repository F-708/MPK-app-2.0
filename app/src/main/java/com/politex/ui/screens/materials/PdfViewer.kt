package com.politex.ui.screens.materials

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialBookmarkEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
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
        Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
            MaterialViewerTopBar(title = item.title, testTag = "pdf_top_bar", onBack = onBack)
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

    // M2: запоминаем первую видимую страницу — «где остановился» переживает смерть процесса.
    LaunchedEffect(listState, item.id) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { page -> dao.setLastPage(item.id, page) }
    }

    val currentPage = listState.firstVisibleItemIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    Column(modifier = modifier.fillMaxSize().background(ColorSurfaceHighlight)) {
        MaterialViewerTopBar(
            title = item.title,
            testTag = "pdf_top_bar",
            onBack = onBack,
        ) {
            ViewerAction(
                icon = Icons.Default.BookmarkBorder,
                description = "Закладки",
                testTag = "pdf_bookmarks_btn",
                active = showBookmarkList,
                onClick = { showBookmarkList = !showBookmarkList },
            )
            Spacer(modifier = Modifier.width(2.dp))
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = Color.Transparent,
                modifier =
                Modifier
                    .heightIn(min = 44.dp)
                    .bouncyClickable { showPageJump = true }
                    .testTag("pdf_page_indicator"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 10.dp),
                ) {
                    Text(
                        text = "${currentPage + 1}/$pageCount",
                        maxLines = 1,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
                    )
                }
            }
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

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                        onBookmarkClick = { bookmarkDraftPage = index },
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
 */
@Composable
private fun PdfPageRow(
    renderer: PdfRenderer,
    pageIndex: Int,
    cache: LruCache<Int, Bitmap>,
    renderLock: Mutex,
    renderWidthPx: Int,
    bookmarked: Boolean,
    onBookmarkClick: () -> Unit,
) {
    var bitmap by remember(pageIndex) { mutableStateOf(cache.get(pageIndex)) }

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
            .testTag("pdf_page_$pageIndex"),
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

        // Номер страницы + закладка поверх: хитбокс 44dp, палец попадает без прицеливания
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBgMain.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, ColorBorderLight),
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
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = if (bookmarked) ColorBrandBlue else ColorBgMain.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier =
                Modifier
                    .size(44.dp)
                    .bouncyClickable(onClick = onBookmarkClick)
                    .testTag("pdf_bookmark_add_$pageIndex"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Закладка на странице ${pageIndex + 1}",
                        tint = if (bookmarked) Color.White else ColorBrandBlue,
                        modifier = Modifier.size(18.dp),
                    )
                }
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
