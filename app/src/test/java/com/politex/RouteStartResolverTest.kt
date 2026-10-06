package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.ui.util.RouteStartResolver
import com.politex.util.SubgroupPref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteStartResolverTest {
    private fun lesson(
        n: Int,
        start: String,
        room: String,
        room2: String = "",
        split: Boolean = false,
    ) = LessonEntity(
        groupName = "11А",
        dayOfWeek = 1,
        lessonNumber = n,
        timeStart = start,
        timeEnd = start,
        subjectRaw = "Математика",
        roomFirst = room,
        teacherFirst = "",
        roomSecond = room2,
        isSplit = split,
    )

    private val lessons =
        listOf(
            lesson(1, "08:30", "118"),
            lesson(2, "09:25", "223"),
            lesson(3, "10:35", "320"),
        )

    private fun at(
        h: Int,
        m: Int,
    ) = h * 60 + m

    @Test
    fun `до уроков — null (маршрут от главного входа)`() {
        assertNull(RouteStartResolver.previousRoomNow(lessons, at(8, 0)))
    }

    @Test
    fun `во время второго урока — кабинет второго`() {
        assertEquals("223", RouteStartResolver.previousRoomNow(lessons, at(10, 30)))
    }

    @Test
    fun `перемена после первого — кабинет первого`() {
        assertEquals("118", RouteStartResolver.previousRoomNow(lessons, at(9, 15)))
    }

    @Test
    fun `вечер после уроков — кабинет последнего`() {
        assertEquals("320", RouteStartResolver.previousRoomNow(lessons, at(20, 0)))
    }

    @Test
    fun `пустой день — null`() {
        assertNull(RouteStartResolver.previousRoomNow(emptyList(), at(12, 0)))
    }

    @Test
    fun `кабинет только у второй подгруппы — берётся roomSecond`() {
        val split = listOf(lesson(1, "08:30", "", "118"))
        assertEquals("118", RouteStartResolver.previousRoomNow(split, at(9, 0)))
    }

    @Test
    fun `без кабинета у начавшегося урока — null, а не пустая строка`() {
        val noRoom = listOf(lesson(1, "08:30", " "))
        assertNull(RouteStartResolver.previousRoomNow(noRoom, at(9, 0)))
    }

    @Test
    fun `V21 сегодня, ничего не началось — пустая строка (от главного входа)`() {
        assertEquals("", RouteStartResolver.startForLesson(lessons, lessons[0], isToday = true, nowMinutes = at(8, 0)))
    }

    @Test
    fun `V21 сегодня, во время второго урока — старт как previousRoomNow`() {
        assertEquals("223", RouteStartResolver.startForLesson(lessons, lessons[2], isToday = true, nowMinutes = at(10, 30)))
    }

    @Test
    fun `V21 не сегодня — кабинет ближайшего предыдущего урока`() {
        assertEquals("223", RouteStartResolver.startForLesson(lessons, lessons[2], isToday = false, nowMinutes = 0))
    }

    @Test
    fun `V21 не сегодня, первый урок — пустая строка (от главного входа)`() {
        assertEquals("", RouteStartResolver.startForLesson(lessons, lessons[0], isToday = false, nowMinutes = 0))
    }

    @Test
    fun `GG2 не сегодня, у предыдущего только roomSecond — берётся roomSecond (как previousRoomNow)`() {
        val onlySecond = listOf(lesson(1, "08:30", "", "118"), lesson(2, "09:25", "", "223"))
        assertEquals("118", RouteStartResolver.startForLesson(onlySecond, onlySecond[1], isToday = false, nowMinutes = 0))
    }

    @Test
    fun `V21 не сегодня, пустой день — пустая строка`() {
        assertEquals("", RouteStartResolver.startForLesson(emptyList(), lessons[0], isToday = false, nowMinutes = 0))
    }

    // --- GG2/GG3: «откуда» для split-уроков ---

    private val splitDay =
        listOf(
            lesson(1, "08:30", "118", "224", split = true),
            lesson(2, "09:25", "320"),
            lesson(3, "10:35", "401", "402", split = true),
        )

    @Test
    fun `GG2 prev non-split — from = комната предыдущего, без вопроса`() {
        // тапнут ТЕКУЩИЙ split-урок (3) — он даёт только цель; источник = урок 2
        assertEquals(
            RouteStartResolver.RouteStart.From("320"),
            RouteStartResolver.startForLesson(splitDay, splitDay[2], isToday = false, nowMinutes = 0, pref = SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `GG2 prev split + NEUTRAL — вопрос с двумя комнатами ПРЕДЫДУЩЕГО урока`() {
        assertEquals(
            RouteStartResolver.RouteStart.Ask("118", "224"),
            RouteStartResolver.startForLesson(splitDay, splitDay[1], isToday = false, nowMinutes = 0, pref = SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `GG3 prev split + FIRST — from = roomFirst предыдущего, без вопроса`() {
        assertEquals(
            RouteStartResolver.RouteStart.From("118"),
            RouteStartResolver.startForLesson(splitDay, splitDay[1], isToday = false, nowMinutes = 0, pref = SubgroupPref.FIRST),
        )
    }

    @Test
    fun `GG3 prev split + SECOND — from = roomSecond предыдущего, без вопроса`() {
        assertEquals(
            RouteStartResolver.RouteStart.From("224"),
            RouteStartResolver.startForLesson(splitDay, splitDay[1], isToday = false, nowMinutes = 0, pref = SubgroupPref.SECOND),
        )
    }

    @Test
    fun `GG2 нет предыдущего — главный вход даже для split-текущего`() {
        val day = listOf(lesson(1, "08:30", "401", "402", split = true))
        assertEquals(
            RouteStartResolver.RouteStart.From(""),
            RouteStartResolver.startForLesson(day, day[0], isToday = false, nowMinutes = 0, pref = SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `GG2 сегодня prev split уже начался + NEUTRAL — вопрос`() {
        assertEquals(
            RouteStartResolver.RouteStart.Ask("118", "224"),
            RouteStartResolver.startForLesson(splitDay, splitDay[1], isToday = true, nowMinutes = at(9, 0), pref = SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `GG2 сегодня prev split ещё не начался — предыдущего нет, главный вход`() {
        // 08:00 — урок 1 (split) ещё не начался → предыдущего нет → главный вход
        assertEquals(
            RouteStartResolver.RouteStart.From(""),
            RouteStartResolver.startForLesson(splitDay, splitDay[1], isToday = true, nowMinutes = at(8, 0), pref = SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `GG2 сегодня тапнут текущий начавшийся split — источник НЕ он сам, а более ранний`() {
        // 08:45 — урок 1 (split) начался, тапаем ЕГО же кабинет: prev по номеру отсутствует → вход
        assertEquals(
            RouteStartResolver.RouteStart.From(""),
            RouteStartResolver.startForLesson(splitDay, splitDay[0], isToday = true, nowMinutes = at(8, 45), pref = SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `GG2 String-совместимость — Ask схлопывается в roomFirst`() {
        assertEquals(
            "118",
            RouteStartResolver.startForLesson(splitDay, splitDay[1], isToday = false, nowMinutes = 0),
        )
    }

    @Test
    fun `GG2 prev split с одной незаполненной комнатой — не вопрос, а комната`() {
        val day = listOf(lesson(1, "08:30", "118", split = true), lesson(2, "09:25", "320"))
        assertEquals(
            RouteStartResolver.RouteStart.From("118"),
            RouteStartResolver.startForLesson(day, day[1], isToday = false, nowMinutes = 0, pref = SubgroupPref.NEUTRAL),
        )
    }
}
