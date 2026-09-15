package com.example

import com.example.data.local.entity.LessonEntity
import com.example.data.model.CollegeBellSchedule
import com.example.util.GroupParser
import com.example.util.NotificationHelper
import com.example.util.SubjectFormatter
import com.example.worker.ScheduleCheckWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step6WidgetsAndWorkerTest {

    @Test
    fun testNextStudyDayTransition_1stCourse_Vs_HigherCourses() {
        // 1 курс (шестидневка)
        val group1stCourse = GroupParser.parse("11Т")
        assertNotNull(group1stCourse)
        assertEquals(1, group1stCourse?.course)
        assertTrue(group1stCourse?.hasSaturdayClasses == true)

        // 4 курс (пятидневка)
        val group4thCourse = GroupParser.parse("41О")
        assertNotNull(group4thCourse)
        assertEquals(4, group4thCourse?.course)
        assertFalse(group4thCourse?.hasSaturdayClasses == true)
    }

    @Test
    fun testSubjectAcronymAndShortNameForWidgets() {
        val rawToe = "Теоретические основы электротехники"
        assertEquals("ТОЭ", SubjectFormatter.getAcronym(rawToe))
        assertTrue(SubjectFormatter.getShortName(rawToe).length <= 25)

        val rawMath = "Математика"
        assertEquals("МАТ", SubjectFormatter.getAcronym(rawMath))
        assertEquals("Математика", SubjectFormatter.getShortName(rawMath))

        val rawPhys = "Физическая культура и здоровье"
        assertEquals("ФКиЗ", SubjectFormatter.getAcronym(rawPhys))
        assertEquals("Физкультура", SubjectFormatter.getShortName(rawPhys))
    }

    @Test
    fun testLessonSplitAndRoomDisplay() {
        val splitLesson = LessonEntity(
            groupName = "41О",
            dayOfWeek = 1,
            lessonNumber = 1,
            timeStart = "08:15",
            timeEnd = "09:55",
            subjectRaw = "Иностранный язык (профессиональная лексика)",
            roomFirst = "215",
            teacherFirst = "Иванова А.А.",
            roomSecond = "308",
            teacherSecond = "Петрова Б.Б.",
            isSplit = true
        )

        assertTrue(splitLesson.isSplit)
        assertEquals("215", splitLesson.roomFirst)
        assertEquals("308", splitLesson.roomSecond)
    }

    @Test
    fun testBellTimesForDayOfWeek() {
        // Понедельник 1 пара (08:15 – 09:55)
        val (monStart, monEnd) = CollegeBellSchedule.getTimeForNumber(1, 1)
        assertEquals("08:15", monStart)
        assertEquals("09:55", monEnd)

        // Четверг 4 пара после инфочаса (14:45 – 16:25)
        val (thu4Start, thu4End) = CollegeBellSchedule.getTimeForNumber(4, 4)
        assertEquals("14:45", thu4Start)
        assertEquals("16:25", thu4End)

        // Суббота 1 пара (08:15 – 09:55)
        val (satStart, satEnd) = CollegeBellSchedule.getTimeForNumber(1, 6)
        assertEquals("08:15", satStart)
        assertEquals("09:55", satEnd)
    }

    @Test
    fun testNotificationChannelConstants() {
        assertEquals("mpk_schedule_updates", NotificationHelper.CHANNEL_ID)
        assertEquals("Обновления расписания МГПК", NotificationHelper.CHANNEL_NAME)
        assertEquals(2026, NotificationHelper.NOTIFICATION_ID)
    }

    @Test
    fun testWorkManagerConstants() {
        assertEquals("mpk_schedule_check_worker", ScheduleCheckWorker.WORK_NAME)
    }
}
