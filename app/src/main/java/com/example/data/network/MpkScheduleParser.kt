package com.example.data.network

import com.example.data.local.entity.LessonEntity
import com.example.data.model.CollegeBellSchedule
import com.example.util.GroupParser
import com.example.util.SubjectFormatter
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Структура ссылки на запись расписания на сайте колледжа.
 */
data class SchedulePostLink(
    val url: String,
    val title: String,
    val dateString: String = ""
)

/**
 * Статистика последнего парсинга — для панели диагностики в настройках.
 * Показывает, в каком формате пришел документ и какая стратегия сработала.
 */
data class ParseStats(
    val format: String = "",      // doc-utf16 / doc-cp1251 / docx / html / text
    val strategy: String = "",    // grid / cells / line / docx-xml
    val runsExtracted: Int = 0,
    val groupFound: Boolean = false,
    val wasHtml: Boolean = false,
    /** Ошибки стратегий разбора: «grid:IllegalState...; cells:...» — пусто, если всё чисто */
    val parseError: String = "",
    /** Первые символы извлечённого текста — видно, что именно видит парсер */
    val textPreview: String = ""
)

/**
 * Парсер расписания занятий ГУО «МГПК» с официального сайта guo-mpk.by.
 *
 * Архитектурные возможности:
 * 1. Сканирование главной страницы https://guo-mpk.by/raspisanie/ для поиска записей дат
 * 2. Извлечение ссылок из <iframe src="view.officeapps.live.com/op/embed.aspx?src=..."> с декодированием URL
 * 3. Отказоустойчивый потоковый экстрактор текста из бинарных файлов .doc (Word 97-2004 OLE2) в UTF-16LE и CP1251
 * 4. Парсинг современных .docx через ZipInputStream и word/document.xml
 * 5. ТРИ стратегии разбора текста, от точной к грубой:
 *    a) grid  — таблица с разделителями │ / | / таб (эталонный формат документов сайта)
 *    b) cells — позиционное восстановление по пронумерованным ячейкам БЕЗ разделителей
 *         (страховка на случай любых искажений текста: работают даже если рамки потеряны)
 *    c) line  — построчный разбор произвольного текста (вставка из Telegram и т.п.)
 * 6. Распознавание 2 подгрупп и 2 кабинетов (каб. 215 / каб. 308) с разметкой 50/50
 * 7. Нормализация дисциплин строго по MpkCurriculum через SubjectFormatter.normalize()
 */
object MpkScheduleParser {

    /** Статистика последнего вызова parseFile/parsePlainTextSchedule (для диагностики). */
    @Volatile
    var lastParseStats: ParseStats = ParseStats()

    private fun resetStats(format: String) {
        lastParseStats = ParseStats(format = format)
    }

    private val CP1251 = Charset.forName("windows-1251")

    val DAY_KEYWORDS = mapOf(
        "понедельник" to 1,
        "вторник" to 2,
        "среда" to 3,
        "четверг" to 4,
        "пятница" to 5,
        "суббота" to 6
    )

    private val POST_LINK_REGEX = Regex(
        "<a[^>]+href=[\"']([^\"']*(?:raspisanie|uchashhihsya|[0-9]{2}[.-][0-9]{2}[.-][0-9]{4})[^\"']*)[\"'][^>]*>(.*?)</a>",
        RegexOption.IGNORE_CASE
    )

    private val DOC_LINK_REGEX = Regex(
        "(?i)href=[\"']([^\"']+\\.(?:docx|doc)(?:\\?[^\"']*)?)[\"']"
    )

    private val IFRAME_SRC_REGEX = Regex(
        "(?i)<iframe[^>]+src=[\"']([^\"']+)[\"']"
    )

    private val OFFICE_VIEWER_SRC_REGEX = Regex(
        "(?i)src=(https?%3A%2F%2F[^&\"']+|https?://[^&\"']+)"
    )

    private val DATE_IN_TEXT_REGEX = Regex(
        "([0-3]?[0-9])[.\\-/]([0-1]?[0-9])[.\\-/](202[0-9])"
    )

    /**
     * Шаг А: Сканирует HTML / JSON главной страницы расписания и находит ссылки на события дат.
     */
    fun findSchedulePostLinks(html: String, baseUrl: String = "https://guo-mpk.by/"): List<SchedulePostLink> {
        val result = mutableListOf<SchedulePostLink>()
        val seenUrls = mutableSetOf<String>()

        // 1. Поиск ссылок в HTML тегах <a href="...">
        POST_LINK_REGEX.findAll(html).forEach { match ->
            val rawUrl = match.groupValues[1].trim()
            val rawTitle = match.groupValues[2].replace(Regex("<[^>]+>"), " ").trim()
            val absoluteUrl = resolveAbsoluteUrl(baseUrl, rawUrl)

            if (!seenUrls.contains(absoluteUrl) && !absoluteUrl.endsWith("/raspisanie/") && !absoluteUrl.endsWith("/raspisanie")) {
                seenUrls.add(absoluteUrl)

                val dateMatch = DATE_IN_TEXT_REGEX.find(rawTitle) ?: DATE_IN_TEXT_REGEX.find(rawUrl)
                val dateString = if (dateMatch != null) {
                    val d = dateMatch.groupValues[1].padStart(2, '0')
                    val m = dateMatch.groupValues[2].padStart(2, '0')
                    val y = dateMatch.groupValues[3]
                    "$d.$m.$y"
                } else ""

                result.add(
                    SchedulePostLink(
                        url = absoluteUrl,
                        title = rawTitle.ifBlank { "Расписание учащихся" },
                        dateString = dateString
                    )
                )
            }
        }

        // 2. Поиск ссылок в JSON ответах WordPress REST API ("link": "...")
        Regex("\"link\"\\s*:\\s*\"([^\"]+)\"").findAll(html).forEach { match ->
            val rawUrl = match.groupValues[1].replace("\\/", "/")
            val absoluteUrl = resolveAbsoluteUrl(baseUrl, rawUrl)
            if (!seenUrls.contains(absoluteUrl) && !absoluteUrl.endsWith("/raspisanie/")) {
                seenUrls.add(absoluteUrl)
                val dateMatch = DATE_IN_TEXT_REGEX.find(absoluteUrl)
                val dateString = if (dateMatch != null) {
                    val d = dateMatch.groupValues[1].padStart(2, '0')
                    val m = dateMatch.groupValues[2].padStart(2, '0')
                    val y = dateMatch.groupValues[3]
                    "$d.$m.$y"
                } else ""

                result.add(
                    SchedulePostLink(
                        url = absoluteUrl,
                        title = "Расписание",
                        dateString = dateString
                    )
                )
            }
        }

        return result
    }

    /**
     * Шаг Б: Загружает HTML / JSON страницы и извлекает все ссылки на документы (.doc / .docx / uploads).
     * Поддерживает декодирование iframe с view.officeapps.live.com, drive.google.com, тегов <a> и JSON source_url.
     */
    fun extractDocumentUrls(html: String, baseUrl: String = "https://guo-mpk.by/"): List<String> {
        val urls = mutableSetOf<String>()

        // 1. Проверяем iframe со встроенным Office Viewer (view.officeapps.live.com/op/embed.aspx?src=...)
        IFRAME_SRC_REGEX.findAll(html).forEach { match ->
            val iframeSrc = match.groupValues[1]
            OFFICE_VIEWER_SRC_REGEX.find(iframeSrc)?.let { officeMatch ->
                var rawUrl = officeMatch.groupValues[1]
                if (rawUrl.contains("%")) {
                    try {
                        rawUrl = URLDecoder.decode(rawUrl, "UTF-8")
                    } catch (_: Exception) {}
                }
                urls.add(resolveAbsoluteUrl(baseUrl, rawUrl))
            }
        }

        // 2. Прямые ссылки на .doc / .docx файлы в тексте / HTML (исключая ссылки просмотрщиков)
        Regex("(?i)(?:href|src|data-src|data-href)=[\"']([^\"']+\\.(?:docx|doc)(?:\\?[^\"']*)?)[\"']").findAll(html).forEach { match ->
            val link = match.groupValues[1]
            if (!link.contains("officeapps.live.com") && !link.contains("docs.google.com")) {
                urls.add(resolveAbsoluteUrl(baseUrl, link))
            }
        }

        // 3. Ссылки в JSON (source_url, guid) WordPress
        Regex("(?i)\"(?:source_url|guid|url)\"\\s*:\\s*\"([^\"]+\\.(?:docx|doc)[^\"]*)\"").findAll(html).forEach { match ->
            val link = match.groupValues[1].replace("\\/", "/")
            urls.add(resolveAbsoluteUrl(baseUrl, link))
        }

        // 4. Ссылки на файлы в блоках wp-content/uploads
        Regex("(?i)https?://[a-zA-Z0-9.-]+/wp-content/uploads/[a-zA-Z0-9/_.-]+\\.(?:doc|docx)").findAll(html).forEach { match ->
            urls.add(match.value)
        }

        return urls.toList()
    }

    /**
     * Универсальный метод парсинга файла: автоматически определяет формат (.docx vs .doc vs HTML) и разбирает данные.
     */
    fun parseFile(
        bytes: ByteArray,
        targetGroup: String,
        targetDate: String = ""
    ): List<LessonEntity> {
        if (bytes.isEmpty()) return emptyList()

        // 0. Если сайт вернул HTML-страницу вместо документа (ошибка/защита) — честно фиксируем это
        val head = String(bytes.copyOfRange(0, minOf(600, bytes.size)), Charsets.UTF_8)
            .lowercase(Locale.ROOT)
        if (head.contains("<!doctype html") || head.contains("<html")) {
            lastParseStats = ParseStats(format = "html", wasHtml = true, groupFound = false)
            return emptyList()
        }

        // 1. Проверяем сигнатуру ZIP (DOCX: 50 4B 03 04)
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
            resetStats("docx")
            val docxLessons = parseDocx(ByteArrayInputStream(bytes), targetGroup, targetDate)
            if (docxLessons.isNotEmpty()) return docxLessons
        }

        // 2. Бинарный Word 97-2004 (.doc OLE2) потоковый экстрактор
        resetStats("doc")
        val docLessons = parseDocBinary(bytes, targetGroup, targetDate)
        if (docLessons.isNotEmpty()) return docLessons

        // Для настоящего бинарного OLE2 .doc текстовые фолбэки бессмысленны (обе кодировки
        // уже опробованы в parseDocBinary) — сохраняем статистику doc-этапа, включая ошибки
        val isOleDoc = bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
        if (isOleDoc) return emptyList()

        // 3. Fallback: разбор как плоского текста в UTF-8 / CP1251
        resetStats("text")
        val textUtf8 = String(bytes, Charsets.UTF_8)
        val textLessons = runCatching { parsePlainTextSchedule(textUtf8, targetGroup, targetDate) }
            .onFailure {
                lastParseStats = lastParseStats.copy(
                    parseError = (lastParseStats.parseError + "; text:${it.javaClass.simpleName}: ${it.message}").trim(';', ' ')
                )
            }
            .getOrDefault(emptyList())
        if (textLessons.isNotEmpty()) return textLessons

        resetStats("text-cp1251")
        val textCp1251 = String(bytes, CP1251)
        return runCatching { parsePlainTextSchedule(textCp1251, targetGroup, targetDate) }
            .onFailure {
                lastParseStats = lastParseStats.copy(
                    parseError = (lastParseStats.parseError + "; cp1251-text:${it.javaClass.simpleName}: ${it.message}").trim(';', ' ')
                )
            }
            .getOrDefault(emptyList())
    }

    /**
     * Шаг В: Отказоустойчивый потоковый экстрактор текста из бинарных файлов .doc (Word 97-2004 OLE2).
     *
     * Современные документы guo-mpk.by хранят текст таблицы в UTF-16LE (с символами рамок │─┬┴),
     * поэтому UTF-16 извлекается ПЕРВЫМ. CP1251 — запасной путь для старых файлов (подход МПК v1):
     * он запускается только если в UTF-16 ничего не нашлось, чтобы бинарный мусор
     * OLE2-контейнера не засорял текст и не ломал определение дня недели.
     */
    fun parseDocBinary(
        bytes: ByteArray,
        targetGroup: String,
        targetDate: String = ""
    ): List<LessonEntity> {
        val utf16Runs = extractUtf16LeRuns(bytes)
        lastParseStats = lastParseStats.copy(format = "doc-utf16", runsExtracted = utf16Runs.size)
        val utf16Text = utf16Runs.joinToString("\n")
        if (utf16Text.isNotBlank()) {
            val utf16Lessons = runCatching { parsePlainTextSchedule(utf16Text, targetGroup, targetDate) }
                .onFailure {
                    lastParseStats = lastParseStats.copy(
                        parseError = (lastParseStats.parseError + "; utf16:${it.javaClass.simpleName}: ${it.message}").trim(';', ' ')
                    )
                }
                .getOrDefault(emptyList())
            if (utf16Lessons.isNotEmpty()) return utf16Lessons
        }

        // Запасная кодировка для старых файлов. ВАЖНО: статистику основного (UTF-16) этапа
        // не затираем, если CP1251 ничего не дал — иначе диагностика теряет главные улики.
        val utf16Stats = lastParseStats.copy()
        val cp1251Runs = extractCp1251Runs(bytes)
        lastParseStats = lastParseStats.copy(format = "doc-cp1251", runsExtracted = cp1251Runs.size)
        val cp1251Text = cp1251Runs.joinToString("\n")
        if (cp1251Text.isNotBlank()) {
            val cp1251Lessons = runCatching { parsePlainTextSchedule(cp1251Text, targetGroup, targetDate) }
                .onFailure {
                    lastParseStats = lastParseStats.copy(
                        parseError = (lastParseStats.parseError + "; cp1251:${it.javaClass.simpleName}: ${it.message}").trim(';', ' ')
                    )
                }
                .getOrDefault(emptyList())
            if (cp1251Lessons.isNotEmpty()) return cp1251Lessons
        }
        // Ничего не нашли: возвращаем статистику UTF-16 этапа (она информативнее)
        if (lastParseStats.parseError.isBlank() && lastParseStats.textPreview.isBlank()) {
            lastParseStats = utf16Stats
        } else if (utf16Stats.textPreview.isNotBlank()) {
            lastParseStats = lastParseStats.copy(
                textPreview = utf16Stats.textPreview,
                runsExtracted = utf16Stats.runsExtracted
            )
        }

        return emptyList()
    }

    /**
     * Извлекает непрерывные последовательности печатных символов CP1251.
     */
    private fun extractCp1251Runs(bytes: ByteArray): List<String> {
        val runs = mutableListOf<String>()
        val buffer = mutableListOf<Byte>()

        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            // Символы: пробелы, табы, переводы строк, ячейка Word (0x07), печатный ASCII (0x20..0x7E), CP1251 кириллица (0x80..0xFF)
            val isValid = (unsigned in 0x20..0x7E) ||
                    (unsigned in 0xC0..0xFF) || // Кириллица А..я
                    unsigned == 0xA8 || unsigned == 0xB8 || // Ё, ё
                    unsigned == 0x09 || unsigned == 0x0A || unsigned == 0x0D || unsigned == 0x07

            if (isValid) {
                if (unsigned == 0x07) {
                    // Разделитель ячейки в Word 97
                    buffer.add(0x09.toByte())
                } else {
                    buffer.add(b)
                }
            } else {
                if (buffer.size >= 4) {
                    val decoded = String(buffer.toByteArray(), CP1251)
                    if (decoded.any { it in 'А'..'я' || it in 'A'..'z' || it in '0'..'9' }) {
                        runs.add(decoded)
                    }
                }
                buffer.clear()
            }
        }

        if (buffer.size >= 4) {
            val decoded = String(buffer.toByteArray(), CP1251)
            runs.add(decoded)
        }

        return runs
    }

    /**
     * Извлекает последовательности UTF-16LE.
     * Публичный для тестов: позволяет эмулировать потерю символов рамок.
     */
    fun extractUtf16LeRuns(bytes: ByteArray): List<String> {
        val runs = mutableListOf<String>()
        val buffer = mutableListOf<Byte>()

        var i = 0
        while (i < bytes.size - 1) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF

            // ASCII в UTF-16LE: b1 == 0, b0 in 0x20..0x7E / 0x09 / 0x0A / 0x0D / 0x07
            // Кириллица в UTF-16LE: b1 == 4, b0 in 0x00..0xFF (U+0400..U+04FF)
            val isAscii = b1 == 0 && ((b0 in 0x20..0x7E) || b0 == 0x09 || b0 == 0x0A || b0 == 0x0D || b0 == 0x07)
            val isCyrillic = (b1 == 4 && b0 in 0x00..0x5F) || (b1 == 4 && (b0 == 0x01 || b0 == 0x51))
            // Символы рамок таблицы Word (U+2500..U+25FF: │ ─ ┬ ┴ ├ ┤ ┌ ┐ └ ┘).
            // КРИТИЧНО: без них таблица расписания guo-mpk.by рассыпается на отдельные
            // ячейки без разделителей, и колонка группы теряется.
            val isBoxDrawing = b1 == 0x25

            if (isAscii || isCyrillic || isBoxDrawing) {
                if (b1 == 0 && b0 == 0x07) {
                    buffer.add(0x09.toByte())
                    buffer.add(0x00.toByte())
                } else {
                    buffer.add(bytes[i])
                    buffer.add(bytes[i + 1])
                }
                i += 2
            } else {
                if (buffer.size >= 6) {
                    val decoded = String(buffer.toByteArray(), Charsets.UTF_16LE)
                    // Пустые (пробельные) последовательности — это ПУСТЫЕ ЯЧЕЙКИ таблицы:
                    // сохраняем их как маркеры, чтобы колонки групп не съезжали при
                    // позиционном восстановлении таблицы без рамок.
                    if (decoded.any { it in 'А'..'я' || it in 'A'..'z' || it in '0'..'9' } ||
                        decoded.length >= 6 && decoded.all { it.isWhitespace() }
                    ) {
                        runs.add(decoded)
                    }
                }
                buffer.clear()
                i += 2
            }
        }

        if (buffer.size >= 6) {
            val decoded = String(buffer.toByteArray(), Charsets.UTF_16LE)
            runs.add(decoded)
        }

        return runs
    }

    /**
     * Шаг Г: Парсинг DOCX файла (через распаковку word/document.xml).
     */
    fun parseDocx(
        inputStream: InputStream,
        targetGroup: String,
        targetDate: String = ""
    ): List<LessonEntity> {
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        var documentXml: String? = null

        while (entry != null) {
            if (entry.name == "word/document.xml") {
                documentXml = zip.bufferedReader(Charsets.UTF_8).use { it.readText() }
                break
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        if (documentXml.isNullOrBlank()) {
            return emptyList()
        }

        return parseDocxXml(documentXml, targetGroup, targetDate)
    }

    /**
     * Парсинг XML разметки документа Word (таблицы расписания).
     */
    fun parseDocxXml(
        xmlContent: String,
        targetGroup: String,
        targetDate: String = ""
    ): List<LessonEntity> {
        val cleanTargetGroup = GroupParser.cleanRawGroupName(targetGroup).uppercase(Locale.ROOT)
        val lessons = mutableListOf<LessonEntity>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(xmlContent.reader())

            var inTable = false
            var inRow = false
            var inCell = false
            var currentCellText = StringBuilder()
            val rowCells = mutableListOf<String>()
            val allRows = mutableListOf<List<String>>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "tbl" -> {
                                inTable = true
                                allRows.clear()
                            }
                            "tr" -> {
                                inRow = true
                                rowCells.clear()
                            }
                            "tc" -> {
                                inCell = true
                                currentCellText = StringBuilder()
                            }
                            "t" -> {
                                if (inCell) {
                                    parser.next()
                                    if (parser.text != null) {
                                        currentCellText.append(parser.text).append(" ")
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tag) {
                            "tc" -> {
                                inCell = false
                                rowCells.add(currentCellText.toString().trim())
                            }
                            "tr" -> {
                                inRow = false
                                if (rowCells.isNotEmpty()) {
                                    allRows.add(rowCells.toList())
                                }
                            }
                            "tbl" -> {
                                inTable = false
                                val parsedTableLessons = parseTableData(allRows, cleanTargetGroup, targetDate)
                                lessons.addAll(parsedTableLessons)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            return parsePlainTextSchedule(xmlContent.replace(Regex("<[^>]+>"), " "), cleanTargetGroup, targetDate)
        }

        return lessons
    }

    /**
     * Парсинг двумерной таблицы из строк ячеек.
     */
    private fun parseTableData(
        rows: List<List<String>>,
        targetGroup: String,
        targetDate: String
    ): List<LessonEntity> {
        val result = mutableListOf<LessonEntity>()
        if (rows.isEmpty()) return result

        var targetColIndex = -1
        var headerRowIndex = -1

        for ((rIdx, row) in rows.withIndex()) {
            for ((cIdx, cell) in row.withIndex()) {
                if (GroupParser.matchesGroup(cell, targetGroup)) {
                    targetColIndex = cIdx
                    headerRowIndex = rIdx
                    break
                }
            }
            if (targetColIndex != -1) break
        }

        var currentDay = 1
        val startRow = if (headerRowIndex != -1) headerRowIndex + 1 else 0

        for (rIdx in startRow until rows.size) {
            val row = rows[rIdx]
            if (row.isEmpty()) continue

            val firstCell = row.getOrNull(0)?.lowercase(Locale.ROOT) ?: ""
            for ((dayName, dayNum) in DAY_KEYWORDS) {
                if (firstCell.contains(dayName)) {
                    currentDay = dayNum
                    break
                }
            }

            val lessonNumber = detectLessonNumber(row) ?: continue

            val lessonCellContent = if (targetColIndex > 0 && targetColIndex < row.size) {
                row[targetColIndex]
            } else if (row.size > 1) {
                row.drop(1).joinToString(" ")
            } else {
                row.firstOrNull() ?: ""
            }

            if (lessonCellContent.isNotBlank() && !GroupParser.matchesGroup(lessonCellContent, targetGroup)) {
                val lesson = createLessonEntity(
                    groupName = targetGroup,
                    dayOfWeek = currentDay,
                    lessonNumber = lessonNumber,
                    rawContent = lessonCellContent,
                    dateString = targetDate
                )
                if (lesson != null) {
                    result.add(lesson)
                }
            }
        }

        return result
    }

    /**
     * Поиск номера урока в строке (документы МГПК нумеруют занятия уроками 1..10).
     */
    private fun detectLessonNumber(row: List<String>): Int? {
        for (cell in row.take(3)) {
            val trimmed = cell.trim()
            val asInt = trimmed.toIntOrNull()
            if (asInt != null && asInt in 1..12) return asInt
            val match = Regex("(?:(10|[1-9])\\s*урок|(10|[1-9])\\s*пара|№?\\s*(10|[1-9]))", RegexOption.IGNORE_CASE).find(trimmed)
            if (match != null) {
                val numStr = match.groupValues[1].ifEmpty {
                    match.groupValues[2].ifEmpty { match.groupValues[3] }
                }
                return numStr.toIntOrNull()
            }
        }
        return null
    }

    /**
     * Разбирает текст расписания (из .doc/.docx/plain text/таблицы псевдографики).
     */
    fun parsePlainTextSchedule(
        text: String,
        targetGroup: String,
        targetDate: String = ""
    ): List<LessonEntity> {
        if (text.isBlank()) return emptyList()

        val cleanTargetGroup = GroupParser.cleanRawGroupName(targetGroup).uppercase(Locale.ROOT)
        val lines = text.lines().map { it.trimEnd() }

        // 1. Извлекаем глобальные метаданные даты и дня недели из заголовка или тела документа
        var detectedDay = 1
        var detectedDate = targetDate

        val searchScopeText = lines.take(100).joinToString(" ").lowercase(Locale.ROOT)
        for ((dayName, dayNum) in DAY_KEYWORDS) {
            if (searchScopeText.contains(dayName)) {
                detectedDay = dayNum
                break
            }
        }

        if (detectedDate.isBlank()) {
            val dateMatch = DATE_IN_TEXT_REGEX.find(searchScopeText)
            if (dateMatch != null) {
                val d = dateMatch.groupValues[1].padStart(2, '0')
                val m = dateMatch.groupValues[2].padStart(2, '0')
                val y = dateMatch.groupValues[3]
                detectedDate = "$d.$m.$y"
            } else {
                val textMonthMatch = Regex("([0-3]?[0-9])\\s+(январ[яе]|феврал[яе]|март[ае]|апрел[яе]|ма[яе]|июн[яе]|июл[яе]|август[ае]|сентябр[яе]|октябр[яе]|ноябр[яе]|декабр[яе])(?:\\s*(202[0-9]))?", RegexOption.IGNORE_CASE).find(searchScopeText)
                if (textMonthMatch != null) {
                    val d = textMonthMatch.groupValues[1].padStart(2, '0')
                    val monthWord = textMonthMatch.groupValues[2].lowercase(Locale.ROOT)
                    val m = when {
                        monthWord.startsWith("янв") -> "01"
                        monthWord.startsWith("фев") -> "02"
                        monthWord.startsWith("мар") -> "03"
                        monthWord.startsWith("апр") -> "04"
                        monthWord.startsWith("ма") -> "05"
                        monthWord.startsWith("июн") -> "06"
                        monthWord.startsWith("июл") -> "07"
                        monthWord.startsWith("авг") -> "08"
                        monthWord.startsWith("сен") -> "09"
                        monthWord.startsWith("окт") -> "10"
                        monthWord.startsWith("ноя") -> "11"
                        monthWord.startsWith("дек") -> "12"
                        else -> "09"
                    }
                    // Год берём текущий, а не зашитый: раньше стояло «2026»,
                    // и с 2027 года даты без года считались бы устаревшими —
                    // чистка архива удаляла бы действующее расписание.
                    val thisYear = java.util.Calendar.getInstance()
                        .get(java.util.Calendar.YEAR).toString()
                    val y = textMonthMatch.groupValues.getOrNull(3)?.ifBlank { thisYear } ?: thisYear
                    detectedDate = "$d.$m.$y"
                }
            }
        }

        // Превью извлечённого текста — ключевая диагностика: видно, что именно видит парсер
        lastParseStats = lastParseStats.copy(
            textPreview = text.replace(Regex("\\s+"), " ").take(120)
        )
        val strategyErrors = mutableListOf<String>()

        // 2. Стратегия A: блочная таблица МГПК с разделителями (│, |, таб) — эталонный формат.
        //    Каждая стратегия изолирована: падение одной не должно убивать весь разбор.
        val tableLessons = runCatching { parseTableGridSchedule(lines, cleanTargetGroup, detectedDay, detectedDate) }
            .onFailure { strategyErrors.add("grid:${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(emptyList())
        if (tableLessons.isNotEmpty()) {
            lastParseStats = lastParseStats.copy(strategy = "grid", groupFound = true, parseError = strategyErrors.joinToString("; "))
            return tableLessons
        }

        // 3. Стратегия B: позиционное восстановление по пронумерованным ячейкам БЕЗ разделителей.
        //    Страховка на случай, если символы рамок потеряны при извлечении текста.
        val cellLessons = runCatching { parseCellRunsSchedule(lines, cleanTargetGroup, detectedDay, detectedDate) }
            .onFailure { strategyErrors.add("cells:${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(emptyList())
        if (cellLessons.isNotEmpty()) {
            lastParseStats = lastParseStats.copy(strategy = "cells", groupFound = true, parseError = strategyErrors.joinToString("; "))
            return cellLessons
        }

        // 4. Стратегия C: построчный разбор произвольного текста
        val lineLessons = runCatching { parseLineByLineFallback(lines, cleanTargetGroup, detectedDay, detectedDate) }
            .onFailure { strategyErrors.add("line:${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(emptyList())
        lastParseStats = lastParseStats.copy(
            strategy = if (lineLessons.isNotEmpty()) "line" else strategyErrors.joinToString("; ").ifBlank { "none" },
            parseError = strategyErrors.joinToString("; ")
        )
        return lineLessons
    }

    /**
     * СТРАТЕГИЯ B. Позиционное восстановление таблицы расписания по ячейкам без разделителей.
     *
     * Документы МГПК устроены так: сначала строка заголовков групп (подряд идущие коды
     * групп: 41Г, 41Н, 42Н, 41О...), затем для каждого урока — строка ячеек предметов
     * (все начинаются с номера урока: «3 ЭкОрган...319/339»), затем строка ячеек
     * преподавателей. Пустая ячейка урока — это ячейка с номером и пробелами («1      »),
     * поэтому выравнивание колонок сохраняется даже без символов рамок.
     *
     * Возвращает пустой список, если целевой группы в документе нет.
     */
    fun parseCellRunsSchedule(
        lines: List<String>,
        targetGroup: String,
        defaultDay: Int,
        dateString: String
    ): List<LessonEntity> {
        val lessons = mutableListOf<LessonEntity>()

        // 1. Найти блоки заголовков групп: >= 2 подряд идущих валидных кода групп
        data class HeaderBlock(val groups: List<String>, val endIdx: Int)

        val blocks = mutableListOf<HeaderBlock>()
        var i = 0
        while (i < lines.size) {
            if (GroupParser.isValid(lines[i].trim())) {
                val groups = mutableListOf<String>()
                var j = i
                while (j < lines.size && GroupParser.isValid(lines[j].trim()) && groups.size < 12) {
                    groups.add(lines[j].trim())
                    j++
                }
                if (groups.size >= 2) {
                    blocks.add(HeaderBlock(groups, j))
                    i = j
                    continue
                }
            }
            i++
        }
        if (blocks.isEmpty()) return emptyList()

        var targetBlockFound = false
        for ((bIdx, block) in blocks.withIndex()) {
            val targetCol = block.groups.indexOfFirst { GroupParser.matchesGroup(it, targetGroup) }
            if (targetCol == -1) continue
            targetBlockFound = true

            // Блок заканчивается началом следующего заголовка групп (или концом документа)
            val end = if (bIdx + 1 < blocks.size) {
                indexOfNextHeaderStart(lines, from = block.endIdx) ?: lines.size
            } else lines.size

            lessons.addAll(
                parseCellRunsBetween(lines, block.endIdx, end, targetCol, block.groups.size, targetGroup, defaultDay, dateString)
            )
        }

        if (!targetBlockFound) {
            lastParseStats = lastParseStats.copy(groupFound = false)
        }
        return lessons
    }

    /** Индекс начала следующего блока заголовков групп (>= 2 подряд валидных групп), начиная с from. */
    private fun indexOfNextHeaderStart(lines: List<String>, from: Int): Int? {
        var i = from
        while (i < lines.size) {
            if (GroupParser.isValid(lines[i].trim())) {
                var j = i
                var count = 0
                while (j < lines.size && GroupParser.isValid(lines[j].trim()) && count < 12) {
                    count++; j++
                }
                if (count >= 2) return i
                i = j
                continue
            }
            i++
        }
        return null
    }

    /** Разбор ячеек одного блока групп (между концом заголовка и следующим заголовком). */
    private fun parseCellRunsBetween(
        lines: List<String>,
        from: Int,
        to: Int,
        targetCol: Int,
        groupCount: Int,
        targetGroup: String,
        dayOfWeek: Int,
        dateString: String
    ): List<LessonEntity> {
        val result = mutableListOf<LessonEntity>()
        var i = from
        while (i < to) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            val lessonNum = leadingLessonNumberOf(line)
            if (lessonNum == null) {
                // не ячейка урока (мусор, продолжения, подписи) — пропускаем
                i++
                continue
            }

            // Собираем строку ячеек предметов: подряд идущие ячейки с ЭТИМ ЖЕ номером урока
            val subjectCells = mutableListOf<String>()
            var k = i
            while (k < to && leadingLessonNumberOf(lines[k]) == lessonNum) {
                subjectCells.add(lines[k])
                k++
            }

            // Собираем строку ячеек преподавателей: не-числовые ячейки.
            // Пустая ЯЧЕЙКА — это строка из ~25 пробелов (фиксированная ширина колонки);
            // почти пустая строка (< 6 символов) — межстрочный разделитель, её пропускаем.
            val teacherCells = mutableListOf<String>()
            var m = k
            while (m < to && teacherCells.size < groupCount) {
                val t = lines[m]
                if (leadingLessonNumberOf(t) != null) break
                if (t.isNotBlank() && GroupParser.isValid(t.trim())) break // начался следующий блок
                if (t.length < 6) {
                    m++
                    continue
                }
                teacherCells.add(t)
                m++
            }

            // Выравнивание колонок возможно только при полном размере строки
            if (subjectCells.size == groupCount) {
                val subj = subjectCells[targetCol].trim()
                val teach = teacherCells.getOrNull(targetCol)?.trim() ?: ""
                val lesson = parseTableCell(subj, teach, targetGroup, dayOfWeek, dateString)
                if (lesson != null && result.none { it.lessonNumber == lesson.lessonNumber }) {
                    result.add(lesson)
                }
            }

            i = if (m > k) m else k
        }
        return result
    }

    /** Номер урока в начале ячейки (« 3 ЭкОрган…» -> 3; «10      » -> 10) либо null. */
    private fun leadingLessonNumberOf(line: String): Int? {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty()) return null
        val match = Regex("^(10|[1-9])(\\s|\$)").find(trimmed) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    /**
     * Парсер официальной сетки расписания МГПК (блоки колонок с разделителями │, |, \t).
     */
    fun parseTableGridSchedule(
        lines: List<String>,
        targetGroup: String,
        defaultDay: Int,
        dateString: String
    ): List<LessonEntity> {
        val lessons = mutableListOf<LessonEntity>()
        var currentDay = defaultDay

        var currentBlockGroups = listOf<String>()
        var targetColIndex = -1

        var i = 0
        while (i < lines.size) {
            val rawLine = lines[i]
            val line = rawLine.trim()

            if (line.isBlank()) {
                i++
                continue
            }

            // Проверяем день недели
            val lowerLine = line.lowercase(Locale.ROOT)
            for ((dayName, dayNum) in DAY_KEYWORDS) {
                if (lowerLine.contains(dayName)) {
                    currentDay = dayNum
                    break
                }
            }

            // Проверяем, является ли строка шапкой групп таблицы
            val delimiter = when {
                line.contains("│") -> "│"
                line.contains("|") -> "|"
                line.contains("\t") -> "\t"
                else -> null
            }

            if (delimiter != null) {
                val cells = line.split(delimiter)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                // Проверяем, содержатся ли здесь названия групп
                val validGroupCount = cells.count { GroupParser.isValid(it) }
                if (validGroupCount >= 2 || (validGroupCount >= 1 && cells.size in 1..8 && cells.any { GroupParser.matchesGroup(it, targetGroup) })) {
                    // Это заголовок блока групп!
                    currentBlockGroups = cells
                    targetColIndex = -1
                    for ((cIdx, grp) in cells.withIndex()) {
                        if (GroupParser.matchesGroup(grp, targetGroup)) {
                            targetColIndex = cIdx
                            break
                        }
                    }
                    i++
                    continue
                }

                // Если в текущем блоке есть наша группа, проверяем строки пар
                if (targetColIndex != -1 && currentBlockGroups.isNotEmpty()) {
                    // Проверяем, не является ли это строкой разделителя рамки
                    val isBorder = line.all { it in "┌┬┐├┼┤┴└┘─|-=+\t " }
                    if (isBorder) {
                        i++
                        continue
                    }

                    // Разбиваем строку на ячейки (сохраняя пустые ячейки)
                    val rowCells = splitRowPreservingColumns(line, delimiter)

                    // Проверяем, содержит ли эта строка номер пары
                    val targetCell1 = rowCells.getOrNull(targetColIndex)?.trim() ?: ""
                    val hasLessonNumber = targetCell1.isNotEmpty() && targetCell1[0].isDigit() && targetCell1[0].digitToInt() in 1..9

                    // Либо любая другая колонка содержит номер пары в начале
                    val anyColHasLesson = rowCells.any { c ->
                        val t = c.trim()
                        t.isNotEmpty() && t[0].isDigit() && t[0].digitToInt() in 1..9
                    }

                    if (hasLessonNumber || anyColHasLesson) {
                        val row1Cells = rowCells
                        // Следующая строка может содержать преподавателей
                        var row2Cells = listOf<String>()
                        if (i + 1 < lines.size) {
                            val nextLine = lines[i + 1].trim()
                            val nextDelimiter = when {
                                nextLine.contains("│") -> "│"
                                nextLine.contains("|") -> "|"
                                nextLine.contains("\t") -> "\t"
                                else -> null
                            }
                            val isNextBorder = nextLine.all { it in "┌┬┐├┼┤┴└┘─|-=+\t " }
                            val isNextGroupHeader = nextDelimiter != null && nextLine.split(nextDelimiter).map { it.trim() }.count { GroupParser.isValid(it) } >= 2
                            val nextFirstColDigit = nextLine.split(nextDelimiter ?: "│").map { it.trim() }.any { it.isNotEmpty() && it[0].isDigit() && it[0].digitToInt() in 1..9 }

                            if (!isNextBorder && !isNextGroupHeader && !nextFirstColDigit && nextDelimiter != null) {
                                row2Cells = splitRowPreservingColumns(nextLine, nextDelimiter)
                                i++ // поглощаем строку преподавателей
                            }
                        }

                        val cell1 = row1Cells.getOrNull(targetColIndex)?.trim() ?: ""
                        val cell2 = row2Cells.getOrNull(targetColIndex)?.trim() ?: ""

                        if (cell1.isNotBlank()) {
                            val parsedLesson = parseTableCell(
                                cellSubject = cell1,
                                cellTeacher = cell2,
                                groupName = targetGroup,
                                dayOfWeek = currentDay,
                                dateString = dateString
                            )
                            if (parsedLesson != null && lessons.none { it.dayOfWeek == parsedLesson.dayOfWeek && it.lessonNumber == parsedLesson.lessonNumber }) {
                                lessons.add(parsedLesson)
                            }
                        }
                    }
                }
            }

            i++
        }

        return lessons
    }

    /**
     * Разбивает строку таблицы с сохранением пустот и выравниванием по колонкам.
     */
    private fun splitRowPreservingColumns(line: String, delimiter: String): List<String> {
        val trimmed = line.trim()
        val withoutOuter = if (trimmed.startsWith(delimiter) && trimmed.endsWith(delimiter) && trimmed.length > 1) {
            trimmed.substring(1, trimmed.length - 1)
        } else if (trimmed.startsWith(delimiter)) {
            trimmed.substring(1)
        } else if (trimmed.endsWith(delimiter)) {
            trimmed.substring(0, trimmed.length - 1)
        } else {
            trimmed
        }

        return withoutOuter.split(delimiter)
    }

    /**
     * Разбор отдельной ячейки пары из таблицы МГПК (поддерживает 1 и 2 подгруппы, слеши, кабинеты, преподавателей).
     */
    fun parseTableCell(
        cellSubject: String,
        cellTeacher: String,
        groupName: String,
        dayOfWeek: Int,
        dateString: String
    ): LessonEntity? {
        val cleanSubj = cellSubject.trim()
        if (cleanSubj.isBlank() || cleanSubj == "-" || cleanSubj == "—") return null

        // Номер урока из начала ячейки (документы МГПК нумеруют уроками 1..10)
        val lessonNumMatch = Regex("^(10|[1-9])\\s*(.*)$").find(cleanSubj) ?: return null
        val lessonNumber = lessonNumMatch.groupValues[1].toInt()
        val rawRest = lessonNumMatch.groupValues[2].trim()

        if (rawRest.isBlank() || rawRest.all { it == '-' || it == '—' || it == ' ' }) {
            return null
        }

        val (timeStart, timeEnd) = CollegeBellSchedule.getTimeForLessonNumber(lessonNumber, dayOfWeek)

        // Проверяем наличие кабинетов в формате "331/228", "154/314", "135/ ", "СТД/ ", "224/125", "245/245", "305/303", "142/142"
        val doubleRoomsPattern = Regex("(?:каб\\.?|ауд\\.?)?\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?|СТД|-{1,7})\\s*/\\s*(?:каб\\.?|ауд\\.?)?\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?|СТД|-{1,7})?\\s*$")
        val doubleRoomsMatch = doubleRoomsPattern.find(rawRest)

        val isExplicitSplit = doubleRoomsMatch != null ||
                rawRest.contains("/") ||
                cellTeacher.contains("/") ||
                cleanSubj.contains("п/г", ignoreCase = true) ||
                cleanSubj.contains("подгруппа", ignoreCase = true)

        if (isExplicitSplit) {
            var room1 = ""
            var room2 = ""
            var subjPart1 = ""
            var subjPart2 = ""

            if (doubleRoomsMatch != null) {
                room1 = doubleRoomsMatch.groupValues[1].trim('-')
                room2 = doubleRoomsMatch.groupValues[2].trim('-')
                val subjectSection = rawRest.substring(0, doubleRoomsMatch.range.first).trim()
                if (subjectSection.contains("/")) {
                    val subjs = subjectSection.split("/")
                    subjPart1 = subjs[0].trim()
                    subjPart2 = subjs.getOrElse(1) { "" }.trim()
                } else {
                    subjPart1 = subjectSection
                    subjPart2 = subjectSection
                }
            } else if (rawRest.contains("/")) {
                val parts = rawRest.split("/")
                subjPart1 = parts[0].trim()
                subjPart2 = parts.getOrElse(1) { "" }.trim()

                val r1Match = Regex("(?:каб\\.?|ауд\\.?)?\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?|СТД)$").find(subjPart1)
                if (r1Match != null) {
                    room1 = r1Match.groupValues[1]
                    subjPart1 = subjPart1.substring(0, r1Match.range.first).trim()
                }
                val r2Match = Regex("(?:каб\\.?|ауд\\.?)?\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?|СТД)$").find(subjPart2)
                if (r2Match != null) {
                    room2 = r2Match.groupValues[1]
                    subjPart2 = subjPart2.substring(0, r2Match.range.first).trim()
                }
            } else {
                val (r, _, s) = extractDetails(rawRest)
                room1 = r
                subjPart1 = s
                subjPart2 = s
            }

            var teacher1 = ""
            var teacher2 = ""
            if (cellTeacher.contains("/")) {
                val teachers = cellTeacher.split("/")
                teacher1 = cleanTeacherName(teachers[0])
                teacher2 = cleanTeacherName(teachers.getOrElse(1) { "" })
            } else {
                teacher1 = cleanTeacherName(cellTeacher)
            }

            val normSubj1 = SubjectFormatter.normalize(subjPart1.ifBlank { rawRest })
            val normSubj2 = if (subjPart2.isNotBlank() && subjPart2 != subjPart1 && !subjPart2.contains("---")) {
                SubjectFormatter.normalize(subjPart2)
            } else normSubj1

            val finalSubjectName = if (normSubj1 != normSubj2 && normSubj2.isNotBlank()) {
                "$normSubj1 / $normSubj2"
            } else {
                normSubj1
            }

            return LessonEntity(
                groupName = GroupParser.cleanRawGroupName(groupName),
                dayOfWeek = dayOfWeek,
                lessonNumber = lessonNumber,
                timeStart = timeStart,
                timeEnd = timeEnd,
                subjectRaw = finalSubjectName,
                roomFirst = room1,
                teacherFirst = teacher1,
                roomSecond = room2,
                teacherSecond = teacher2,
                isSplit = true,
                dateString = dateString
            )
        } else {
            // Одиночный предмет
            var room = ""
            var subject = rawRest
            val singleRoomMatch = Regex("(?:каб\\.?|ауд\\.?)?\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?|СТД)$").find(rawRest)
            if (singleRoomMatch != null) {
                room = singleRoomMatch.groupValues[1]
                subject = rawRest.substring(0, singleRoomMatch.range.first).trim()
            } else {
                val (r, _, s) = extractDetails(rawRest)
                room = r
                subject = s
            }

            val finalTeacher = cleanTeacherName(cellTeacher)
            val normSubject = SubjectFormatter.normalize(subject.ifBlank { rawRest })

            return LessonEntity(
                groupName = GroupParser.cleanRawGroupName(groupName),
                dayOfWeek = dayOfWeek,
                lessonNumber = lessonNumber,
                timeStart = timeStart,
                timeEnd = timeEnd,
                subjectRaw = normSubject,
                roomFirst = room,
                teacherFirst = finalTeacher,
                roomSecond = "",
                teacherSecond = "",
                isSplit = false,
                dateString = dateString
            )
        }
    }

    /**
     * Очищает и форматирует имя преподавателя (удаляет случайные цифры, форматирует инициалы).
     */
    fun cleanTeacherName(raw: String): String {
        var t = raw.replace(Regex("[0-9]"), "").trim()
        t = t.replace(Regex("\\s+"), " ")
        t = t.trim(',', '-', '/', '\\', ' ')
        if (t.isBlank()) return ""

        // Форматирование Фамилия И.О. или Фамилия И.
        val match = Regex("([\\u0410-\\u042F\\u0401][\\u0430-\\u044F\\u0451]+)\\s+([\\u0410-\\u042F\\u0401])\\.?\\s*([\\u0410-\\u042F\\u0401])?\\.?").find(t)
        if (match != null) {
            val surname = match.groupValues[1]
            val init1 = match.groupValues[2]
            val init2 = match.groupValues[3]
            return if (init2.isNotEmpty()) {
                "$surname $init1.$init2."
            } else {
                "$surname $init1."
            }
        }

        return t
    }

    /**
     * Построчный Fallback-разборщик.
     */
    private fun parseLineByLineFallback(
        lines: List<String>,
        targetGroup: String,
        defaultDay: Int,
        dateString: String
    ): List<LessonEntity> {
        val lessons = mutableListOf<LessonEntity>()
        var currentDay = defaultDay
        var isCurrentGroupActive = false

        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            for ((dayName, dayNum) in DAY_KEYWORDS) {
                if (lower.contains(dayName)) {
                    currentDay = dayNum
                    break
                }
            }

            val groupPattern = Regex("(?:группа\\s+)([1-4][1-9]\\s*[\\u0410-\\u042FA-Z])|(?<![0-9A-Za-z_\\u0400-\\u04FF])([1-4][1-9]\\s*[\\u0410-\\u042FA-Z])(?![0-9A-Za-z_\\u0400-\\u04FF])", RegexOption.IGNORE_CASE)
            val groupHeaderMatch = groupPattern.find(line)
            if (groupHeaderMatch != null) {
                val matchedGroup = groupHeaderMatch.value
                if (GroupParser.isValid(matchedGroup.replace("группа", "", ignoreCase = true).trim())) {
                    isCurrentGroupActive = GroupParser.matchesGroup(matchedGroup, targetGroup)
                }
            }

            val isPureHeader = line.matches(Regex("^(?:группа\\s*)?[1-4][1-9]\\s*[\\u0410-\\u042FA-Z](?:\\s+(?:понедельник|вторник|среда|четверг|пятница|суббота))?$", RegexOption.IGNORE_CASE))
            val isLineForGroup = !isPureHeader && (GroupParser.matchesGroup(line, targetGroup) || isCurrentGroupActive)

            if (isLineForGroup) {
                val numMatch = Regex("(?:(10|[1-9])\\s*урок|(10|[1-9])\\s*пара|№?\\s*(10|[1-9]))", RegexOption.IGNORE_CASE).find(line)
                val lessonNum = numMatch?.groupValues?.get(1)?.ifEmpty {
                    numMatch.groupValues.getOrNull(2)?.ifEmpty { numMatch.groupValues.getOrNull(3) }
                }?.toIntOrNull()
                    ?: if (isCurrentGroupActive) (lessons.size + 1).coerceAtMost(10) else 1

                val lesson = createLessonEntity(
                    groupName = targetGroup,
                    dayOfWeek = currentDay,
                    lessonNumber = lessonNum,
                    rawContent = line,
                    dateString = dateString
                )
                if (lesson != null && lessons.none { it.dayOfWeek == currentDay && it.lessonNumber == lessonNum }) {
                    lessons.add(lesson)
                }
            }
        }

        return lessons
    }

    /**
     * Создает сущность LessonEntity из сырого текста ячейки расписания.
     * Обрабатывает подгруппы, кабинеты, преподавателей и нормализует дисциплины через SubjectFormatter.
     */
    fun createLessonEntity(
        groupName: String,
        dayOfWeek: Int,
        lessonNumber: Int,
        rawContent: String,
        dateString: String = ""
    ): LessonEntity? {
        val trimmed = rawContent.trim()
        if (trimmed.isBlank() || trimmed == "-" || trimmed == "—") return null

        val (timeStart, timeEnd) = CollegeBellSchedule.getTimeForLessonNumber(lessonNumber, dayOfWeek)

        // Проверяем наличие 2 кабинетов или 2 подгрупп
        val doubleRooms = extractDoubleRooms(trimmed)
        val isExplicitSplit = trimmed.contains("1 п/г", ignoreCase = true) ||
                trimmed.contains("1п/г", ignoreCase = true) ||
                trimmed.contains("1 пг", ignoreCase = true) ||
                trimmed.contains("1 подгруппа", ignoreCase = true) ||
                doubleRooms != null ||
                (trimmed.contains("/") && trimmed.length > 15)

        if (isExplicitSplit) {
            val parts = splitSubgroupContent(trimmed)
            val firstPart = parts.first
            val secondPart = parts.second

            val (room1, teacher1, subject1) = extractDetails(firstPart)
            val (room2, teacher2, subject2) = extractDetails(secondPart)

            val finalRoom1 = when {
                doubleRooms != null -> doubleRooms.first
                room1.isNotBlank() -> room1
                else -> ""
            }
            val finalRoom2 = when {
                doubleRooms != null -> doubleRooms.second
                room2.isNotBlank() && room2 != room1 -> room2
                else -> ""
            }

            val subjectRaw = subject1.ifBlank { subject2.ifBlank { trimmed } }
            val normalizedSubject = SubjectFormatter.normalize(subjectRaw)

            return LessonEntity(
                groupName = GroupParser.cleanRawGroupName(groupName),
                dayOfWeek = dayOfWeek,
                lessonNumber = lessonNumber,
                timeStart = timeStart,
                timeEnd = timeEnd,
                subjectRaw = normalizedSubject,
                roomFirst = finalRoom1,
                teacherFirst = teacher1,
                roomSecond = finalRoom2,
                teacherSecond = teacher2,
                isSplit = true,
                dateString = dateString
            )
        } else {
            val (room, teacher, subject) = extractDetails(trimmed)
            val normalizedSubject = SubjectFormatter.normalize(subject.ifBlank { trimmed })

            return LessonEntity(
                groupName = GroupParser.cleanRawGroupName(groupName),
                dayOfWeek = dayOfWeek,
                lessonNumber = lessonNumber,
                timeStart = timeStart,
                timeEnd = timeEnd,
                subjectRaw = normalizedSubject,
                roomFirst = room,
                teacherFirst = teacher,
                roomSecond = "",
                teacherSecond = "",
                isSplit = false,
                dateString = dateString
            )
        }
    }

    /**
     * Поиск двух кабинетов (например «каб. 215 / каб. 308», «215 / 308», «каб. 215 - каб. 308», «ауд. 101а / 102»).
     */
    private fun extractDoubleRooms(text: String): Pair<String, String>? {
        val pattern = Regex("(?:каб\\.?|ауд\\.?)\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?)\\s*[/,-]\\s*(?:каб\\.?|ауд\\.?)\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?)", RegexOption.IGNORE_CASE)
        val match = pattern.find(text)
        if (match != null) {
            return Pair(match.groupValues[1], match.groupValues[2])
        }

        val slashDigitsPattern = Regex("([0-9]{2,3}[Ѐ-ӿa-zA-Z]?)\\s*/\\s*([0-9]{2,3}[Ѐ-ӿa-zA-Z]?)")
        val slashMatch = slashDigitsPattern.find(text)
        if (slashMatch != null) {
            return Pair(slashMatch.groupValues[1], slashMatch.groupValues[2])
        }

        return null
    }

    /**
     * Извлечение кабинета, преподавателя и названия предмета из текста.
     */
    private fun extractDetails(text: String): Triple<String, String, String> {
        var room = ""
        var teacher = ""
        var subject = text

        // 1. Поиск кабинета/аудитории
        val roomMatch = Regex("(?:каб\\.?|ауд\\.?)\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?)|([0-9]{2,3}[Ѐ-ӿa-zA-Z]?)\\s*(?:каб|ауд)", RegexOption.IGNORE_CASE).find(text)
        if (roomMatch != null) {
            room = roomMatch.groupValues[1].ifEmpty { roomMatch.groupValues[2] }
            subject = subject.replace(roomMatch.value, " ")
        } else {
            val endDigits = Regex("([1-9][0-9]{1,2}[Ѐ-ӿa-zA-Z]?)$").find(text.trim())
            if (endDigits != null) {
                room = endDigits.groupValues[1]
                subject = subject.substring(0, endDigits.range.first).trim()
            }
        }

        // 2. Поиск преподавателя (Фамилия И.О. или Фамилия И. О.)
        val teacherMatch = Regex("([\\u0410-\\u042F\\u0401][\\u0430-\\u044F\\u0451]+)\\s+([\\u0410-\\u042F\\u0401]\\.\\s*[\\u0410-\\u042F\\u0401]\\.)").find(subject)
        if (teacherMatch != null) {
            teacher = "${teacherMatch.groupValues[1]} ${teacherMatch.groupValues[2].replace(" ", "")}"
            subject = subject.replace(teacherMatch.value, " ")
        }

        // Очистка названия предмета от префиксов номера пары и подгрупп
        subject = subject
            .replace(Regex("(?:^(?:10|[1-9])\\s*урок|^(?:10|[1-9])\\s*пара|^(?:10|[1-9])\\s*\\.|^№\\s*(?:10|[1-9]))", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?:1|2)\\s*п/?г|(?:1|2)\\s*подгруппа", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim(',', '.', '-', ' ', '/', '\\', ':')

        return Triple(room, teacher, subject)
    }

    /**
     * Разделение текста пары на 2 подгруппы.
     */
    private fun splitSubgroupContent(raw: String): Pair<String, String> {
        val p2Match = Regex("(?:/\\s*|\\s+)(?:2\\s*п/?г|2\\s*подгруппа)", RegexOption.IGNORE_CASE).find(raw)
        if (p2Match != null) {
            val part1 = raw.substring(0, p2Match.range.first).trim()
            val part2 = raw.substring(p2Match.range.first).trim()
            if (part1.isNotBlank()) {
                return Pair(part1, part2)
            }
        }

        if (raw.contains("/")) {
            val parts = raw.split("/")
            if (parts.size >= 2 && parts[0].isNotBlank()) {
                return Pair(parts[0].trim(), parts.drop(1).joinToString("/").trim())
            }
        }

        return Pair(raw, raw)
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val base = java.net.URI(baseUrl)
            base.resolve(relativeUrl).toString()
        } catch (_: Exception) {
            if (relativeUrl.startsWith("http")) relativeUrl else "$baseUrl$relativeUrl"
        }
    }
}
