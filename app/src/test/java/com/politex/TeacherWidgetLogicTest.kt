package com.politex

import com.politex.data.local.entity.TeacherOverrideEntity
import com.politex.widget.NextLessonRef
import com.politex.widget.TeacherDayRef
import com.politex.widget.TeacherNextState
import com.politex.widget.TeacherWidgetLesson
import com.politex.widget.TeacherWidgetLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T3-C: чистая логика teacher-виджетов. RemoteViews/БД не тестируем (нет инфраструктуры) —
 * только выбор следующей пары, overrides, состояния и состав кэш-ключа.
 */
class TeacherWidgetLogicTest {
    private fun lesson(
        n: Int,
        startMin: Int,
        endMin: Int,
        room: String = "241",
        subject: String = "Математика",
        groups: List<String> = listOf("241"),
    ) = TeacherWidgetLesson(
        lessonNumber = n,
        subject = subject,
        groups = groups,
        room = room,
        start = "%02d:%02d".format(startMin / 60, startMin % 60),
        end = "%02d:%02d".format(endMin / 60, endMin % 60),
        startMinutes = startMin,
        endMinutes = endMin,
    )

    private fun at(h: Int, m: Int) = h * 60 + m

    // ——— overrides (§5.4) ———

    @Test
    fun override_otherRoom_replacesRoom() {
        val out = TeacherWidgetLogic.applyOverrides(
            listOf(lesson(1, at(8, 0), at(9, 0), room = "241")),
            listOf(TeacherOverrideEntity(dateString = "d", lessonNumber = 1, kind = TeacherOverrideEntity.KIND_OTHER_ROOM, room = "305")),
        )
        assertEquals("305", out.single().room)
        assertFalse(out.single().replaced)
    }

    @Test
    fun override_noLesson_removesIt() {
        val out = TeacherWidgetLogic.applyOverrides(
            listOf(lesson(1, at(8, 0), at(9, 0)), lesson(2, at(9, 0), at(10, 0))),
            listOf(TeacherOverrideEntity(dateString = "d", lessonNumber = 1, kind = TeacherOverrideEntity.KIND_NO_LESSON)),
        )
        assertEquals(listOf(2), out.map { it.lessonNumber })
    }

    @Test
    fun override_someoneElse_setsReplacementBadge() {
        val out = TeacherWidgetLogic.applyOverrides(
            listOf(lesson(1, at(8, 0), at(9, 0))),
            listOf(TeacherOverrideEntity(dateString = "d", lessonNumber = 1, kind = TeacherOverrideEntity.KIND_SOMEONE_ELSE)),
        )
        assertTrue(out.single().replaced)
        assertEquals("241", out.single().room) // кабинет остаётся
    }

    @Test
    fun override_unknownKindAndOtherNumber_ignored() {
        val src = listOf(lesson(1, at(8, 0), at(9, 0)))
        val out = TeacherWidgetLogic.applyOverrides(
            src,
            listOf(
                TeacherOverrideEntity(dateString = "d", lessonNumber = 9, kind = TeacherOverrideEntity.KIND_NO_LESSON),
                TeacherOverrideEntity(dateString = "d", lessonNumber = 1, kind = "GARBAGE"),
            ),
        )
        assertEquals(src, out)
    }

    @Test
    fun overrides_resultSortedByLessonNumber() {
        val out = TeacherWidgetLogic.applyOverrides(
            listOf(lesson(3, at(11, 0), at(12, 0)), lesson(1, at(8, 0), at(9, 0))),
            emptyList(),
        )
        assertEquals(listOf(1, 3), out.map { it.lessonNumber })
    }

    // ——— состояния: идёт / следующая / пусто / выходной ———

    @Test
    fun state_running_nowInsideLesson() {
        val l = listOf(lesson(1, at(8, 0), at(9, 0)), lesson(2, at(9, 0), at(10, 0)))
        val s = TeacherWidgetLogic.nextState(l, at(8, 30), dow = 3, next = null)
        assertTrue(s is TeacherNextState.Running)
        assertEquals(1, (s as TeacherNextState.Running).lesson.lessonNumber)
    }

    @Test
    fun state_nextToday_nowBeforeFirst() {
        val l = listOf(lesson(1, at(9, 0), at(10, 0)))
        val s = TeacherWidgetLogic.nextState(l, at(8, 0), dow = 3, next = null)
        assertTrue(s is TeacherNextState.NextToday)
        assertEquals(1, (s as TeacherNextState.NextToday).lesson.lessonNumber)
    }

    @Test
    fun state_nextToday_skipsRunningPicksFollowing() {
        val l = listOf(lesson(1, at(8, 0), at(9, 0)), lesson(2, at(9, 0), at(10, 0)))
        // 08:30 — первая идёт; «следующая сегодня» не нужна (Running приоритетнее)
        assertTrue(TeacherWidgetLogic.nextState(l, at(8, 30), 3, null) is TeacherNextState.Running)
        // 09:30 — первая прошла, вторая идёт
        assertEquals(2, (TeacherWidgetLogic.nextState(l, at(9, 30), 3, null) as TeacherNextState.Running).lesson.lessonNumber)
    }

    @Test
    fun state_dayDone_allPast() {
        val l = listOf(lesson(1, at(8, 0), at(9, 0)))
        val s = TeacherWidgetLogic.nextState(l, at(20, 0), dow = 3, next = null)
        assertTrue(s is TeacherNextState.DayDone)
    }

    @Test
    fun state_emptyToday_carriesNextRef() {
        val next = NextLessonRef("09.10.2026", 4, lesson(1, at(9, 50), at(10, 35)))
        val s = TeacherWidgetLogic.nextState(emptyList(), at(12, 0), dow = 3, next = next)
        assertTrue(s is TeacherNextState.EmptyToday)
        assertEquals(next, (s as TeacherNextState.EmptyToday).next)
    }

    @Test
    fun state_dayOff_sundayNoLessons() {
        val s = TeacherWidgetLogic.nextState(emptyList(), at(12, 0), dow = 7, next = null)
        assertTrue(s is TeacherNextState.DayOff)
    }

    @Test
    fun state_sundayWithLessons_notDayOff() {
        // вс с парами (край) — показываем их, а не «Выходной»
        val l = listOf(lesson(1, at(9, 0), at(10, 0)))
        assertTrue(TeacherWidgetLogic.nextState(l, at(9, 30), 7, null) is TeacherNextState.Running)
    }

    // ——— выбор следующей пары из списка дней (кэш, без сети) ———

    @Test
    fun findNext_skipsSundayAndEmptyDays() {
        val days = listOf(
            TeacherDayRef("09.10.2026", 4, emptyList()), // чт — пусто
            TeacherDayRef("10.10.2026", 5, listOf(lesson(1, at(8, 0), at(9, 0)))), // пт — есть
            TeacherDayRef("11.10.2026", 6, listOf(lesson(1, at(8, 0), at(9, 0)))), // сб
        )
        val next = TeacherWidgetLogic.findNextLesson(days)
        assertEquals("10.10.2026", next?.dateString)
        assertEquals(5, next?.dow)
    }

    @Test
    fun findNext_sundayDaySkippedEvenIfListed() {
        val days = listOf(
            TeacherDayRef("sun", 7, listOf(lesson(1, at(8, 0), at(9, 0)))), // вс — игнор
            TeacherDayRef("mon", 1, listOf(lesson(2, at(8, 0), at(9, 0)))),
        )
        assertEquals("mon", TeacherWidgetLogic.findNextLesson(days)?.dateString)
    }

    @Test
    fun findNext_picksLowestLessonNumber() {
        val days = listOf(
            TeacherDayRef("d", 2, listOf(lesson(3, at(11, 0), at(12, 0)), lesson(1, at(8, 0), at(9, 0)))),
        )
        assertEquals(1, TeacherWidgetLogic.findNextLesson(days)?.lesson?.lessonNumber)
    }

    @Test
    fun findNext_emptyReturnsNull() {
        assertNull(TeacherWidgetLogic.findNextLesson(emptyList()))
    }

    // ——— кэш-ключ: дата + минута + профиль + override-счётчик ———

    @Test
    fun cacheKey_includesDateMinuteProfileOverrideCount() {
        val base = TeacherWidgetLogic.cacheKey("08.10.2026", 500, "Иванов И.И.", 2)
        // каждая компонента меняет ключ (грабли v2.x «виджет отключается через день»)
        assertFalse(base == TeacherWidgetLogic.cacheKey("09.10.2026", 500, "Иванов И.И.", 2)) // дата
        assertFalse(base == TeacherWidgetLogic.cacheKey("08.10.2026", 501, "Иванов И.И.", 2)) // минута
        assertFalse(base == TeacherWidgetLogic.cacheKey("08.10.2026", 500, "Петров П.П.", 2)) // профиль
        assertFalse(base == TeacherWidgetLogic.cacheKey("08.10.2026", 500, "Иванов И.И.", 3)) // overrides
        // стабилен при тех же входах
        assertEquals(base, TeacherWidgetLogic.cacheKey("08.10.2026", 500, "Иванов И.И.", 2))
    }

    // ——— тексты плашек ———

    @Test
    fun updatedLabel_zeroEpoch_blank() {
        assertEquals("", TeacherWidgetLogic.updatedLabel(0L))
    }

    @Test
    fun updatedLabel_positive_hasPrefix() {
        assertTrue(TeacherWidgetLogic.updatedLabel(System.currentTimeMillis()).startsWith("обновлено в "))
    }

    @Test
    fun todayHeader_format() {
        assertEquals("Сегодня, 08.10", TeacherWidgetLogic.todayHeader(8, 10))
    }

    @Test
    fun nextLessonText_includesDowTimeRoom() {
        val next = NextLessonRef("d", 2, lesson(1, at(9, 50), at(10, 35), room = "241"))
        val t = TeacherWidgetLogic.nextLessonText(next)
        assertTrue(t.contains("вт"))
        assertTrue(t.contains("09:50"))
        assertTrue(t.contains("каб. 241"))
    }

    @Test
    fun dayOffText_withoutNext_canonicalMonday() {
        assertEquals("Выходной.\nДо 1-й пары в пн", TeacherWidgetLogic.dayOffText(null))
    }

    @Test
    fun emptyText_withNext_appendsSecondLine() {
        val next = NextLessonRef("d", 4, lesson(1, at(9, 50), at(10, 35)))
        val t = TeacherWidgetLogic.emptyText("Сегодня пар нет.", next)
        assertTrue(t.startsWith("Сегодня пар нет."))
        assertTrue(t.contains("Следующая —"))
    }
}
