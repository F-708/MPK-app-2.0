package com.example

import com.example.data.model.BellScheduleType
import com.example.data.model.CollegeBellSchedule
import com.example.data.network.MpkNetworkClient
import com.example.data.network.MpkScheduleParser
import java.io.ByteArrayOutputStream
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты для проверки требований 2026 года:
 * 1. Официальное расписание звонков (Стандартное, Четверг с Инфочасом, Суббота).
 * 2. Парсер бинарных файлов .doc (OLE2 / CP1251 / UTF-16LE / 0x07 ячейки).
 * 3. Сетевой клиент с браузерными заголовками и предсказуемой адресацией.
 */
class Step5OfficialDocTest {

    @Test
    fun testStandardBellSchedule() {
        val standard = CollegeBellSchedule.STANDARD_BELLS
        assertEquals(12, standard.size)

        // 1 урок
        assertEquals("08:15", standard[0].start)
        assertEquals("09:00", standard[0].end)
        assertEquals(10, standard[0].breakAfterMinutes)

        // 2 урок (большая перемена 25 мин)
        assertEquals("09:10", standard[1].start)
        assertEquals("09:55", standard[1].end)
        assertEquals(25, standard[1].breakAfterMinutes)
        assertTrue(standard[1].isBigBreak)

        // 3 урок
        assertEquals("10:20", standard[2].start)
        assertEquals("11:05", standard[2].end)

        // 4 урок (большая перемена 25 мин)
        assertEquals("11:15", standard[3].start)
        assertEquals("12:00", standard[3].end)
        assertEquals(25, standard[3].breakAfterMinutes)
        assertTrue(standard[3].isBigBreak)

        // 12 урок
        val last = standard.last()
        assertEquals(12, last.lessonNumber)
        assertEquals("18:50", last.start)
        assertEquals("19:35", last.end)
    }

    @Test
    fun testThursdayBellScheduleWithInfoHour() {
        val thursday = CollegeBellSchedule.THURSDAY_BELLS
        assertEquals(13, thursday.size) // 12 уроков + 1 инфочас

        // Поиск инфочаса
        val infoHour = thursday.find { it.isInfoHour }
        assertNotNull(infoHour)
        assertEquals("14:15", infoHour?.start)
        assertEquals("14:35", infoHour?.end)
        assertEquals("Информационный час", infoHour?.title)

        // 7 урок после инфочаса начинается в 14:45
        val lesson7 = thursday.find { it.lessonNumber == 7 }
        assertNotNull(lesson7)
        assertEquals("14:45", lesson7?.start)
        assertEquals("15:30", lesson7?.end)

        // 12 урок
        val lesson12 = thursday.find { it.lessonNumber == 12 }
        assertEquals("19:20", lesson12?.start)
        assertEquals("20:05", lesson12?.end)
    }

    @Test
    fun testSaturdayBellSchedule() {
        val saturday = CollegeBellSchedule.SATURDAY_BELLS
        assertEquals(8, saturday.size)

        assertEquals("08:15", saturday.first().start)
        assertEquals("09:00", saturday.first().end)
        assertEquals("14:40", saturday.last().start)
        assertEquals("15:25", saturday.last().end)
    }

    @Test
    fun testGetTimeForNumber() {
        // Стандартный день (Пн = 1): 1 пара (08:15 - 09:55)
        val pair1 = CollegeBellSchedule.getTimeForNumber(1, dayOfWeek = 1)
        assertEquals("08:15", pair1.first)
        assertEquals("09:55", pair1.second)

        // Четверг (4): 4 пара начинается в 14:45
        val pair4Thu = CollegeBellSchedule.getTimeForNumber(4, dayOfWeek = 4)
        assertEquals("14:45", pair4Thu.first)
        assertEquals("16:25", pair4Thu.second)
    }

    @Test
    fun testBinaryDocParserWithCellDelimiters() {
        // Имитируем бинарный поток Word .doc с CP1251 текстом и символами 0x07 ячеек таблицы
        val baos = ByteArrayOutputStream()

        // Заголовок таблицы
        baos.write("Группа 41О\u0007Понедельник\u0007\r\n".toByteArray(charset("windows-1251")))

        // 1 пара с 2 подгруппами (каб. 215 / каб. 308)
        baos.write("1 пара\u0007Информатика\u0007каб. 215 / каб. 308\u0007Иванов / Петров\u0007\r\n".toByteArray(charset("windows-1251")))

        // 2 пара
        baos.write("2 пара\u0007Математика\u0007каб. 104\u0007Сидоров\u0007\r\n".toByteArray(charset("windows-1251")))

        val bytes = baos.toByteArray()
        val lessons = MpkScheduleParser.parseDocBinary(bytes, targetGroup = "41О", targetDate = "15.09.2026")

        assertFalse(lessons.isEmpty())
        assertEquals(2, lessons.size)

        val lesson1 = lessons[0]
        assertEquals("41О", lesson1.groupName)
        assertEquals(1, lesson1.lessonNumber)
        assertTrue(lesson1.isSplit)
        assertEquals("215", lesson1.roomFirst)
        assertEquals("308", lesson1.roomSecond)

        val lesson2 = lessons[1]
        assertEquals(2, lesson2.lessonNumber)
        assertFalse(lesson2.isSplit)
        assertEquals("104", lesson2.roomFirst)
    }

    @Test
    fun testNetworkCandidateUrls() {
        val client = MpkNetworkClient()
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 15) // Вторник, 15.09.2026
        }

        val directUrls = client.generateCandidateDirectUrls(cal)
        assertTrue(directUrls.isNotEmpty())
        assertTrue(directUrls.any { it.first.contains("15.09.2026-raspisanie-uchashhihsya.doc") })

        val eventUrls = client.generateCandidateEventUrls(cal)
        assertTrue(eventUrls.isNotEmpty())
        assertTrue(eventUrls.any { it.first.contains("15-09-2026-raspisanie-uchashhihsya") })
    }
}
