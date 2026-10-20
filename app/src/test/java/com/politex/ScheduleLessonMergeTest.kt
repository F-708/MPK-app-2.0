package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.ui.screens.ScheduleLessonSlot
import com.politex.ui.screens.canMergeLessons
import com.politex.ui.screens.mergedLessonSlots
import com.politex.ui.screens.scheduleSlotIndexOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLessonMergeTest {
    private fun lesson(
        number: Int,
        subject: String = "Математика",
        teacher: String = "Ковалев В.П.",
        room: String = "204",
        group: String = "41О",
        dow: Int = 2,
        date: String = "",
        split: Boolean = false,
    ) = LessonEntity(
        groupName = group,
        dayOfWeek = dow,
        lessonNumber = number,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = subject,
        roomFirst = room,
        teacherFirst = teacher,
        roomSecond = if (split) "118" else "",
        teacherSecond = if (split) "Смирнов А.В." else "",
        isSplit = split,
        dateString = date,
    )

    @Test
    fun `одинаковые подряд уроки объединяются`() {
        assertTrue(canMergeLessons(lesson(1), lesson(2), teacherMode = false))
    }

    @Test
    fun `разный кабинет не объединяется`() {
        assertFalse(canMergeLessons(lesson(1), lesson(2, room = "118"), teacherMode = false))
    }

    @Test
    fun `разный преподаватель не объединяется`() {
        assertFalse(canMergeLessons(lesson(1), lesson(2, teacher = "Смирнов А.В."), teacherMode = false))
    }

    @Test
    fun `разный предмет не объединяется`() {
        assertFalse(canMergeLessons(lesson(1), lesson(2, subject = "Физика"), teacherMode = false))
    }

    @Test
    fun `предмет сравнивается после normalize`() {
        assertTrue(canMergeLessons(lesson(1, subject = "Математика"), lesson(2, subject = "математика"), teacherMode = false))
    }

    @Test
    fun `пустой кабинет не объединяется`() {
        assertFalse(canMergeLessons(lesson(1, room = ""), lesson(2, room = ""), teacherMode = false))
    }

    @Test
    fun `пустой преподаватель не объединяется`() {
        assertFalse(canMergeLessons(lesson(1, teacher = ""), lesson(2, teacher = ""), teacherMode = false))
    }

    @Test
    fun `не подряд по номеру не объединяется`() {
        assertFalse(canMergeLessons(lesson(1), lesson(3), teacherMode = false))
        assertFalse(canMergeLessons(lesson(2), lesson(1), teacherMode = false))
    }

    @Test
    fun `сплит уроки не объединяются`() {
        assertFalse(canMergeLessons(lesson(1, split = true), lesson(2), teacherMode = false))
        assertFalse(canMergeLessons(lesson(1), lesson(2, split = true), teacherMode = false))
    }

    @Test
    fun `разные даты не объединяются`() {
        assertFalse(canMergeLessons(lesson(1, date = "20.10.2026"), lesson(2, date = ""), teacherMode = false))
        assertFalse(canMergeLessons(lesson(1, dow = 1), lesson(2, dow = 2), teacherMode = false))
    }

    @Test
    fun `teacher режим объединяет по группе а не по преподавателю`() {
        val a = lesson(1, teacher = "Иванов И.И.", group = "41О")
        val b = lesson(2, teacher = "Петров П.П.", group = "41О")
        assertFalse(canMergeLessons(a, b, teacherMode = false))
        assertTrue(canMergeLessons(a, b, teacherMode = true))
        assertFalse(canMergeLessons(a, lesson(2, teacher = "Иванов И.И.", group = "42О"), teacherMode = true))
        assertFalse(canMergeLessons(a, lesson(2, teacher = "Иванов И.И.", group = ""), teacherMode = true))
    }

    @Test
    fun `слоты - пустой список даёт пустой результат`() {
        assertTrue(mergedLessonSlots(emptyList(), teacherMode = false).isEmpty())
    }

    @Test
    fun `слоты - одиночный урок`() {
        val slots = mergedLessonSlots(listOf(lesson(1)), teacherMode = false)
        assertEquals(listOf(ScheduleLessonSlot.Single(lesson(1))), slots)
    }

    @Test
    fun `слоты - первая пара объединяется, третий отдельно`() {
        val slots = mergedLessonSlots(listOf(lesson(1), lesson(2), lesson(3)), teacherMode = false)
        assertEquals(2, slots.size)
        assertTrue(slots[0] is ScheduleLessonSlot.Merged)
        assertEquals(1, slots[0].key)
        assertEquals(ScheduleLessonSlot.Single(lesson(3)), slots[1])
    }

    @Test
    fun `слоты - неодинаковые соседи не объединяются`() {
        val slots = mergedLessonSlots(listOf(lesson(1), lesson(2, room = "118")), teacherMode = false)
        assertEquals(
            listOf(
                ScheduleLessonSlot.Single(lesson(1)),
                ScheduleLessonSlot.Single(lesson(2, room = "118")),
            ),
            slots,
        )
    }

    @Test
    fun `слоты - дырки сохраняются как Empty`() {
        val slots = mergedLessonSlots(listOf(lesson(1), lesson(3, room = "118")), teacherMode = false)
        assertEquals(
            listOf(
                ScheduleLessonSlot.Single(lesson(1)),
                ScheduleLessonSlot.Empty(2),
                ScheduleLessonSlot.Single(lesson(3, room = "118")),
            ),
            slots,
        )
    }

    @Test
    fun `слоты - слияние через отсутствующий номер невозможно`() {
        val slots = mergedLessonSlots(listOf(lesson(1), lesson(3)), teacherMode = false)
        assertEquals(3, slots.size)
        assertTrue(slots[0] is ScheduleLessonSlot.Single)
        assertTrue(slots[1] is ScheduleLessonSlot.Empty)
        assertTrue(slots[2] is ScheduleLessonSlot.Single)
    }

    @Test
    fun `слоты - пара в конце списка`() {
        val slots = mergedLessonSlots(listOf(lesson(1, room = "118"), lesson(2), lesson(3)), teacherMode = false)
        assertEquals(2, slots.size)
        assertTrue(slots[0] is ScheduleLessonSlot.Single)
        assertTrue(slots[1] is ScheduleLessonSlot.Merged)
        assertEquals(2, slots[1].key)
    }

    @Test
    fun `слоты - teacher режим объединяет одинаковые группы`() {
        val a = lesson(1, teacher = "Иванов И.И.")
        val b = lesson(2, teacher = "Петров П.П.")
        assertEquals(2, mergedLessonSlots(listOf(a, b), teacherMode = false).size)
        val merged = mergedLessonSlots(listOf(a, b), teacherMode = true)
        assertEquals(1, merged.size)
        assertTrue(merged[0] is ScheduleLessonSlot.Merged)
    }

    @Test
    fun `slotIndexOf - находит оба урока пары и границы`() {
        val slots = mergedLessonSlots(listOf(lesson(1), lesson(2), lesson(4, room = "118")), teacherMode = false)
        assertEquals(0, scheduleSlotIndexOf(slots, 1))
        assertEquals(0, scheduleSlotIndexOf(slots, 2))
        assertEquals(2, scheduleSlotIndexOf(slots, 4))
        assertNull(scheduleSlotIndexOf(slots, 3))
        assertNull(scheduleSlotIndexOf(slots, 9))
    }
}
