package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.screens.GroupRoomResult
import com.politex.ui.screens.findGroupRoomNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Т5: логика «где группа сейчас» для поиска по группе на карте колледжа.
 *
 * Опорные звонки (STANDARD, Пн): 1-й 08:15–09:00, 3-й 10:20–11:05, 5-й 12:25–13:10.
 */
class GroupRoomLookupTest {
    private fun lesson(
        number: Int,
        roomFirst: String,
        roomSecond: String = "",
        isSplit: Boolean = false,
        dateString: String = "01.09.2026",
        timeStart: String = "",
    ) = LessonEntity(
        groupName = "41О",
        dayOfWeek = 1,
        lessonNumber = number,
        timeStart = timeStart,
        timeEnd = "",
        subjectRaw = "Тест",
        roomFirst = roomFirst,
        teacherFirst = "",
        roomSecond = roomSecond,
        teacherSecond = "",
        isSplit = isSplit,
        dateString = dateString,
    )

    // ---------- Урок идёт сейчас ----------

    @Test
    fun currentLesson_roomFromSlot() {
        // 10:30 внутри 3-го урока (10:20–11:05) → кабинет 214
        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(3, "214"), lesson(5, "101")),
                currentMinutes = 10 * 60 + 30,
                dayOfWeek = 1,
            )
        assertNotNull(res)
        assertEquals("214", res!!.room)
        assertEquals(3, res.lessonNumber)
        assertEquals(true, res.isNow)
    }

    @Test
    fun currentSlotButGroupHasNoLesson_fallsToNext() {
        // 10:30 — идёт 3-й урок, но у группы его нет → ближайший 5-й, «к 5 уроку»
        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(5, "101")),
                currentMinutes = 10 * 60 + 30,
                dayOfWeek = 1,
            )
        assertNotNull(res)
        assertEquals("101", res!!.room)
        assertEquals(5, res.lessonNumber)
        assertEquals(false, res.isNow)
    }

    @Test
    fun splitLesson_prefersRoomFirst() {
        // split-урок: подгруппу надёжно не определить — берём roomFirst
        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(3, "214", "305", isSplit = true)),
                currentMinutes = 10 * 60 + 30,
                dayOfWeek = 1,
            )
        assertEquals("214", res!!.room)
    }

    @Test
    fun roomFirstBlank_fallsToRoomSecond() {
        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(3, "", "305")),
                currentMinutes = 10 * 60 + 30,
                dayOfWeek = 1,
            )
        assertEquals("305", res!!.room)
    }

    // ---------- Между уроками → следующий ----------

    @Test
    fun betweenLessons_nextLessonRoom() {
        // 11:30 — после 3-го, до 5-го (12:25) → комната 5-го урока, метка «к 5 уроку»
        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(3, "214"), lesson(5, "101")),
                currentMinutes = 11 * 60 + 30,
                dayOfWeek = 1,
            )
        assertNotNull(res)
        assertEquals("101", res!!.room)
        assertEquals(5, res.lessonNumber)
        assertEquals(false, res.isNow)
    }

    @Test
    fun afterAllLessons_null() {
        // 20:00 — все уроки закончились, следующего нет
        assertNull(
            findGroupRoomNow(listOf(lesson(3, "214")), 20 * 60, 1),
        )
    }

    @Test
    fun beforeAllLessons_firstLesson() {
        // 07:30 — до 1-го урока (08:15) → кабинет 1-го урока, «к 1 уроку»
        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(3, "214"), lesson(1, "102")),
                currentMinutes = 7 * 60 + 30,
                dayOfWeek = 1,
            )
        assertNotNull(res)
        assertEquals("102", res!!.room)
        assertEquals(1, res.lessonNumber)
        assertEquals(false, res.isNow)
    }

    // ---------- Суббота: расписание звонков своё (8 уроков) ----------

    @Test
    fun saturdayBellsUsed() {
        // Суббота, 14:00 — между 7-м (13:45–14:30)? нет: 14:00 внутри 7-го урока субботы
        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(7, "305"), lesson(8, "101")),
                currentMinutes = 14 * 60,
                dayOfWeek = 6,
            )
        assertNotNull(res)
        assertEquals("305", res!!.room)
        assertEquals(true, res.isNow)
    }

    // ---------- Пустые данные ----------

    @Test
    fun emptyLessons_null() {
        assertNull(findGroupRoomNow(emptyList(), 10 * 60, 1))
    }

    @Test
    fun noRoomOnLesson_null() {
        // урок есть, но кабинет не указан → результата нет
        assertNull(findGroupRoomNow(listOf(lesson(3, "")), 10 * 60 + 30, 1))
    }

    @Test
    fun timeStartFallbackWhenBellMissing() {
        // номер урока вне сетки звонков (например, 11 при пустых звонках дня не находится) —
        // фолбэк на timeStart строки: урок 11 в 17:55, сейчас 12:00 → «к 11 уроку»
        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(11, "214", timeStart = "17:55")),
                currentMinutes = 12 * 60,
                dayOfWeek = 1,
            )
        assertNotNull(res)
        assertEquals("214", res!!.room)
        assertEquals(11, res.lessonNumber)
    }

    // ---------- Слот по звонкам напрямую (гейт логики) ----------

    @Test
    fun bellSlotMapsCorrectly() {
        // Пн, 10:30 → 3-й урок (10:20–11:05); сравнение как в ScheduleScreen
        val bells = CollegeBellSchedule.getBellsForDay(1)
        assertEquals(3, bells.firstOrNull { 10 * 60 + 30 in it.startMinutes..it.endMinutes }?.lessonNumber)
    }

    @Test
    fun resultShape_isSerializableDataClass() {
        // Доступ к полям из UI-слоя без сюрпризов
        val r = GroupRoomResult(room = "214", lessonNumber = 3, isNow = true)
        assertEquals("214", r.room)
    }
}
