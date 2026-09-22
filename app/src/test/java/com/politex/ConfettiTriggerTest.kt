package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.util.ConfettiGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Триггеры конфетти: ровная минута конца последнего урока (б) и одноразовый
 * флаг «завтра ≤3 уроков» (c). Чистая логика ConfettiGate, без Android.
 */
class ConfettiTriggerTest {

    private fun lesson(date: String, number: Int) = LessonEntity(
        groupName = "41О",
        dayOfWeek = 2,
        lessonNumber = number,
        timeStart = "08:15",
        timeEnd = "09:00",
        subjectRaw = "Тест",
        roomFirst = "1",
        teacherFirst = "",
        dateString = date
    )

    // --- (б) точная минута конца последнего урока ---

    @Test
    fun testFiresExactlyAtLastLessonEndMinute() {
        val end = CollegeBellSchedule.getBellsForDay(2).last { !it.isInfoHour }.endMinutes
        assertTrue(ConfettiGate.shouldCelebrate(celebratedToday = false, endMinute = end, nowMinute = end))
    }

    @Test
    fun testNoFireOneMinuteAfterEnd() {
        val end = CollegeBellSchedule.getBellsForDay(2).last { !it.isInfoHour }.endMinutes
        assertFalse(ConfettiGate.shouldCelebrate(celebratedToday = false, endMinute = end, nowMinute = end + 1))
    }

    @Test
    fun testNoFireOneMinuteBeforeEnd() {
        val end = CollegeBellSchedule.getBellsForDay(2).last { !it.isInfoHour }.endMinutes
        assertFalse(ConfettiGate.shouldCelebrate(celebratedToday = false, endMinute = end, nowMinute = end - 1))
    }

    @Test
    fun testNoFireWhenAlreadyCelebratedToday() {
        val end = CollegeBellSchedule.getBellsForDay(2).last { !it.isInfoHour }.endMinutes
        assertFalse(ConfettiGate.shouldCelebrate(celebratedToday = true, endMinute = end, nowMinute = end))
    }

    @Test
    fun testNoFireWhenNoEndMinute() {
        assertFalse(ConfettiGate.shouldCelebrate(celebratedToday = false, endMinute = null, nowMinute = 500))
    }

    // --- минута конца последнего урока группы ---

    @Test
    fun testGroupMaxLessonClampsToItsBell() {
        // У группы последний урок 5 → конец 5-го звонка, а не 12-го.
        val end = ConfettiGate.lastLessonEndMinute(
            CollegeBellSchedule.getBellsForDay(1), maxLessonNumber = 5
        )
        assertEquals(CollegeBellSchedule.getBellsForDay(1).first { it.lessonNumber == 5 }.endMinutes, end)
    }

    @Test
    fun testInfoHourSkippedOnThursday() {
        // Инфочас не считается уроком: конец — 12-й звонок.
        val bells = CollegeBellSchedule.getBellsForDay(4)
        val end = ConfettiGate.lastLessonEndMinute(bells, maxLessonNumber = 12)
        assertEquals(bells.first { it.lessonNumber == 12 }.endMinutes, end)
    }

    @Test
    fun testFallbackToCollegeLastBellWhenNoGroupRows() {
        // Нет строк по группе → фолбэк: последний звонок колледжа.
        val bells = CollegeBellSchedule.getBellsForDay(2)
        val fallback = ConfettiGate.lastLessonEndMinute(bells, maxLessonNumber = null)
        assertEquals(bells.last { !it.isInfoHour }.endMinutes, fallback)
    }

    // --- выбор «сегодняшних» строк из БД (оба формата дат) ---

    @Test
    fun testTodayRowsPreferredOverOlderStamps() {
        // Строка №8 со старой датой исключается: максимум — 6 среди строк «на сегодня».
        val rows = listOf(lesson("01.01.2026", 8), lesson("10.09.2026", 5), lesson("10.09.2026", 6))
        val today = ConfettiGate.selectTodayLessons(rows, "10.09.2026")
        assertEquals(6, today.maxOf { it.lessonNumber })
    }

    @Test
    fun testLegacyStampDatesUnified() {
        // Старый формат с дефисом приравнивается к точке.
        val rows = listOf(lesson("10-09-2026", 5), lesson("10.09.2026", 7))
        val today = ConfettiGate.selectTodayLessons(rows, "10.09.2026")
        assertEquals(7, today.maxOf { it.lessonNumber })
    }

    @Test
    fun testWeeklyTemplateRowsCountAsToday() {
        // Пустая dateString — недельный шаблон: берётся как сегодняшний день.
        val rows = listOf(lesson("", 3))
        assertEquals(3, ConfettiGate.selectTodayLessons(rows, "10.09.2026").maxOf { it.lessonNumber })
    }

    @Test
    fun testOnlyWeeklyTemplateFallsBackToLatestStamp() {
        // Нет ни сегодняшней даты, ни шаблона → старый формат «неделя одной датой».
        val rows = listOf(lesson("01.09.2026", 4), lesson("05.09.2026", 9))
        val picked = ConfettiGate.selectTodayLessons(rows, "10.09.2026")
        assertEquals(9, picked.maxOf { it.lessonNumber })
    }

    // --- (c) «завтра ≤3 уроков»: установка и одноразовое чтение ---

    @Test
    fun testPendingSetOnlyForThreeOrFewerLessons() {
        val store = mutableMapOf<String, Boolean>()
        ConfettiGate.setPending(store, "11.09.2026", 3)
        ConfettiGate.setPending(store, "12.09.2026", 4)
        ConfettiGate.setPending(store, "13.09.2026", 0)
        assertTrue(store.containsKey("confetti_tomorrow_pending_11.09.2026"))
        assertEquals(1, store.size)
    }

    @Test
    fun testPendingConsumedExactlyOnce() {
        val store = mutableMapOf<String, Boolean>()
        ConfettiGate.setPending(store, "11.09.2026", 2)
        assertTrue(ConfettiGate.consumePending(store, "11.09.2026"))
        assertFalse(ConfettiGate.consumePending(store, "11.09.2026"))
        assertTrue(store.isEmpty())
    }

    @Test
    fun testConsumeWithoutPendingIsFalse() {
        assertFalse(ConfettiGate.consumePending(mutableMapOf(), "11.09.2026"))
    }

    @Test
    fun testPendingForDifferentDateNotConsumed() {
        val store = mutableMapOf<String, Boolean>()
        ConfettiGate.setPending(store, "11.09.2026", 2)
        assertFalse(ConfettiGate.consumePending(store, "12.09.2026"))
        assertTrue(ConfettiGate.consumePending(store, "11.09.2026"))
    }
}
