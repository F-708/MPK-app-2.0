package com.politex.ui.screens.materials

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialAnnotationEntity
import com.politex.data.local.entity.MaterialBookmarkEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.util.bouncyClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

// WebView-просмотр PDF (pdf.js) и DOCX (docx-preview) — рендер из gander (MIT),
// своя обвязка: READER-PLAN.md D3/D4/D6/D9. Security-инварианты — в комментарии к WebView.

/** Тул-состояние для колбэков порта: снимок UI-состояния, который читают JS-обработчики. */
private class InkToolState {
    @Volatile var color: Int = ANNOTATION_COLORS.first()
    @Volatile var widthDp: Float = 4f
    @Volatile var eraser: Boolean = false
    @Volatile var mode: Boolean = false

    @Volatile var viewerWidthPx: Float = 0f
    @Volatile var density: Float = 1f

    fun widthNorm(widthDp: Float): Float {
        val w = viewerWidthPx
        return if (w > 0f) widthDp * density / w else 0.01f
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MaterialWebViewer(
    item: MaterialItemEntity,
    dao: MaterialDao,
    onBack: () -> Unit,
    onFallbackNative: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPdf = item.ext == "pdf"
    val density = LocalDensity.current.density

    val tool = remember(item.id) { InkToolState() }
    LaunchedEffect(density) { tool.density = density }

    // ---------------------------------------------------------------- страницы / тулбар
    var pageAt by remember(item.id) { mutableIntStateOf(0) } // 0-based
    var pageTotal by remember(item.id) { mutableIntStateOf(0) }
    var drawMode by remember(item.id) { mutableStateOf(false) }
    var drawColor by remember(item.id) { mutableIntStateOf(tool.color) }
    var drawWidth by remember(item.id) { mutableFloatStateOf(tool.widthDp) }
    var eraserActive by remember(item.id) { mutableStateOf(false) }
    var showBookmarkList by remember(item.id) { mutableStateOf(false) }
    var showAddBookmark by remember(item.id) { mutableStateOf(false) }
    var showPageJump by remember(item.id) { mutableStateOf(false) }
    var rendererGone by remember(item.id) { mutableStateOf(false) }

    val undoSession = remember(item.id) { AnnotationUndoSession() }

    // ---------------------------------------------------------------- аннотации (Room, формат нативного PdfViewer)
    val annotationsFlow: Flow<List<MaterialAnnotationEntity>> =
        remember(item.id) { if (isPdf) dao.getAnnotationsFlow(item.id) else flowOf(emptyList()) }
    val bookmarksFlow: Flow<List<MaterialBookmarkEntity>> =
        remember(item.id) { if (isPdf) dao.getBookmarksFlow(item.id) else flowOf(emptyList()) }
    val annotations by annotationsFlow.collectAsState(initial = emptyList())
    val bookmarks by bookmarksFlow.collectAsState(initial = emptyList())

    val pageStrokes = remember(item.id) { mutableStateMapOf<Int, List<Stroke>>() }
    val dirtyPages = remember(item.id) { mutableStateListOf<Int>() }
    var saveVersion by remember(item.id) { mutableIntStateOf(0) }

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

    var flushing by remember(item.id) { mutableStateOf(false) }
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

    LaunchedEffect(rendererGone) { if (rendererGone) onFallbackNative() }

    // ---------------------------------------------------------------- порт / WebView
    val portRef = remember(item.id) { arrayOfNulls<WebMessagePortCompat>(1) }
    val webRef = remember(item.id) { arrayOfNulls<WebView>(1) }
    var portReady by remember(item.id) { mutableStateOf(false) }

    fun send(cmd: String) {
        runCatching { portRef[0]?.postMessage(WebMessageCompat(cmd)) }
    }

    fun sendStrokesPage(page0: Int) {
        send(PortCommand.inkStrokes(page0, inkWireJson(pageStrokes[page0].orEmpty()) { tool.widthNorm(it) }))
    }

    fun sendTool() {
        send(PortCommand.inkMode(tool.mode))
        send(PortCommand.inkTool(tool.color, tool.widthNorm(tool.widthDp)))
        send(PortCommand.inkEraser(tool.eraser))
    }

    fun jumpTo(page0: Int) {
        send(PortCommand.goToPage(pageToPort(page0)))
    }

    fun handlePort(data: String?) {
        when (val said = parsePortMessage(data)) {
            is PortMessage.Page -> {
                pageAt = pageFromPort(said.n)
                pageTotal = said.of
                scope.launch { dao.setLastPage(item.id, pageFromPort(said.n)) }
            }

            is PortMessage.Ink -> {
                val stroke = Stroke(tool.color, tool.widthDp, said.pts)
                undoSession.record(AnnotationAction.Add(said.page0, stroke))
                pageStrokes[said.page0] = pageStrokes[said.page0].orEmpty() + stroke
                markDirty(said.page0)
                sendStrokesPage(said.page0)
            }

            is PortMessage.Zap -> {
                val list = pageStrokes[said.page0].orEmpty()
                if (list.isEmpty()) return
                val threshold =
                    eraserThresholdNorm(tool.widthDp, ERASER_TOUCH_EXTRA_DP, tool.density, tool.viewerWidthPx.coerceAtLeast(1f), 1f)
                val idx = list.indexOfLast { eraserHitTest(it, said.at, threshold, said.aspect) }
                if (idx < 0) return
                undoSession.record(AnnotationAction.Erase(said.page0, idx, list[idx]))
                pageStrokes[said.page0] = list.filterIndexed { i, _ -> i != idx }
                markDirty(said.page0)
                sendStrokesPage(said.page0)
            }

            PortMessage.Locked -> { rendererGone = true }
            is PortMessage.SearchCount -> Unit
            null -> Unit
        }
    }

    fun openPort(w: WebView) {
        if (portRef[0] != null) return
        val ends = WebViewCompat.createWebMessageChannel(w)
        ends[0].setWebMessageCallback(
            Handler(Looper.getMainLooper()),
            object : WebMessagePortCompat.WebMessageCallbackCompat() {
                override fun onMessage(port: WebMessagePortCompat, message: WebMessageCompat?) {
                    handlePort(message?.data)
                }
            },
        )
        portRef[0] = ends[0]
        // Порт адресуется своему origin, не «*»: его не должна получить чужая страница.
        WebViewCompat.postWebMessage(w, WebMessageCompat("vw-search-port", arrayOf(ends[1])), Uri.parse("https://$ASSET_HOST"))
        portReady = true
        sendTool()
        pageStrokes.keys.toList().forEach { sendStrokesPage(it) }
    }

    // синхронизация тула с JS-слоем
    LaunchedEffect(drawMode, drawColor, drawWidth, eraserActive) {
        tool.mode = drawMode
        tool.color = drawColor
        tool.widthDp = drawWidth
        tool.eraser = eraserActive
        if (portReady) sendTool()
    }

    // пуш канонических штрихов, когда Room догнал локальные правки
    LaunchedEffect(annotations, portReady) {
        if (portReady) pageStrokes.keys.toList().forEach { sendStrokesPage(it) }
    }

    // ---------------------------------------------------------------- UI
    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        MaterialViewerTopBar(title = item.title, testTag = if (isPdf) "web_pdf_top_bar" else "web_docx_top_bar", onBack = flushAndBack) {
            if (pageTotal > 0) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    modifier =
                        Modifier
                            .heightIn(min = 44.dp)
                            .bouncyClickable(onClick = { showPageJump = true })
                            .testTag("web_page_indicator"),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            "${pageAt + 1}/$pageTotal",
                            maxLines = 1,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White),
                        )
                    }
                }
            }
            if (isPdf) {
                ViewerAction(
                    icon = Icons.Default.BookmarkAdd,
                    description = "Закладка на этой странице",
                    testTag = "web_add_bookmark_btn",
                ) { showAddBookmark = true }
                ViewerAction(
                    icon = Icons.Default.Bookmark,
                    description = "Закладки",
                    testTag = "web_bookmarks_btn",
                    active = showBookmarkList,
                ) { showBookmarkList = !showBookmarkList }
                ViewerAction(
                    icon = Icons.Default.Edit,
                    description = "Рисование",
                    testTag = "web_draw_btn",
                    active = drawMode,
                ) {
                    drawMode = !drawMode
                    if (drawMode) showBookmarkList = false else eraserActive = false
                }
            }
        }

        if (drawMode && isPdf) {
            AnnotationToolbar(
                color = drawColor,
                widthDp = drawWidth,
                eraserActive = eraserActive,
                onEraserToggle = { eraserActive = !eraserActive },
                onColor = { drawColor = it },
                onWidth = { drawWidth = it },
                onUndo = {
                    val action = undoSession.pop()
                    if (action != null) {
                        val updated = applyUndo(action, pageStrokes.toMap())
                        updated.forEach { (p, list) ->
                            if (pageStrokes[p] != list) {
                                pageStrokes[p] = list
                                markDirty(p)
                                sendStrokesPage(p)
                            }
                        }
                    }
                },
                onClear = {
                    val snapshot = pageStrokes.toMap().filterValues { it.isNotEmpty() }
                    if (snapshot.isNotEmpty()) {
                        undoSession.record(AnnotationAction.ClearAll(snapshot))
                        snapshot.keys.forEach { p ->
                            pageStrokes[p] = emptyList()
                            markDirty(p)
                            sendStrokesPage(p)
                        }
                    }
                },
                onExit = { drawMode = false },
            )
        }

        if (showBookmarkList && isPdf) {
            BookmarkPanel(
                bookmarks = bookmarks,
                onJump = { page ->
                    showBookmarkList = false
                    jumpTo(page)
                },
                onDelete = { id -> scope.launch { dao.deleteBookmarkById(id) } },
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        tool.viewerWidthPx = it.width.toFloat()
                    },
        ) {
            key(item.id) {
                AndroidView(
                    factory = { ctx -> createDocWebView(ctx, item, isPdf, webRef, ::openPort) { rendererGone = true } },
                    onRelease = { v ->
                        runCatching { (v.parent as? ViewGroup)?.removeView(v) }
                        runCatching { v.destroy() }
                        webRef[0] = null
                        portRef[0] = null
                    },
                    modifier = Modifier.fillMaxSize().testTag(if (isPdf) "web_pdf_view" else "web_docx_view"),
                )
            }
        }
    }

    if (showPageJump && pageTotal > 0) {
        PageJumpDialog(
            pageCount = pageTotal,
            onDismiss = { showPageJump = false },
            onJump = { page0 ->
                showPageJump = false
                jumpTo(page0)
            },
        )
    }

    if (showAddBookmark) {
        AddBookmarkDialog(
            page = pageAt,
            onDismiss = { showAddBookmark = false },
            onConfirm = { label ->
                showAddBookmark = false
                scope.launch { dao.insertBookmark(MaterialBookmarkEntity(itemId = item.id, page = pageAt, label = label)) }
            },
        )
    }
}

// ---------------------------------------------------------------- WebView-фабрика (вне композиции)

private fun createDocWebView(
    ctx: android.content.Context,
    item: MaterialItemEntity,
    isPdf: Boolean,
    webRef: Array<WebView?>,
    openPort: (WebView) -> Unit,
    onRendererGone: () -> Unit,
): WebView {
    val file = File(item.uriPath)
    val mime = documentMime(item.ext)

    return WebView(ctx).apply {
        // Security-инварианты (READER-PLAN D9): JS включён ТОЛЬКО для локальных viewer-страниц;
        // всё остальное закрыто явно, а не по дефолту.
        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)

            @Suppress("DEPRECATION")
            setAllowFileAccessFromFileURLs(false)

            @Suppress("DEPRECATION")
            setAllowUniversalAccessFromFileURLs(false)
            // pdf.js TextLayer меряет 1px-пробник; пол 8 ломает выделение (gander issue #22).
            if (isPdf) minimumFontSize = 1
        }
        CookieManager.getInstance().also { cm ->
            cm.setAcceptCookie(false)
            cm.setAcceptThirdPartyCookies(this, false)
        }

        val assetLoader =
            WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                .build()

        webViewClient =
            object : WebViewClientCompat() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (isPdf) openPort(view)
                }

                override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest): WebResourceResponse? {
                    val url = request.url
                    if (url.scheme == "https" && url.host == ASSET_HOST) {
                        val path = url.path ?: return webNotFound()
                        if (path == "/doc/file." + item.ext) return docResponse(file, mime, request.requestHeaders["Range"])
                        // Узкий префикс + запрет «..»: JS не должен читать ничего вне viewer/
                        // (в admin-сборке рядом в assets лежит students.csv — READER-PLAN D9).
                        if (path.startsWith("/assets/viewer/") && ".." !in path) {
                            return assetLoader.shouldInterceptRequest(url)?.withViewerPolicy() ?: webNotFound()
                        }
                        return webNotFound()
                    }
                    // У MPK есть INTERNET: любая попытка страницы уйти в сеть гасится здесь (вторая стена).
                    return when (url.scheme) {
                        "http", "https" -> webNotFound()
                        else -> null
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                    val url = request.url
                    if (isViewerPage(url)) return false
                    // Ссылки документа (vwDisarmLinks оставляет http/mailto/tel) — наружу в браузер,
                    // в WebView не открывается ничего (QA HIGH: мёртвые ссылки — регресс).
                    if (url.scheme == "http" || url.scheme == "https" || url.scheme == "mailto" || url.scheme == "tel") {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    return true
                }

                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                    // Без true Android убивает ВЕСЬ процесс приложения. WebView после этого
                    // мёртв: снимаем, уничтожаем и отступаем в нативный viewer (READER-PLAN D3).
                    if (webRef[0] === view) {
                        webRef[0] = null
                        runCatching { (view.parent as? ViewGroup)?.removeView(view) }
                        view.destroy()
                        onRendererGone()
                    }
                    return true
                }
            }

        webRef[0] = this
        loadUrl(buildDocUrl(item, file))
    }
}

private fun buildDocUrl(item: MaterialItemEntity, file: File): String {
    val total = file.length()
    val ranged = if (useRanges(total)) 1 else 0
    // item.lastPage 0-based (нативный viewer) → resume 1-based, только если не первая (контракт gander).
    val resume = if (item.ext == "pdf" && item.lastPage > 0) pageToPort(item.lastPage) else 0
    return "https://$ASSET_HOST/assets/viewer/" + (if (item.ext == "pdf") "pdf.html" else "docx.html") +
        "?name=${Uri.encode(item.fileName.ifBlank { item.title })}&ext=${Uri.encode(item.ext)}&ranged=$ranged" +
        (if (total > 0) "&length=$total" else "") +
        "&night=0" +
        (if (resume > 1) "&resume=$resume" else "")
}

private fun docResponse(file: File, mime: String, range: String?): WebResourceResponse =
    try {
        val total = file.length()
        val rangeable = useRanges(total)
        val span = if (rangeable) range?.let { parseRange(it, total) } else null
        val nosniff = "X-Content-Type-Options" to "nosniff"
        if (span == null) {
            val headers = mutableMapOf(nosniff)
            if (rangeable) headers["Accept-Ranges"] = "bytes"
            if (total >= 0) headers["Content-Length"] = total.toString()
            WebResourceResponse(mime, null, 200, "OK", headers, file.inputStream())
        } else {
            val (start, end) = span
            WebResourceResponse(
                mime,
                null,
                206,
                "Partial Content",
                mapOf(
                    nosniff,
                    "Accept-Ranges" to "bytes",
                    "Content-Range" to "bytes $start-$end/$total",
                    "Content-Length" to (end - start + 1).toString(),
                ),
                fileSlice(file, start, end),
            )
        }
    } catch (e: Exception) {
        webNotFound()
    }

/** CSP-заголовок на КАЖДЫЙ asset-ответ: только так политика доезжает до worker'а pdf.js. */
private fun WebResourceResponse.withViewerPolicy(): WebResourceResponse =
    apply { responseHeaders = responseHeaders.orEmpty() + ("Content-Security-Policy" to ViewerPolicy.CSP) }

private fun webNotFound(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, ByteArrayInputStream(ByteArray(0)))

private fun isViewerPage(url: Uri): Boolean {
    val path = url.path ?: return false
    return url.scheme == "https" && url.host == ASSET_HOST &&
        path.startsWith("/assets/viewer/") && path.endsWith(".html") && ".." !in path
}
