package com.politex.data.network

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipInputStream

data class TeacherSlot(
    val lessonNumber: Int,
    val groups: List<String>,
    val subject: String,
    val room: String,

    val dayOfWeek: Int = 0,
)

object TeacherScheduleParser {
    private val CP1251 = Charset.forName("windows-1251")

    private val GROUP_REGEX = Regex("^([1-4])([1-9])([\\u0410-\\u042F])$")

    private val DATE_DOT_REGEX = Regex("\\d{2}\\.\\d{2}\\.\\d{4}")

    private const val HEADER_SCAN_LINES = 6

    private val ROOM_REGEX = Regex("^([0-9]{1,3}[\\u0430-\\u044Fa-zA-Z]?|СТД)$")

    private val HEADER_DOW_REGEX =
        Regex(
            "ПОНЕДЕЛЬНИК|ВТОРНИК|СРЕД[АУЕ]|ЧЕТВЕРГ|ПЯТНИЦ[АУЕ]|СУББОТ",
            RegexOption.IGNORE_CASE,
        )

    fun dowFromHeaderText(text: String): Int? {
        val word = HEADER_DOW_REGEX.find(text)?.value?.uppercase(Locale.ROOT) ?: return null
        return when {
            word.startsWith("ПОНЕД") -> 1
            word.startsWith("ВТОР") -> 2
            word.startsWith("СРЕД") -> 3
            word.startsWith("ЧЕТВ") -> 4
            word.startsWith("ПЯТН") -> 5
            word.startsWith("СУББ") -> 6
            else -> null
        }
    }

    fun findHeaderLine(text: String): String? {
        val lines = text.lines()
        lines.firstOrNull { it.contains("НЕДЕЛЯ", ignoreCase = true) }?.let { return it }
        val head = lines.take(HEADER_SCAN_LINES)
        head.firstOrNull { HEADER_DOW_REGEX.containsMatchIn(it) }?.let { return it }
        return head.firstOrNull { DATE_DOT_REGEX.containsMatchIn(it) }
    }

    fun dateFromHeaderText(text: String): String? = DATE_DOT_REGEX.find(text)?.value

    fun dotDate(cal: java.util.Calendar): String = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(cal.time)

    fun matchesRequestedDate(
        text: String,
        requested: java.util.Calendar,
    ): Boolean {
        val header = findHeaderLine(text) ?: return false
        val inDocDate = dateFromHeaderText(header)

        if (inDocDate != null) {
            val inDocCal = MpkScheduleParser.parseDotDate(inDocDate) ?: return false
            return inDocCal.get(java.util.Calendar.YEAR) == requested.get(java.util.Calendar.YEAR) &&
                inDocCal.get(java.util.Calendar.DAY_OF_YEAR) == requested.get(java.util.Calendar.DAY_OF_YEAR)
        }

        val dow = dowFromHeaderText(header) ?: return false
        return dow == MpkScheduleParser.dowOfCalendar(requested)
    }

    fun parse(bytes: ByteArray): Map<String, List<TeacherSlot>> {
        if (bytes.isEmpty()) return emptyMap()

        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            parseDocx(bytes)?.let { if (it.isNotEmpty()) return it }
        }

        val utf16 = extractUtf16(bytes)
        if (utf16.isNotBlank()) {
            val parsed = parsePlainText(utf16)
            if (parsed.isNotEmpty()) return parsed
        }
        val cp1251 = extractCp1251(bytes)
        return parsePlainText(cp1251)
    }

    fun parsePlainText(text: String): Map<String, List<TeacherSlot>> {
        if (text.isBlank()) return emptyMap()

        data class Cell(
            val groups: MutableList<String> = mutableListOf(),
            val rooms: MutableList<String> = mutableListOf(),
            var subject: String = "",
        )

        val headerLine = findHeaderLine(text)
        val headerDow =
            headerLine?.let { line ->
                dowFromHeaderText(line)
                    ?: dateFromHeaderText(line)
                        ?.let { dot -> MpkScheduleParser.parseDotDate(dot) }
                        ?.let { cal -> MpkScheduleParser.dowOfCalendar(cal) }
            } ?: 0

        val result = mutableMapOf<String, MutableMap<Int, Cell>>()
        var currentTeacher: String? = null

        var headerSeen = headerLine == null

        text.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (!line.contains('║')) return@forEach

            if (line.contains('╫') || line.contains('╥')) return@forEach

            val parts = line.split('║')
            if (parts.size < 2) return@forEach

            if (!headerSeen) {
                if (line.contains("НЕДЕЛЯ", ignoreCase = true) ||
                    HEADER_DOW_REGEX.containsMatchIn(line) ||
                    DATE_DOT_REGEX.containsMatchIn(line)
                ) {
                    headerSeen = true
                }
                return@forEach
            }

            val nameCell = parts[0].replace("│", "").trim()
            if (nameCell.isNotEmpty() && !nameCell.startsWith("1 НЕДЕЛЯ")) {
                currentTeacher = nameCell
            }
            val teacher = currentTeacher ?: return@forEach

            val cells = parts[1].split('│').map { it.trim() }

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

                    value.uppercase(Locale.ROOT).contains("КУРС") -> cell.subject = value
                    else -> cell.subject = value
                }
            }
        }

        return result
            .mapValues { (_, byLesson) ->
                byLesson.entries.sortedBy { it.key }.map { (lessonNumber, cell) ->
                    TeacherSlot(
                        lessonNumber = lessonNumber,
                        groups = cell.groups.distinct(),
                        subject = cell.subject,
                        room = cell.rooms.firstOrNull().orEmpty(),
                        dayOfWeek = headerDow,
                    )
                }
            }.filterValues { it.isNotEmpty() }
    }

    fun extractText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            parseDocxText(bytes)?.let { if (it.isNotBlank()) return it }
        }
        val utf16 = extractUtf16(bytes)
        if (utf16.isNotBlank()) return utf16
        return extractCp1251(bytes)
    }

    private fun parseDocxText(bytes: ByteArray): String? = try {
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
        xml?.let { xmlToPseudoTable(it) }
    } catch (_: Exception) {
        null
    }

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
                buffer.add(bytes[i])
                buffer.add(bytes[i + 1])
                i += 2
            } else {
                if (buffer.size >= 6) {
                    runs.add(String(buffer.toByteArray(), Charsets.UTF_16LE))
                }
                buffer.clear()
                i += 2
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
            val isValid =
                (unsigned in 0x20..0x7E) ||
                    (unsigned >= 0xC0) ||
                    unsigned == 0xA8 ||
                    unsigned == 0xB8 ||
                    unsigned == 0x09 ||
                    unsigned == 0x0A ||
                    unsigned == 0x0D
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

    private fun parseDocx(bytes: ByteArray): Map<String, List<TeacherSlot>>? = parseDocxText(bytes)?.let { parsePlainText(it) }

    private fun xmlToPseudoTable(xml: String): String {
        val sb = StringBuilder()
        var cellIndex = 0
        Regex("<w:tr[ >]").findAll(xml).forEach { rowMatch ->
            val rowEnd = xml.indexOf("</w:tr>", rowMatch.range.first)
            if (rowEnd < 0) return@forEach
            val rowXml = xml.substring(rowMatch.range.first, rowEnd)
            sb.append('│')
            Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(rowXml).forEach { textMatch ->
                if (cellIndex == 0) {
                    sb.append(textMatch.groupValues[1]).append('║')
                } else {
                    sb.append(textMatch.groupValues[1]).append('│')
                }
                cellIndex++
            }
            sb.append('\n')
            cellIndex = 0
        }
        return sb.toString()
    }
}
