package com.example

import com.example.data.model.CollegeBellSchedule
import com.example.data.model.TaskType
import com.example.ui.viewmodel.AppTab
import com.example.util.GroupParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step2UiLogicTest {

    @Test
    fun `test bell schedule business rules`() {
        assertEquals(12, CollegeBellSchedule.BELLS.size)
        assertTrue(CollegeBellSchedule.DISCLAIMER.contains("расписание звонков колледжа МГПК на 2026 г."))

        // 1-й урок (1 пара): 08:15-09:00
        val firstLesson = CollegeBellSchedule.getBellForNumber(1)
        assertNotNull(firstLesson)
        assertEquals("08:15", firstLesson?.start)
        assertEquals("09:00", firstLesson?.end)

        // 12-й урок: 18:50-19:35
        val twelfthLesson = CollegeBellSchedule.getBellForNumber(12)
        assertNotNull(twelfthLesson)
        assertEquals("18:50", twelfthLesson?.start)
        assertEquals("19:35", twelfthLesson?.end)

        // Тест времени для пар
        val pair1 = CollegeBellSchedule.getTimeForNumber(1, dayOfWeek = 1)
        assertEquals("08:15", pair1.first)
        assertEquals("09:55", pair1.second)

        // Тест определения текущего слота (09:00 -> 1 урок)
        val slotAt9am = CollegeBellSchedule.getCurrentSlot(9 * 60, dayOfWeek = 2)
        assertNotNull(slotAt9am)
        assertEquals(1, slotAt9am?.lessonNumber)
    }

    @Test
    fun `test task types availability per course`() {
        // 1 курс
        val course1Tasks = TaskType.getAvailableForCourse(1)
        assertFalse(course1Tasks.contains(TaskType.COURSE_WORK))
        assertFalse(course1Tasks.contains(TaskType.DIPLOMA))
        assertTrue(course1Tasks.contains(TaskType.HOMEWORK))

        // 2 курс
        val course2Tasks = TaskType.getAvailableForCourse(2)
        assertTrue(course2Tasks.contains(TaskType.COURSE_WORK))
        assertFalse(course2Tasks.contains(TaskType.DIPLOMA))

        // 4 курс
        val course4Tasks = TaskType.getAvailableForCourse(4)
        assertTrue(course4Tasks.contains(TaskType.COURSE_WORK))
        assertTrue(course4Tasks.contains(TaskType.DIPLOMA))
        assertEquals("ДЗ", TaskType.HOMEWORK.badge)
    }

    @Test
    fun `test group parsing saturday and diploma rules`() {
        val group11E = GroupParser.parse("11Э")
        assertNotNull(group11E)
        assertTrue(group11E?.hasSaturdayClasses == true)
        assertFalse(group11E?.canHaveDiploma == true)

        val group41O = GroupParser.parse("41О")
        assertNotNull(group41O)
        assertFalse(group41O?.hasSaturdayClasses == true)
        assertTrue(group41O?.canHaveCourseWork == true)
        assertTrue(group41O?.canHaveDiploma == true)
    }
}
