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
 * Парсер расписания занятий ГУО «МГПК» с официального сайта guo-mpk.by.
 *
 * Архитектурные возможности:
 * 1. Сканирование главной страницы https://guo-mpk.by/raspisanie/ для поиска записей дат
 * 2. Извлечение ссылок из <iframe src="view.officeapps.live.com/op/embed.aspx?src=..."> с декодированием URL
 * 3. Отказоустойчивый потоковый экстрактор текста из бинарных файлов .doc (Word 97-2004 OLE2) в UTF-16LE и CP1251
 * 4. Парсинг современных .docx через ZipInputStream и word/document.xml
 * 5. Распознавание 2 подгрупп и 2 кабинетов (каб. 215 / каб. 308) с разметкой 50/50
 * 6. Нормализация дисциплин строго по MpkCurriculum через SubjectFormatter.normalize()
 */
object MpkScheduleParser {

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
        "([0-3]?[0-9])[.-]([0-1]?[0-9])[.-](202[0-9])"
    )

    /**
     * Шаг А: Сканирует HTML главной страницы расписания и находит ссылки на события дат.
     */
    fun findSchedulePostLinks(html: String, baseUrl: String = "https://guo-mpk.by/"): List<SchedulePostLink> {
        val result = mutableListOf<SchedulePostLink>()
        val seenUrls = mutableSetOf<String>()

        POST_LINK_REGEX.findAll(html).forEach { match ->
            val rawUrl = match.groupValues[1].trim()
            val rawTitle = match.groupValues[2].replace(Regex("<[^>]+>"), " ").trim()
            val absoluteUrl = resolveAbsoluteUrl(baseUrl, rawUrl)

            if (!seenUrls.contains(absoluteUrl) && !absoluteUrl.endsWith("/raspisanie/") && !absoluteUrl.endsWith("/raspisanie")) {
                seenUrls.add(absoluteUrl)

                // Извлекаем дату из URL или названия записи
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

        return result
    }

    /**
     * Шаг Б: Загружает HTML страницы события и извлекает ссылки на документы (.doc / .docx).
     * Поддерживает декодирование iframe с view.officeapps.live.com и прямые теги <a>.
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

        // 2. Прямые ссылки на .doc / .docx файлы
        DOC_LINK_REGEX.findAll(html).forEach { match ->
            val link = match.groupValues[1]
            urls.add(resolveAbsoluteUrl(baseUrl, link))
        }

        return urls.toList()
    }

    /**
     * Универсальный метод парсинга файла: автоматически определяет формат (.docx vs .doc) и разбирает данные.
     */
    fun parseFile(
        bytes: ByteArray,
        targetGroup: String,
        targetDate: String = ""
    ): List<LessonEntity> {
        if (bytes.isEmpty()) return emptyList()

        // 1. Проверяем сигнатуру ZIP (DOCX: 50 4B 03 04)
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
            val docxLessons = parseDocx(ByteArrayInputStream(bytes), targetGroup, targetDate)
            if (docxLessons.isNotEmpty()) return docxLessons
        }

        // 2. Бинарный Word 97-2004 (.doc OLE2) потоковый экстрактор
        val docLessons = parseDocBinary(bytes, targetGroup, targetDate)
        if (docLessons.isNotEmpty()) return docLessons

        // 3. Fallback: разбор как плоского текста в UTF-8 / CP1251
        val textUtf8 = String(bytes, Charsets.UTF_8)
        val textLessons = parsePlainTextSchedule(textUtf8, targetGroup, targetDate)
        if (textLessons.isNotEmpty()) return textLessons

        val textCp1251 = String(bytes, CP1251)
        return parsePlainTextSchedule(textCp1251, targetGroup, targetDate)
    }

    /**
     * Шаг В: Отказоустойчивый потоковый экстрактор текста из бинарных файлов .doc (Word 97-2004 OLE2).
     * Считывает байты и извлекает последовательности в кодировках UTF-16LE и Windows-1251.
     */
    fun parseDocBinary(
        bytes: ByteArray,
        targetGroup: String,
        targetDate: String = ""
    ): List<LessonEntity> {
        val extractedTextBuilder = StringBuilder()

        // 1. Извлечение строк в кодировке Windows-1251 (CP1251)
        val cp1251Runs = extractCp1251Runs(bytes)
        for (run in cp1251Runs) {
            extractedTextBuilder.append(run).append("\n")
        }

        // 2. Извлечение строк в кодировке UTF-16LE
        val utf16Runs = extractUtf16LeRuns(bytes)
        for (run in utf16Runs) {
            extractedTextBuilder.append(run).append("\n")
        }

        val allExtractedText = extractedTextBuilder.toString()
        if (allExtractedText.isBlank()) return emptyList()

        return parsePlainTextSchedule(allExtractedText, targetGroup, targetDate)
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
     */
    private fun extractUtf16LeRuns(bytes: ByteArray): List<String> {
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

            if (isAscii || isCyrillic) {
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
                    if (decoded.any { it in 'А'..'я' || it in 'A'..'z' || it in '0'..'9' }) {
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
     * Поиск номера пары в строке.
     */
    private fun detectLessonNumber(row: List<String>): Int? {
        for (cell in row.take(3)) {
            val trimmed = cell.trim()
            if (trimmed.length == 1 && trimmed[0].isDigit()) {
                val num = trimmed[0].digitToInt()
                if (num in 1..6) return num
            }
            val match = Regex("(?:([1-6])\\s*пара|№?\\s*([1-6]))", RegexOption.IGNORE_CASE).find(trimmed)
            if (match != null) {
                val numStr = match.groupValues[1].ifEmpty { match.groupValues[2] }
                return numStr.toIntOrNull()
            }
        }
        return null
    }

    /**
     * Разбирает текст расписания (из .doc/.docx/plain text) построчно.
     */
    fun parsePlainTextSchedule(
        text: String,
        targetGroup: String,
        targetDate: String = ""
    ): List<LessonEntity> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val lessons = mutableListOf<LessonEntity>()

        var currentDay = 1
        var isCurrentGroupActive = false

        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            for ((dayName, dayNum) in DAY_KEYWORDS) {
                if (lower.contains(dayName)) {
                    currentDay = dayNum
                    break
                }
            }

            // Проверяем смену группы в заголовке блока (строго 2 цифры [1-4][1-9] или префикс "группа")
            val groupPattern = Regex("(?:группа\\s+)([1-4][1-9]\\s*[А-ЯA-Z])|\\b([1-4][1-9]\\s*[А-ЯA-Z])\\b", RegexOption.IGNORE_CASE)
            val groupHeaderMatch = groupPattern.find(line)
            if (groupHeaderMatch != null) {
                val matchedGroup = groupHeaderMatch.value
                if (GroupParser.isValid(matchedGroup.replace("группа", "", ignoreCase = true).trim())) {
                    isCurrentGroupActive = GroupParser.matchesGroup(matchedGroup, targetGroup)
                }
            }

            val isPureHeader = line.matches(Regex("^(?:группа\\s*)?[1-4][1-9]\\s*[А-ЯA-Z](?:\\s+(?:понедельник|вторник|среда|четверг|пятница|суббота))?$", RegexOption.IGNORE_CASE))

            val isLineForGroup = !isPureHeader && (GroupParser.matchesGroup(line, targetGroup) || isCurrentGroupActive)

            if (isLineForGroup) {
                val numMatch = Regex("(?:([1-6])\\s*пара|№?\\s*([1-6]))", RegexOption.IGNORE_CASE).find(line)
                val lessonNum = numMatch?.groupValues?.get(1)?.ifEmpty { numMatch.groupValues.getOrNull(2) }?.toIntOrNull()
                    ?: if (isCurrentGroupActive) (lessons.size + 1).coerceAtMost(6) else 1

                val lesson = createLessonEntity(
                    groupName = targetGroup,
                    dayOfWeek = currentDay,
                    lessonNumber = lessonNum,
                    rawContent = line,
                    dateString = targetDate
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

        val (timeStart, timeEnd) = CollegeBellSchedule.getTimeForNumber(lessonNumber, dayOfWeek)

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
        val pattern = Regex("(?:каб\\.?|ауд\\.?)\\s*([0-9]{1,3}[а-яА-Яa-zA-Z]?)\\s*[/,-]\\s*(?:каб\\.?|ауд\\.?)\\s*([0-9]{1,3}[а-яА-Яa-zA-Z]?)", RegexOption.IGNORE_CASE)
        val match = pattern.find(text)
        if (match != null) {
            return Pair(match.groupValues[1], match.groupValues[2])
        }

        val slashDigitsPattern = Regex("([0-9]{2,3}[а-яА-Яa-zA-Z]?)\\s*/\\s*([0-9]{2,3}[а-яА-Яa-zA-Z]?)")
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
        val roomMatch = Regex("(?:каб\\.?|ауд\\.?)\\s*([0-9]{1,3}[а-яА-Яa-zA-Z]?)|([0-9]{2,3}[а-яА-Яa-zA-Z]?)\\s*(?:каб|ауд)", RegexOption.IGNORE_CASE).find(text)
        if (roomMatch != null) {
            room = roomMatch.groupValues[1].ifEmpty { roomMatch.groupValues[2] }
            subject = subject.replace(roomMatch.value, " ")
        } else {
            val endDigits = Regex("([1-9][0-9]{1,2}[а-яА-Яa-zA-Z]?)$").find(text.trim())
            if (endDigits != null) {
                room = endDigits.groupValues[1]
                subject = subject.substring(0, endDigits.range.first).trim()
            }
        }

        // 2. Поиск преподавателя (Фамилия И.О. или Фамилия И. О.)
        val teacherMatch = Regex("([А-ЯЁ][а-яё]+)\\s+([А-ЯЁ]\\.\\s*[А-ЯЁ]\\.)").find(subject)
        if (teacherMatch != null) {
            teacher = "${teacherMatch.groupValues[1]} ${teacherMatch.groupValues[2].replace(" ", "")}"
            subject = subject.replace(teacherMatch.value, " ")
        }

        // Очистка названия предмета от префиксов номера пары и подгрупп
        subject = subject
            .replace(Regex("(?:^[1-6]\\s*пара|^[1-6]\\s*\\.|^№\\s*[1-6])", RegexOption.IGNORE_CASE), "")
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
