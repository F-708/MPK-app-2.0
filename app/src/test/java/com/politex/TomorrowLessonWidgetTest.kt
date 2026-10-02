package com.politex

import com.politex.data.model.CollegeBellSchedule
import com.politex.widget.BellCountdownWidgetProvider
import com.politex.widget.CountdownMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * W3: вечернее окно виджета «До звонка» — «до 1 урока завтра» вместо пустоты.
 * Звонки Пн: 1-й урок 08:15, последний 12-й 18:50–19:35.
 */
class TomorrowLessonWidgetTest {
    private val standard = CollegeBellSchedule.getBellsForDay(1)

    @Test
    fun eveningAfterGroupLessons_showsTomorrowMorning() {
        // Пн 20:00, у группы 6 уроков (конец 14:05) → до завтрашнего 08:15.
        val info =
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                20 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertEquals("до 1 урока завтра", info?.second)
        // 24*60-1200 + 495 = 1440-1200+495 = 735 мин
        assertEquals(735L, info?.first)
    }

    @Test
    fun eveningAfterCollegeBells_showsTomorrowMorning() {
        // Пн 19:36, ALL_BELLS: 12-й кончился в 19:35 → завтра 08:15.
        val info =
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                19 * 60 + 36,
                1,
                standard,
                CountdownMode.ALL_BELLS,
                null,
            )
        assertEquals("до 1 урока завтра", info?.second)
        // 1440 - 1176 = 264 мин до полуночи + 495 до 08:15 = 759
        assertEquals(759L, info?.first)
    }

    @Test
    fun saturdayEvening_tomorrowSunday_null() {
        // Сб вечер: завтра Вс, уроков нет — «до 1 урока завтра» было бы ложью.
        assertNull(
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                20 * 60,
                6,
                CollegeBellSchedule.getBellsForDay(6),
                CountdownMode.GROUP_LESSONS,
                8,
            ),
        )
    }

    @Test
    fun beforeLessonsEnd_null() {
        // 13:00 — 6-й урок группы (13:20–14:05) ещё не начался, уроки не кончились.
        assertNull(
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                13 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun morningGate07_null() {
        assertNull(
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                6 * 60 + 59,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun sunday_null() {
        assertNull(
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                20 * 60,
                7,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun noGroupLessonsToday_null() {
        // Пустой день группы — скрыто весь день (Т10), «завтра» не показываем.
        assertNull(
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                20 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                null,
            ),
        )
    }

    @Test
    fun night_at_00_24_showsFirstLessonToday() {
        // Заказчик: «0:24, виджет пустой без текста». Ночью показываем отсчёт
        // до первого урока сегодняшнего дня, а не пустую плашку.
        val info =
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                24,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertEquals("до 1 урока", info?.second)
        // 08:15 (495) - 24 = 471 мин
        assertEquals(471L, info?.first)
    }

    @Test
    fun night_at_00_09_displayedAsMinutes() {
        val info =
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                9,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertEquals("до 1 урока", info?.second)
    }

    @Test
    fun night_after07_null() {
        // После 07:00 работает обычная логика, ночное окно выключается
        assertNull(
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                7 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun night_sunday_null() {
        assertNull(
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                30,
                7,
                CollegeBellSchedule.getBellsForDay(6),
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun night_noGroupLessonsToday_null() {
        // День без уроков группы — показывать «до 1 урока» было бы ложью
        assertNull(
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                30,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                null,
            ),
        )
    }

    @Test
    fun night_collegeMode_worksWithoutGroupLessons() {
        // Режим «уроки колледжа»: уроки группы не важны, звонки есть всегда
        val info =
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                30,
                1,
                standard,
                CountdownMode.ALL_BELLS,
                null,
            )
        assertEquals("до 1 урока", info?.second)
    }

    @Test
    fun hoursRounding() {
        // 735 мин = 12,25 ч → 12; 759 мин = 12,65 ч → 13; 25 мин → 1 (не 0)
        assertEquals("12 ч", BellCountdownWidgetProvider.formatHoursRounded(735L))
        assertEquals("13 ч", BellCountdownWidgetProvider.formatHoursRounded(759L))
        assertEquals("1 ч", BellCountdownWidgetProvider.formatHoursRounded(25L))
    }
}
