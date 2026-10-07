package com.politex.ui.screens.materials

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.compose.ui.geometry.Offset
import androidx.webkit.WebViewCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

// Мост и чистая логика WebView-просмотра PDF/DOCX.
// Порты из gander (MIT, tools/reader/gander): DocRange.kt, PortMessage.kt, ViewerPolicy.kt,
// WebViewFloor.kt — см. READER-PLAN.md (D6/D7/D9), лицензии: assets/viewer-licences.md.

internal const val ASSET_HOST = "appassets.androidplatform.net"

/** CSP каждой viewer-страницы. Дословно совпадает с meta-тегом html-страниц в assets/viewer (ViewerAssetsTest). */
internal object ViewerPolicy {
    const val CSP =
        "default-src 'none'; " +
            "script-src 'self' 'wasm-unsafe-eval'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob:; " +
            "font-src 'self' data: blob:; " +
            "media-src 'self' data: blob:; " +
            "connect-src 'self'; " +
            "worker-src 'self'; " +
            "frame-src 'none'; " +
            "object-src 'none'; " +
            "form-action 'none'; " +
            "base-uri 'none'"
}

// ---------------------------------------------------------------- DocRange (локальные файлы)

/** Выше этого документ отдаётся диапазонами, а не читается целиком (порог памяти, не скорости). */
internal const val RANGE_THRESHOLD_BYTES = 16L * 1024 * 1024

internal fun useRanges(total: Long): Boolean = total >= RANGE_THRESHOLD_BYTES

/** «bytes=start-end» против известного total. Null = отдать целиком (неразбор/неудовлетворимый диапазон). */
internal fun parseRange(header: String, total: Long): Pair<Long, Long>? {
    if (total <= 0) return null
    val spec = header.substringAfter("bytes=", "").substringBefore(',').trim()
    if (spec.isEmpty()) return null
    val start = spec.substringBefore('-').trim().toLongOrNull() ?: return null
    val end = spec.substringAfter('-').trim().toLongOrNull() ?: (total - 1)
    if (start < 0 || start > end || start >= total) return null
    return start to minOf(end, total - 1)
}

internal fun documentMime(ext: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"

/** Читает не больше [remaining] байт и закрывает источник вместе с [alsoClose]. */
internal class LimitedInputStream(
    private val source: InputStream,
    private var remaining: Long,
    private val alsoClose: Closeable,
) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0) return -1
        return source.read().also { if (it >= 0) remaining-- }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val n = source.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (n > 0) remaining -= n
        return n
    }

    override fun available(): Int = minOf(source.available().toLong(), remaining).toInt()

    override fun close() {
        runCatching { source.close() }
        runCatching { alsoClose.close() }
    }
}

/** Диапазон [start..end] локального файла как поток (RandomAccessFile, без skip-петли). */
internal fun fileSlice(file: File, start: Long, end: Long): InputStream {
    val raf = RandomAccessFile(file, "r")
    raf.seek(start)
    val input = object : InputStream() {
        override fun read(): Int = raf.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len)
    }
    return LimitedInputStream(input, end - start + 1, raf)
}

// ---------------------------------------------------------------- WebViewFloor (срезан до PDF/DOCX)

internal const val PDFJS_MIN_CHROMIUM = 125
internal const val DOCX_PREVIEW_MIN_CHROMIUM = 80
internal const val PLAUSIBLE_CHROMIUM_MAJOR = 30
internal val CHROME_TOKEN = Regex("""Chrome/(\d+)""")

/** Провайдеры, которых нельзя заменить (Huawei без Google-сервисов); версия у них не про Chromium. */
internal val LOCKED_WEBVIEW_PACKAGES = setOf("com.huawei.webview")

/** Chromium major из user agent (Chrome/NNN), иначе из versionName пакета. Null = не удалось прочесть. */
internal fun chromiumMajor(userAgent: String?, packageVersionName: () -> String?): Int? {
    val fromUa = userAgent
        ?.let { CHROME_TOKEN.find(it) }
        ?.groupValues?.get(1)
        ?.toIntOrNull()
        ?.takeIf { it >= PLAUSIBLE_CHROMIUM_MAJOR }
    return fromUa ?: packageVersionName()
        ?.substringBefore('.')
        ?.toIntOrNull()
        ?.takeIf { it >= PLAUSIBLE_CHROMIUM_MAJOR }
}

/** major/locked текущего WebView-провайдера; в Robolectric/без провайдера → (null, false). */
internal fun webViewEngineInfo(context: Context): Pair<Int?, Boolean> {
    val pkg = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
    return chromiumMajor(null) { pkg?.versionName } to (pkg?.packageName in LOCKED_WEBVIEW_PACKAGES)
}

/**
 * PDF в WebView только когда движок точно достаточно новый и файл целый.
 * Null major трактуется КОНСЕРВАТИВНО (нативный PdfRenderer) — в отличие от gander:
 * у MPK нативный путь полноценный (аннотации), а не «плашка обнови WebView».
 */
internal fun pdfRouteWeb(fileExists: Boolean, pageCount: Int, major: Int?, locked: Boolean): Boolean =
    fileExists && pageCount > 0 && !locked && major != null && major >= PDFJS_MIN_CHROMIUM

/** DOCX в WebView, если не правлен (textEdited показываем плоским текстом из БД) и движок не древний. */
internal fun docxRouteWeb(textEdited: Boolean, fileExists: Boolean, major: Int?): Boolean =
    !textEdited && fileExists && (major == null || major >= DOCX_PREVIEW_MIN_CHROMIUM)

// ---------------------------------------------------------------- Индексы страниц

// QA CRITICAL #2: БД/закладки/аннотации MPK 0-based, порт gander 1-based. Конверсия ТОЛЬКО здесь.
internal fun pageToPort(zeroBased: Int): Int = zeroBased + 1

internal fun pageFromPort(oneBased: Int): Int = oneBased - 1

// ---------------------------------------------------------------- Протокол порта

/** Что шлёт native. Одна буква verb, затем payload (у gander: q/n/p/c/g/i; ink-глаголы MPK: M/S/E/K). */
internal object PortCommand {
    fun query(q: String) = "q$q"
    fun next() = "n"
    fun prev() = "p"
    fun clear() = "c"

    /** К странице, 1-based. */
    fun goToPage(n: Int) = "g$n"

    fun nightMode(on: Boolean) = if (on) "i1" else "i0"

    /** Режим рисования: JS перехватывает pointer-события страниц. */
    fun inkMode(on: Boolean) = "M" + if (on) "1" else "0"

    /** Текущий инструмент: цвет ARGB (signed int) и толщина, нормированная к ширине страницы. */
    fun inkTool(colorArgb: Int, widthNorm: Float) = "S $colorArgb $widthNorm"

    fun inkEraser(on: Boolean) = "E" + if (on) "1" else "0"

    /** Канонические штрихи страницы (0-based): JSON [{"c":argb,"w":norm,"pts":[x,y,…]}]. */
    fun inkStrokes(page0: Int, json: String) = "K$page0 $json"
}

/** Что прислала страница, уже прочитанное и признанное годным. */
internal sealed interface PortMessage {
    /** Страница под серединой экрана, 1-based (формат gander «page N OF»). */
    data class Page(val n: Int, val of: Int) : PortMessage

    data class SearchCount(val at: Int, val total: Int, val done: Boolean) : PortMessage

    /** PDF запросил пароль: WebView-путь отступает в нативный viewer (plaque/поведение как раньше). */
    data object Locked : PortMessage

    /** Готовый штрих с overlay-канваса: страница 0-based + нормализованные 0..1 точки. */
    data class Ink(val page0: Int, val pts: List<Offset>) : PortMessage

    /** Точка ластика (норм. 0..1) + aspect страницы (h/w) для изотропного hit-test. */
    data class Zap(val page0: Int, val at: Offset, val aspect: Float) : PortMessage
}

/**
 * Граница безопасности: всё, что говорит документ, читается как маленькая фиксированная
 * форма или молча дропается. Ошибок наружу нет намеренно (порт из gander, расширен ink).
 */
internal fun parsePortMessage(data: String?): PortMessage? {
    if (data == "locked") return PortMessage.Locked
    val said = data?.split(" ") ?: return null

    // «ink <page0> <x1> <y1> <x2> <y2> …» — нормализованные 0..1 координаты.
    if (said.firstOrNull() == "ink") {
        if (said.size < 4 || said.size % 2 != 0) return null
        val page0 = said[1].toIntOrNull() ?: return null
        if (page0 < 0) return null
        val pts = ArrayList<Offset>((said.size - 2) / 2)
        for (i in 2 until said.size step 2) {
            val x = said[i].toFloatOrNull() ?: return null
            val y = said[i + 1].toFloatOrNull() ?: return null
            if (!x.isFinite() || !y.isFinite()) return null
            pts.add(Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)))
        }
        return PortMessage.Ink(page0, pts)
    }

    // «zap <page0> <x> <y> <aspect=h/w>» — точка ластика.
    if (said.firstOrNull() == "zap") {
        if (said.size != 5) return null
        val page0 = said[1].toIntOrNull() ?: return null
        val x = said[2].toFloatOrNull() ?: return null
        val y = said[3].toFloatOrNull() ?: return null
        val aspect = said[4].toFloatOrNull() ?: return null
        if (page0 < 0 || !x.isFinite() || !y.isFinite() || !aspect.isFinite() || aspect <= 0f) return null
        return PortMessage.Zap(page0, Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)), aspect)
    }

    if (said.size != 3) return null

    if (said[0] == "page") {
        val n = said[1].toIntOrNull() ?: return null
        val of = said[2].toIntOrNull() ?: return null
        if (n < 1 || of < 1 || n > of) return null
        return PortMessage.Page(n, of)
    }

    val at = said[0].toIntOrNull() ?: return null
    val total = said[1].toIntOrNull() ?: return null
    if (at < 0 || total < 0) return null
    return PortMessage.SearchCount(at, total, said[2] == "1")
}

/**
 * Штрихи страницы в wire-формат mpk-ink.js: [{"c":argb,"w":<норм. к ширине страницы>,"pts":[x,y,…]}].
 * [widthNorm] переводит хранимую widthDp в безразмерную долю ширины страницы — JS умножит на
 * ширину своего page-бокса (в CSS px), и толщина останется постоянной в экранных dp.
 */
internal fun inkWireJson(strokes: List<Stroke>, widthNorm: (Float) -> Float): String {
    val arr = JSONArray()
    strokes.forEach { s ->
        val pts = JSONArray()
        s.pts.forEach {
            pts.put(it.x.toDouble())
            pts.put(it.y.toDouble())
        }
        arr.put(JSONObject().put("c", s.color).put("w", widthNorm(s.widthDp).toDouble()).put("pts", pts))
    }
    return arr.toString()
}
