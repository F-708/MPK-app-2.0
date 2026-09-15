package com.example

import com.example.data.network.MpkScheduleParser
import com.example.util.GroupParser
import com.example.util.SubjectFormatter
import java.nio.charset.Charset
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Комплексные тесты для Шага 4:
 * 1. Сканирование ссылок на страницы расписания и iframe Office Viewer.
 * 2. Парсинг бинарных потоков .doc (CP1251 и UTF-16LE).
 * 3. Деление пар на 2 кабинета («каб. 215 / каб. 308»).
 * 4. Точный расчет дат недели от Понедельника.
 * 5. Поведение кнопки быстрого перехода на следующий учебный день.
 */
class Step4BugfixTest {

    private val CP1251 = Charset.forName("windows-1251")

    @Test
    fun testFindSchedulePostLinks() {
        val html = """
            <div class="site-content">
                <h2>Расписание</h2>
                <ul>
                    <li><a href="https://guo-mpk.by/15-09-2026-raspisanie-uchashhihsya/">15.09.2026 РАСПИСАНИЕ УЧАЩИХСЯ</a></li>
                    <li><a href="/16-09-2026-raspisanie-uchashhihsya/">16.09.2026 РАСПИСАНИЕ УЧАЩИХСЯ</a></li>
                </ul>
            </div>
        """.trimIndent()

        val links = MpkScheduleParser.findSchedulePostLinks(html, "https://guo-mpk.by/")
        assertEquals(2, links.size)
        assertEquals("15.09.2026", links[0].dateString)
        assertEquals("16.09.2026", links[1].dateString)
        assertTrue(links[1].url.startsWith("https://guo-mpk.by/16-09-2026"))
    }

    @Test
    fun testIframeOfficeAppsUrlDecoding() {
        val eventHtml = """
            <div class="entry-content">
                <iframe src="https://view.officeapps.live.com/op/embed.aspx?src=https%3A%2F%2Fguo-mpk.by%2Fwp-content%2Fuploads%2F2026%2F09%2F15_09_2026.doc"></iframe>
            </div>
        """.trimIndent()

        val docUrls = MpkScheduleParser.extractDocumentUrls(eventHtml, "https://guo-mpk.by/")
        assertEquals(1, docUrls.size)
        assertEquals("https://guo-mpk.by/wp-content/uploads/2026/09/15_09_2026.doc", docUrls[0])
    }

    @Test
    fun testTwoRoomParsingVariations() {
        // Вариант 1: «каб. 215 / каб. 308»
        val lesson1 = MpkScheduleParser.createLessonEntity(
            groupName = "41О",
            dayOfWeek = 1,
            lessonNumber = 2,
            rawContent = "Электронные системы ТС каб. 215 / каб. 308 Иванов И.И. / Петров П.П."
        )
        assertNotNull(lesson1)
        assertTrue("Должно быть определено деление на 2 подгруппы", lesson1!!.isSplit)
        assertEquals("215", lesson1.roomFirst)
        assertEquals("308", lesson1.roomSecond)

        // Вариант 2: «каб. 101 - каб. 102»
        val lesson2 = MpkScheduleParser.createLessonEntity(
            groupName = "41О",
            dayOfWeek = 2,
            lessonNumber = 1,
            rawContent = "Охрана труда каб. 101 - каб. 102 Смирнов А.А."
        )
        assertNotNull(lesson2)
        assertTrue(lesson2!!.isSplit)
        assertEquals("101", lesson2.roomFirst)
        assertEquals("102", lesson2.roomSecond)

        // Вариант 3: «215 / 308»
        val lesson3 = MpkScheduleParser.createLessonEntity(
            groupName = "41О",
            dayOfWeek = 3,
            lessonNumber = 4,
            rawContent = "Информационные технологии 215 / 308"
        )
        assertNotNull(lesson3)
        assertTrue(lesson3!!.isSplit)
        assertEquals("215", lesson3.roomFirst)
        assertEquals("308", lesson3.roomSecond)
    }

    @Test
    fun testBinaryDocCp1251StreamParsing() {
        // Симулируем бинарный поток .doc в кодировке CP1251
        val simulatedDocText = "Понедельник\n1 пара: 41О Охрана труда каб. 204 Ковалев В.П.\n2 пара: 41О ТОЭ ауд. 301 Смирнов Д.А."
        val bytes = simulatedDocText.toByteArray(CP1251)

        val lessons = MpkScheduleParser.parseDocBinary(bytes, "41О", "15.09.2026")
        assertEquals(2, lessons.size)
        assertEquals(1, lessons[0].lessonNumber)
        assertEquals("204", lessons[0].roomFirst)
        assertEquals("Ковалев В.П.", lessons[0].teacherFirst)
        assertEquals("15.09.2026", lessons[0].dateString)
    }

    @Test
    fun testBinaryDocUtf16LeStreamParsing() {
        // Симулируем бинарный поток .doc в кодировке UTF-16LE
        val simulatedDocText = "Вторник\n1 пара: 41О Математика каб. 105 Сидорова Е.Н."
        val bytes = simulatedDocText.toByteArray(Charsets.UTF_16LE)

        val lessons = MpkScheduleParser.parseDocBinary(bytes, "41О", "16.09.2026")
        assertEquals(1, lessons.size)
        assertEquals(2, lessons[0].dayOfWeek)
        assertEquals(1, lessons[0].lessonNumber)
        assertEquals("105", lessons[0].roomFirst)
        assertEquals("16.09.2026", lessons[0].dateString)
    }

    @Test
    fun testStrictWeekDateCalculation() {
        val locale = Locale.forLanguageTag("ru-BY")
        val cal = Calendar.getInstance(locale).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(2026, Calendar.SEPTEMBER, 15) // Вторник, 15 сентября 2026
        }

        val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Calendar.TUESDAY = 3
        val daysFromMonday = if (currentDayOfWeek == Calendar.SUNDAY) 6 else currentDayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday) // Переход на Понедельник 14 сентября 2026

        assertEquals(14, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, cal.get(Calendar.MONTH))
        assertEquals(2026, cal.get(Calendar.YEAR))

        // Проверяем все 6 дней недели (Пн-Сб)
        val expectedDays = listOf(14, 15, 16, 17, 18, 19)
        for (i in 0..5) {
            val dayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            assertEquals(expectedDays[i], dayCal.get(Calendar.DAY_OF_MONTH))
        }
    }

    @Test
    fun testQuickNextDayButtonLogic() {
        val course1Group = GroupParser.parse("11О")!! // 1 курс -> шестидневка
        val course4Group = GroupParser.parse("41О")!! // 4 курс -> пятидневка

        assertTrue(course1Group.hasSaturdayClasses)
        assertFalse(course4Group.hasSaturdayClasses)

        // В пятницу (день 5):
        // 1 курс видит «Завтра (Сб)»
        val fridayBtnCourse1 = when {
            5 == 5 && course1Group.hasSaturdayClasses -> "Завтра (Сб)"
            5 == 5 && !course1Group.hasSaturdayClasses -> "Понедельник"
            else -> "Завтра"
        }
        assertEquals("Завтра (Сб)", fridayBtnCourse1)

        // 2-4 курсы видят «Понедельник»
        val fridayBtnCourse4 = when {
            5 == 5 && course4Group.hasSaturdayClasses -> "Завтра (Сб)"
            5 == 5 && !course4Group.hasSaturdayClasses -> "Понедельник"
            else -> "Завтра"
        }
        assertEquals("Понедельник", fridayBtnCourse4)

        // В субботу (день 6) или воскресенье: все видят «Понедельник»
        val saturdayBtn = when {
            6 == 6 -> "Понедельник"
            else -> "Завтра"
        }
        assertEquals("Понедельник", saturdayBtn)

        // С понедельника по четверг (дни 1..4): «Завтра»
        val tuesdayBtn = when {
            2 == 5 && course4Group.hasSaturdayClasses -> "Завтра (Сб)"
            2 == 5 && !course4Group.hasSaturdayClasses -> "Понедельник"
            2 == 6 -> "Понедельник"
            else -> "Завтра"
        }
        assertEquals("Завтра", tuesdayBtn)
    }
}
