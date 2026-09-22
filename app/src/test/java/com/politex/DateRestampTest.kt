package com.politex

import com.politex.data.network.MpkScheduleParser
import com.politex.data.network.TeacherScheduleParser
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1: парсер штампует строки реальной датой дня недели (заголовок документа
 * приоритетнее запрошенной даты), сравнение дат — через Calendar.
 */
class DateRestampTest {

    private val CP1251 = Charset.forName("windows-1251")

    // --- заголовок документа с датой + днём недели ---

    @Test
    fun `парсер берёт дату из заголовка документа, а не из запрошенной`() {
        // Документ: «РАСПИСАНИЕ на 15.09.2026 г. ВТОРНИК», запрошена дата «17.09.2026» (четверг)
        val doc = ("РАСПИСАНИЕ на 15.09.2026 г.  ВТОРНИК\r\n" +
            "Понедельник\r\n1 пара: 41О Охрана труда каб. 204 Ковалев В.П.\r\n" +
            "Вторник\r\n2 пара: 41О ТОЭ ауд. 301 Смирнов Д.А.\r\n").toByteArray(CP1251)

        val lessons = MpkScheduleParser.parseFile(doc, targetGroup = "41О", targetDate = "17.09.2026")
        assertEquals(2, lessons.size)

        val monday = lessons.first { it.dayOfWeek == 1 }
        val tuesday = lessons.first { it.dayOfWeek == 2 }
        // Заголовок называет ВТОРНИК 15.09 → пн = 14.09, вт = 15.09 (не 17.09!)
        assertEquals("14.09.2026", monday.dateString)
        assertEquals("15.09.2026", tuesday.dateString)
    }

    @Test
    fun `запрошенная дата без заголовка - пн недели запрошенной даты`() {
        // Нет даты в заголовке; запрошена пятница 18.09.2026 → пн = 14.09, пт = 18.09
        val doc = ("Понедельник\r\n1 пара: 41О Охрана труда каб. 204 Ковалев В.П.\r\n" +
            "Пятница\r\n1 пара: 41О Математика каб. 105 Сидорова Е.Н.\r\n").toByteArray(CP1251)

        val lessons = MpkScheduleParser.parseFile(doc, targetGroup = "41О", targetDate = "18.09.2026")
        val monday = lessons.first { it.dayOfWeek == 1 }
        val friday = lessons.first { it.dayOfWeek == 5 }
        assertEquals("14.09.2026", monday.dateString)
        assertEquals("18.09.2026", friday.dateString)
    }

    @Test
    fun `граница месяца при штамповке от заголовка`() {
        // Заголовок: вторник 29.09.2026 → пн = 28.09, сб = 03.10 (другой месяц)
        val doc = ("РАСПИСАНИЕ на 29.09.2026 г.  ВТОРНИК\r\n" +
            "Понедельник\r\n1 пара: 41О Охрана труда каб. 204 Ковалев В.П.\r\n" +
            "Суббота\r\n1 пара: 41О Математика каб. 105 Сидорова Е.Н.\r\n").toByteArray(CP1251)

        val lessons = MpkScheduleParser.parseFile(doc, targetGroup = "41О", targetDate = "29.09.2026")
        assertEquals("28.09.2026", lessons.first { it.dayOfWeek == 1 }.dateString)
        assertEquals("03.10.2026", lessons.first { it.dayOfWeek == 6 }.dateString)
    }

    @Test
    fun `граница года при штамтовке от заголовка`() {
        // Заголовок: среда 30.12.2026 → пн = 28.12, пт = 01.01.2027 (другой год)
        val doc = ("РАСПИСАНИЕ на 30.12.2026 г.  СРЕДА\r\n" +
            "Понедельник\r\n1 пара: 41О Охрана труда каб. 204 Ковалев В.П.\r\n" +
            "Пятница\r\n1 пара: 41О Математика каб. 105 Сидорова Е.Н.\r\n").toByteArray(CP1251)

        val lessons = MpkScheduleParser.parseFile(doc, targetGroup = "41О", targetDate = "30.12.2026")
        assertEquals("28.12.2026", lessons.first { it.dayOfWeek == 1 }.dateString)
        assertEquals("01.01.2027", lessons.first { it.dayOfWeek == 5 }.dateString)
    }

    @Test
    fun `реальный документ сайта штампуется датой заголовка`() {
        val bytes = javaClass.getResourceAsStream("/raspisanie-15.09.2026.doc")!!.readBytes()

        // Запрошена «не та» дата — документ всё равно штампуется своей датой 15.09.2026
        val lessons = MpkScheduleParser.parseFile(bytes, targetGroup = "41О", targetDate = "16.09.2026")
        assertTrue(lessons.isNotEmpty())
        assertTrue(
            "Все строки должны иметь дату 15.09.2026: ${lessons.map { it.dateString }.distinct()}",
            lessons.all { it.dateString == "15.09.2026" }
        )
        assertEquals(2, lessons.first().dayOfWeek)
    }

    // --- parseDotDate / dowOfCalendar ---

    @Test
    fun `parseDotDate понимает оба формата`() {
        val parser = MpkScheduleParser
        assertEquals(
            parser.parseDotDate("15.09.2026")!!.timeInMillis,
            parser.parseDotDate("2026-09-15")!!.timeInMillis
        )
        assertEquals(
            parser.parseDotDate("05.10.2026")!!.timeInMillis,
            parser.parseDotDate("2026-10-5")!!.timeInMillis
        )
    }

    @Test
    fun `dowOfCalendar воскресенье = 7`() {
        val parser = MpkScheduleParser
        val sunday = java.util.Calendar.getInstance().apply { clear(); set(2026, 8, 20) }
        assertEquals(7, parser.dowOfCalendar(sunday))
        val monday = java.util.Calendar.getInstance().apply { clear(); set(2026, 8, 14) }
        assertEquals(1, parser.dowOfCalendar(monday))
    }

    // --- преподаватели: размерность дня недели ---

    @Test
    fun `teacher doc размечает слоты днём недели заголовка`() {
        val bytes = javaClass.getResourceAsStream("/16.09.2026-raspisanie-prepodavatelej.doc")!!.readBytes()
        val parsed = TeacherScheduleParser.parse(bytes)

        val bazulina = parsed.entries.firstOrNull { it.key.startsWith("Базулина") }
        assertNotNull(bazulina)
        // Заголовок документа: СРЕДА 16.09.2026
        assertTrue(bazulina!!.value.all { it.dayOfWeek == 3 })
    }

    @Test
    fun `dowFromHeaderText распознаёт день заголовка`() {
        assertEquals(3, TeacherScheduleParser.dowFromHeaderText("│  1 НЕДЕЛЯ    ║ СРЕДА                  16.09.2026               ║"))
        assertEquals(1, TeacherScheduleParser.dowFromHeaderText("ПОНЕДЕЛЬНИК 15.09.2026"))
        assertEquals(null, TeacherScheduleParser.dowFromHeaderText("какой-то текст без дня"))
    }
}
