package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.ui.util.RouteStartResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteStartResolverTest {

    private fun lesson(n: Int, start: String, room: String, room2: String = "") = LessonEntity(
        groupName = "11А",
        dayOfWeek = 1,
        lessonNumber = n,
        timeStart = start,
        timeEnd = start, // для «начался ли» важен только старт
        subjectRaw = "Математика",
        roomFirst = room,
        teacherFirst = "",
        roomSecond = room2
    )

    // Пн: 1-й 08:30, 2-й 09:25, 3-й 10:35 (реальные звоны колледжа)
    private val lessons = listOf(
        lesson(1, "08:30", "118"),
        lesson(2, "09:25", "223"),
        lesson(3, "10:35", "320")
    )

    private fun at(h: Int, m: Int) = h * 60 + m

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
}
