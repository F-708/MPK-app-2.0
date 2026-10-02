package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.components.currentRoomLessonNumber
import com.politex.ui.components.filterRoomLessonsForDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomScheduleLogicTest {
    private fun lesson(
        lessonNumber: Int,
        roomFirst: String,
        roomSecond: String = "",
        groupName: String = "41О",
        dateString: String = "",
        dayOfWeek: Int = 1,
        teacherFirst: String = "Иванова А.А.",
        teacherSecond: String = "",
    ) = LessonEntity(
        groupName = groupName,
        dayOfWeek = dayOfWeek,
        lessonNumber = lessonNumber,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = "Иностранный язык",
        roomFirst = roomFirst,
        teacherFirst = teacherFirst,
        roomSecond = roomSecond,
        teacherSecond = teacherSecond,
        isSplit = roomSecond.isNotBlank(),
        dateString = dateString,
    )

    @Test
    fun `фильтр по кабинету roomFirst и roomSecond (дробные пары)`() {
        val lessons =
            listOf(
                lesson(1, roomFirst = "215"),
                lesson(2, roomFirst = "101"),
                lesson(3, roomFirst = "101", roomSecond = "215"), // дробная пара — 215 вторая подгруппа
                lesson(4, roomFirst = "302"),
            )
        val result = filterRoomLessonsForDate(lessons, "215", "22.09.2026")
        assertEquals(listOf(1, 3), result.map { it.lessonNumber })
    }

    @Test
    fun `точная дата приоритетнее пустого dateString`() {
        val lessons =
            listOf(
                lesson(1, roomFirst = "215", dateString = "22.09.2026"),
                lesson(2, roomFirst = "215", dateString = ""), // шаблон дня недели
                lesson(3, roomFirst = "215", dateString = "25.09.2026"), // другая дата — игнорируется
            )
        val result = filterRoomLessonsForDate(lessons, "215", "22.09.2026")
        assertEquals(listOf(1), result.map { it.lessonNumber })
    }

    @Test
    fun `фолбэк на пустой dateString когда точных нет`() {
        val lessons =
            listOf(
                lesson(2, roomFirst = "215", dateString = ""),
                lesson(1, roomFirst = "215", dateString = ""),
            )
        val result = filterRoomLessonsForDate(lessons, "215", "22.09.2026")
        // сортировка по lessonNumber
        assertEquals(listOf(1, 2), result.map { it.lessonNumber })
    }

    @Test
    fun `пустой кабинет или нет совпадений — пустой список`() {
        assertTrue(filterRoomLessonsForDate(emptyList(), "215", "22.09.2026").isEmpty())
        assertTrue(
            filterRoomLessonsForDate(
                listOf(lesson(1, roomFirst = "101")),
                "215",
                "22.09.2026",
            ).isEmpty(),
        )
        assertTrue(
            filterRoomLessonsForDate(
                listOf(lesson(1, roomFirst = "215")),
                "",
                "22.09.2026",
            ).isEmpty(),
        )
    }

    @Test
    fun `текущая пара по звонкам — внутри урока и на перемене`() {
        // Пн, 3-й урок 10:20–11:05
        assertEquals(3, currentRoomLessonNumber(10 * 60 + 30, 1))
        // Перемена между 2 (до 09:55) и 3 (с 10:20) уроками
        assertEquals(null, currentRoomLessonNumber(10 * 60 + 5, 1))
        // До начала занятий
        assertEquals(null, currentRoomLessonNumber(7 * 60, 1))
        // Суббота: 1-й урок 08:15–09:00
        assertEquals(1, currentRoomLessonNumber(8 * 60 + 30, 6))
    }

    @Test
    fun `инфочас четверга не считается парой`() {
        // Четверг, информационный час 14:15–14:35, lessonNumber = 0
        assertEquals(null, currentRoomLessonNumber(14 * 60 + 20, 4))
    }

    @Test
    fun `подтверждение источника слота — CollegeBellSchedule getCurrentSlot`() {
        // Заодно проверяем контракт: слот урока 1 в понедельник 08:15–09:00
        val slot = CollegeBellSchedule.getCurrentSlot(8 * 60 + 45, 1)
        assertEquals(1, slot?.lessonNumber)
    }
}
