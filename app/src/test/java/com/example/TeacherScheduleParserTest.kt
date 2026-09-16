package com.example

import com.example.data.network.TeacherScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты парсера расписания преподавателей на реальном документе
 * guo-mpk.by (16.09.2026-raspisanie-prepodavatelej.doc).
 *
 * Документ — транспонированная таблица: строки это преподаватели,
 * колонки — номера уроков (1..10). Значения в ячейках: код группы,
 * сокращение дисциплины и номер кабинета.
 */
class TeacherScheduleParserTest {

    private fun realDocBytes(): ByteArray =
        javaClass.getResourceAsStream("/16.09.2026-raspisanie-prepodavatelej.doc")!!.readBytes()

    private fun parsed() = TeacherScheduleParser.parse(realDocBytes())

    @Test
    fun `документ распознаётся и содержит преподавателей`() {
        val result = parsed()
        assertTrue("Должны найтись преподаватели", result.isNotEmpty())
        assertTrue("Преподавателей должно быть много (в документе ~80)", result.size >= 50)
    }

    @Test
    fun `Базулина ведёт уроки у 41П с кабинетом 301`() {
        val teacher = parsed().entries.firstOrNull { it.key.startsWith("Базулина") }
        assertNotNull("Базулина Т.Г. должна быть в расписании", teacher)

        val slots = teacher!!.value
        // В документе: 2 урок — 32Э (301), 3 и 4 уроки — 41П (301)
        val lessonTo41P = slots.filter { it.groups.contains("41П") }
        assertTrue("Базулина ведёт у 41П", lessonTo41P.isNotEmpty())
        assertEquals("каб. 301", "301", lessonTo41P.first().room)
        assertTrue("Номера уроков в диапазоне 1..10", slots.all { it.lessonNumber in 1..10 })
    }

    @Test
    fun `кабинеты извлекаются как отдельный вид значения`() {
        val result = parsed()
        // Все занятия с непустым кабинетом должны содержать только цифры/СТД
        val withRoom = result.values.flatten().filter { it.room.isNotBlank() }
        assertTrue("Должны быть занятия с кабинетами", withRoom.isNotEmpty())
        withRoom.forEach { slot ->
            assertTrue(
                "Кабинет «${slot.room}» не должен быть группой или дисциплиной",
                !slot.room.matches(Regex("[1-4][1-9][А-Я]"))
            )
        }
    }

    @Test
    fun `группы в записях выглядят как коды групп`() {
        val result = parsed()
        val allGroups = result.values.flatten().flatMap { it.groups }.distinct()
        assertTrue("Группы должны быть найдены", allGroups.isNotEmpty())
        allGroups.forEach { g ->
            assertTrue("«$g» не похоже на код группы", g.matches(Regex("[1-4][1-9][А-Я]")))
        }
        // В документе точно есть эти группы
        assertTrue("41П должна встречаться", allGroups.contains("41П"))
        assertTrue("31П должна встречаться", allGroups.contains("31П"))
    }

    @Test
    fun `дисциплины извлекаются как текст`() {
        val result = parsed()
        val withSubject = result.values.flatten().filter { it.subject.isNotBlank() }
        assertTrue("Дисциплины должны быть найдены", withSubject.isNotEmpty())
        // Дисциплины не должны быть просто числами (иначе спутаны с кабинетами)
        withSubject.take(20).forEach { slot ->
            assertTrue(
                "«${slot.subject}» похоже на кабинет, а не дисциплину",
                !slot.subject.matches(Regex("[0-9]{1,3}"))
            )
        }
    }

    @Test
    fun `расписание отсортировано по номеру урока`() {
        val result = parsed()
        result.values.forEach { slots ->
            val numbers = slots.map { it.lessonNumber }
            assertEquals("Уроки должны идти по возрастанию", numbers.sorted(), numbers)
            assertTrue("Нет дублей уроков", numbers.distinct().size == numbers.size)
        }
    }

    @Test
    fun `конкретный преподаватель имеет ожидаемое число занятий`() {
        val teacher = parsed().entries.firstOrNull { it.key.startsWith("Бирюк") }
        assertNotNull(teacher)
        // В документе: 1,2 — 31П (213); 3,4 — 41О (306); 5,6 — 32Н (306); 7,8 — 32О (213)
        val slots = teacher!!.value
        assertTrue("У Бирюка должно быть не меньше 6 занятий", slots.size >= 6)
        val groups = slots.flatMap { it.groups }.distinct()
        assertTrue("Бирюк ведёт у 41О", groups.contains("41О"))
        assertTrue("Бирюк ведёт у 31П", groups.contains("31П"))
    }
}
