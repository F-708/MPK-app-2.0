package com.politex

import com.politex.data.model.CollegeBellSchedule
import com.politex.widget.BellCountdownWidgetProvider
import com.politex.widget.CountdownMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Т10.5: чистая логика cutoff строки «до звонка» — утренний гейт 07:00,
 * пер-групповый cutoff до цикла звонков, скрытие после конца уроков и в пустые дни.
 *
 * Опорные звонки (STANDARD, Пн): 6-й урок 13:20–14:05, 12-й 18:50–19:35.
 * SATURDAY: 6-й урок 12:50–13:35, 8-й 14:40–15:25.
 */
class BellCutoffTest {
    private val standard = CollegeBellSchedule.getBellsForDay(1)
    private val saturday = CollegeBellSchedule.getBellsForDay(6)

    // ---------- Утренний гейт 07:00 ----------

    @Test
    fun hiddenBefore0700EvenIfLessonsLater() {
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                6 * 60 + 59,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun visibleAt0700Sharp() {
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                7 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        // 07:00 < 08:15 → «до 1 урока», 75 мин.
        assertEquals(75L, info?.first)
        assertEquals("до 1 урока", info?.second)
    }

    // ---------- GROUP_LESSONS: cutoff группы ----------

    @Test
    fun groupLessons_stillVisibleInsideLastLesson() {
        // 13:30 внутри 6-го урока (13:20–14:05): «до звонка», 35 мин.
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                13 * 60 + 30,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertNotNull(info)
        assertEquals("до звонка", info!!.second)
        assertEquals(6, info.third)
        assertEquals(35L, info.first)
    }

    @Test
    fun groupLessons_lastMinuteOfLastLessonStillCounts() {
        // 14:05 — последняя (включительно) минута 6-го урока: 0 мин, не скрыто.
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                14 * 60 + 5,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertNotNull(info)
        assertEquals(0L, info!!.first)
        assertEquals(6, info.third)
    }

    @Test
    fun groupLessons_hiddenOneMinuteAfterGroupEnd() {
        // Уроки группы кончились в 14:05 → в 14:06 строки уже нет (главный баг Т10).
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                14 * 60 + 6,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun groupLessons_breakAfterLastGroupLesson_hiddenNotNextBell() {
        // 14:10 — перемена после последнего урока группы: НЕ «до 7 урока», а скрыто.
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                14 * 60 + 10,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun groupLessons_afternoonAfterGroupEnd_hidden() {
        // 16:00: у группы 6 уроков, колледж ещё занимается (7–12) — строки нет.
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                16 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun groupLessons_breakBetweenGroupLessons_countsToNextGroupLesson() {
        // 12:10 — большая перемена (после 4-го, 11:15–12:00): «до 5 урока», 15 мин.
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                12 * 60 + 10,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertNotNull(info)
        assertEquals("до 5 урока", info!!.second)
        assertEquals(15L, info.first)
    }

    @Test
    fun groupLessons_smallBreakBeforeLastLesson() {
        // 13:12 — перемена после 5-го (12:25–13:10): «до 6 урока», 8 мин.
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                13 * 60 + 12,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertNotNull(info)
        assertEquals("до 6 урока", info!!.second)
        assertEquals(8L, info.first)
    }

    @Test
    fun groupLessons_noLessonsToday_hiddenAllDay() {
        // Пустой день группы (DB пуст) — скрыто весь день, без fallback на все звонки.
        for (minute in intArrayOf(7 * 60, 10 * 60, 15 * 60, 20 * 60)) {
            assertNull(
                BellCountdownWidgetProvider.countdownForMinute(
                    minute,
                    1,
                    standard,
                    CountdownMode.GROUP_LESSONS,
                    null,
                ),
            )
        }
    }

    // ---------- Суббота ----------

    @Test
    fun saturday_visibleDuringLastLesson() {
        // Сб, 15:00 внутри 8-го урока (14:40–15:25): 25 мин.
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                15 * 60,
                6,
                saturday,
                CountdownMode.GROUP_LESSONS,
                8,
            )
        assertNotNull(info)
        assertEquals(8, info!!.third)
        assertEquals(25L, info.first)
    }

    @Test
    fun saturday_hiddenAfterLastLesson() {
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                15 * 60 + 26,
                6,
                saturday,
                CountdownMode.GROUP_LESSONS,
                8,
            ),
        )
    }

    @Test
    fun saturday_groupWith6Lessons_hiddenAfter13_35() {
        // Сб, у группы 6 уроков (конец 13:35): в 13:36 скрыто, хотя 7–8 идут до 15:25.
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                13 * 60 + 36,
                6,
                saturday,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    // ---------- ALL_BELLS: cutoff колледжа ----------

    @Test
    fun allBells_visibleDuringLateLesson() {
        // 18:00 внутри 11-го урока (17:55–18:40): «до звонка», 40 мин —
        // режим колледжа игнорирует cutoff группы.
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                18 * 60,
                1,
                standard,
                CountdownMode.ALL_BELLS,
                null,
            )
        assertNotNull(info)
        assertEquals("до звонка", info!!.second)
        assertEquals(40L, info.first)
    }

    @Test
    fun allBells_hiddenAfterCollegeLastBell() {
        // 19:36 — минута после конца 12-го урока (19:35): скрыто и в ALL_BELLS.
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                19 * 60 + 36,
                1,
                standard,
                CountdownMode.ALL_BELLS,
                null,
            ),
        )
    }

    // ---------- Воскресенье ----------

    @Test
    fun sunday_hiddenAllDay_bothModes() {
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                12 * 60,
                7,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                12 * 60,
                7,
                standard,
                CountdownMode.ALL_BELLS,
                null,
            ),
        )
    }
}
