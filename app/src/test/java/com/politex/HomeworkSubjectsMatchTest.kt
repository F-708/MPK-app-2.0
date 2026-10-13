package com.politex

import com.politex.util.HomeworkMarkers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// v2.0: баг «ДЗ не показывается на split-уроках». Урок с разными предметами подгрупп
// хранит subjectRaw = «ПредметA / ПредметB»; subjectsMatch обязан сматчить задачу
// по ЛЮБОЙ из частей, включая короткие предметы («ТОЭ», «Химия»), которые прежний
// contains-fallback (len≥6 у обеих строк) терял.
class HomeworkSubjectsMatchTest {
    private fun match(lesson: String, task: String) = HomeworkMarkers.subjectsMatch(lesson, task)

    @Test
    fun `точный предмет совпадает`() {
        assertTrue(match("Математика", "Математика"))
        assertTrue(match("Физика", "физика")) // ignore-case
    }

    @Test
    fun `split-склейка матчит обе подгруппы`() {
        assertTrue(match("Математика / Информатика", "Математика"))
        assertTrue(match("Математика / Информатика", "Информатика"))
        assertTrue(match("Физкультура / Математика", "Физкультура"))
    }

    @Test
    fun `split с КОРОТКИМ предметом больше не теряется (корень бага)`() {
        // было FAIL: «ТОЭ»(3) и «Химия»(5) короче 6 → contains-fallback не срабатывал
        assertTrue(match("ТОЭ / Электронные приборы", "ТОЭ"))
        assertTrue(match("Химия / Биология", "Химия"))
        assertTrue(match("Химия / Биология", "Биология"))
    }

    @Test
    fun `длинный предмет через contains`() {
        assertTrue(match("История Беларуси в контексте всемирной истории / Обществоведение", "История"))
        assertTrue(match("Иностранный язык (профессиональная лексика) / Физическая культура и здоровье", "Физическая культура и здоровье"))
    }

    @Test
    fun `несовпадение остаётся несовпадением`() {
        assertFalse(match("Математика", "Информатика"))
        assertFalse(match("ТОЭ / Электронные приборы", "Химия"))
        assertFalse(match("", "Математика"))
        assertFalse(match("Математика", ""))
    }
}
