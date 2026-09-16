package com.example.data.network

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Одно занятие преподавателя из расписания преподавателей.
 *
 * @param lessonNumber номер урока (1..10)
 * @param groups группы, у которых преподаватель ведёт этот урок
 * @param subject сокращённое название дисциплины из документа
 * @param room кабинет (может быть пустым)
 */
data class TeacherSlot(
    val lessonNumber: Int,
    val groups: List<String>,
    val subject: String,
    val room: String
)

/**
 * Расписание преподавателей с сайта guo-mpk.by.
 *
 * Документ `NN.MM.YYYY-raspisanie-prepodavatelej.doc` устроен как
 * транспонированная таблица: строки — преподаватели, колонки — номера уроков.
 * На каждого преподавателя идёт несколько строк:
 *
 * ```
 * │Базулина Т.Г. ║    │32Э │41П │41П │31П │31П │
 * │              ║    │ЭлСн│НалД│НалД│ЭлМа│ЭлМа│   ← дисциплины
 * │              ║    │301 │301 │301 │123 │123 │   ← кабинеты
 * ```
 *
 * Значения классифицируются по виду: код группы (`41П`) → группа,
 * число (`301`) → кабинет, остальное → дисциплина.
 */
object TeacherScheduleParser {

    private val CP1251 = Charset.forName("windows-1251")

    /** Код группы вида «41П», «11Э», «33Э». */
    private val GROUP_REGEX = Regex("^([1-4])([1-9])([\\u0410-\\u042F])$")

    /** Кабинет: 1–3 цифры, возможно с буквой (305а) или «СТД». */
    private val ROOM_REGEX = Regex("^([0-9]{1,3}[\\u0430-\\u044Fa-zA-Z]?|СТД)$")

    /**
     * Разбирает документ расписания преподавателей.
     * @return ФИО преподавателя → список занятий (отсортирован по номеру урока)
     */
    fun parse(bytes: ByteArray): Map<String, List<TeacherSlot>> {
        if (bytes.isEmpty()) return emptyMap()

        // 1. DOCX (ZIP) — читаем word/document.xml
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            parseDocx(bytes)?.let { if (it.isNotEmpty()) return it }
        }

        // 2. Бинарный .doc: извлекаем текст в UTF-16LE (основной) или CP1251
        val utf16 = extractUtf16(bytes)
        if (utf16.isNotBlank()) {
            val parsed = parsePlainText(utf16)
            if (parsed.isNotEmpty()) return parsed
        }
        val cp1251 = extractCp1251(bytes)
        return parsePlainText(cp1251)
    }

    /**
     * Разбирает уже извлечённый текст таблицы преподавателей.
     */
    fun parsePlainText(text: String): Map<String, List<TeacherSlot>> {
        if (text.isBlank()) return emptyMap()

        // Накопители: ФИО → (номер урока → набор значений по видам)
        data class Cell(
            val groups: MutableList<String> = mutableListOf(),
            val rooms: MutableList<String> = mutableListOf(),
            var subject: String = ""
        )

        val result = mutableMapOf<String, MutableMap<Int, Cell>>()
        var currentTeacher: String? = null

        text.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (!line.contains('║')) return@forEach
            // Пропускаем шапку и разделители
            if (line.contains('╫') || line.contains('╥')) return@forEach

            val parts = line.split('║')
            if (parts.size < 2) return@forEach

            val nameCell = parts[0].replace("│", "").trim()
            if (nameCell.isNotEmpty() && !nameCell.startsWith("1 НЕДЕЛЯ")) {
                currentTeacher = nameCell
            }
            val teacher = currentTeacher ?: return@forEach

            // Правая часть: ячейки по номерам уроков, разделитель │
            val cells = parts[1].split('│').map { it.trim() }
            // Первая ячейка часто пустая (после ║), последняя — мусор после ║
            val lessonCells = cells.drop(1)

            val teacherCells = result.getOrPut(teacher) { mutableMapOf() }

            lessonCells.forEachIndexed { index, value ->
                val lessonNumber = index + 1
                if (lessonNumber > 10) return@forEachIndexed
                if (value.isBlank() || value == "—") return@forEachIndexed

                val cell = teacherCells.getOrPut(lessonNumber) { Cell() }
                when {
                    GROUP_REGEX.matches(value) -> cell.groups.add(value)
                    ROOM_REGEX.matches(value) -> cell.rooms.add(value)
                    // «1 КУРС» в ячейке означает занятие у всего курса
                    value.uppercase(Locale.ROOT).contains("КУРС") -> cell.subject = value
                    else -> cell.subject = value
                }
            }
        }

        // Преобразуем накопители в плоский список занятий
        return result.mapValues { (_, byLesson) ->
            byLesson.entries.sortedBy { it.key }.map { (lessonNumber, cell) ->
                TeacherSlot(
                    lessonNumber = lessonNumber,
                    groups = cell.groups.distinct(),
                    subject = cell.subject,
                    // Кабинетов может быть меньше, чем групп: берём первый как основной
                    room = cell.rooms.firstOrNull().orEmpty()
                )
            }
        }.filterValues { it.isNotEmpty() }
    }

    // --------------------------- Извлечение текста ---------------------------

    private fun extractUtf16(bytes: ByteArray): String {
        val runs = mutableListOf<String>()
        val buffer = mutableListOf<Byte>()
        var i = 0
        while (i < bytes.size - 1) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val isAscii = b1 == 0 && ((b0 in 0x20..0x7E) || b0 == 0x09 || b0 == 0x0A || b0 == 0x0D)
            val isCyrillic = b1 == 0x04 && b0 <= 0x5F
            val isBoxDrawing = b1 == 0x25
            if (isAscii || isCyrillic || isBoxDrawing) {
                buffer.add(bytes[i]); buffer.add(bytes[i + 1]); i += 2
            } else {
                if (buffer.size >= 6) {
                    runs.add(String(buffer.toByteArray(), Charsets.UTF_16LE))
                }
                buffer.clear(); i += 2
            }
        }
        if (buffer.size >= 6) runs.add(String(buffer.toByteArray(), Charsets.UTF_16LE))
        return runs.joinToString("\n")
    }

    private fun extractCp1251(bytes: ByteArray): String {
        val runs = mutableListOf<String>()
        val buffer = mutableListOf<Byte>()
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            val isValid = (unsigned in 0x20..0x7E) || (unsigned >= 0xC0) ||
                unsigned == 0xA8 || unsigned == 0xB8 ||
                unsigned == 0x09 || unsigned == 0x0A || unsigned == 0x0D
            if (isValid) {
                buffer.add(b)
            } else {
                if (buffer.size >= 6) {
                    val decoded = String(buffer.toByteArray(), CP1251)
                    if (decoded.contains('║') || decoded.count { it in 'А'..'я' } > 3) runs.add(decoded)
                }
                buffer.clear()
            }
        }
        if (buffer.size >= 6) runs.add(String(buffer.toByteArray(), CP1251))
        return runs.joinToString("\n")
    }

    private fun parseDocx(bytes: ByteArray): Map<String, List<TeacherSlot>>? = try {
        val zip = ZipInputStream(ByteArrayInputStream(bytes))
        var entry = zip.nextEntry
        var xml: String? = null
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                xml = zip.bufferedReader(Charsets.UTF_8).use { it.readText() }
                break
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        // Для .docx строим псевдографику из ячеек таблицы: вставляем «║»/«│»
        xml?.let { parsePlainText(xmlToPseudoTable(it)) }
    } catch (_: Exception) {
        null
    }

    /** Грубое превращение XML таблицы .docx в ту же псевдографику, что у .doc. */
    private fun xmlToPseudoTable(xml: String): String {
        val sb = StringBuilder()
        var cellIndex = 0
        Regex("<w:tr[ >]").findAll(xml).forEach { rowMatch ->
            val rowEnd = xml.indexOf("</w:tr>", rowMatch.range.first)
            if (rowEnd < 0) return@forEach
            val rowXml = xml.substring(rowMatch.range.first, rowEnd)
            sb.append('│')
            Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(rowXml).forEach { textMatch ->
                if (cellIndex == 0) sb.append(textMatch.groupValues[1]).append('║')
                else sb.append(textMatch.groupValues[1]).append('│')
                cellIndex++
            }
            sb.append('\n')
            cellIndex = 0
        }
        return sb.toString()
    }
}
