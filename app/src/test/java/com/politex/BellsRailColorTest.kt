package com.politex

import com.politex.data.model.BellItem
import com.politex.ui.screens.railColorForMinute
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorTextDisabled
import com.politex.ui.theme.ColorTextMuted
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * X17: railColorForMinute — цвет чёрточки/линии рельса = цвет сегмента урока
 * в этой минуте: прошедшее → ColorTextDisabled, будущее до blueEnd → ColorActiveBlue,
 * после blueEnd → ColorTextMuted 55%, не-сегодня → ColorTextDisabled.
 */
class BellsRailColorTest {
    private val bells =
        listOf(
            BellItem(1, title = "Урок", start = "09:00", end = "09:45", breakAfterMinutes = 15),
            BellItem(2, title = "Урок", start = "10:00", end = "10:45", breakAfterMinutes = 10),
            BellItem(3, title = "Урок", start = "10:55", end = "11:40", breakAfterMinutes = 0),
        )

    // Группа учится до конца 2-го урока
    private val blueEnd = 10 * 60 + 45
    private val muted = ColorTextMuted.copy(alpha = 0.55f)

    @Test
    fun pastLesson_boundariesAreDisabledGray() {
        // 10:10 — 1-й урок (09:00–09:45) прошёл: обе его границы серые
        assertEquals(ColorTextDisabled, railColorForMinute(9 * 60, bells, 10 * 60 + 10, blueEnd, true))
        assertEquals(ColorTextDisabled, railColorForMinute(9 * 60 + 45, bells, 10 * 60 + 10, blueEnd, true))
    }

    @Test
    fun ongoingLesson_startTickIsBlueNotGray() {
        // 10:10 внутри 2-го урока (10:00–10:45): минута старта уже «в прошлом»,
        // но линия в этой точке синяя → чёрточка синяя (главный баг X17).
        assertEquals(ColorActiveBlue, railColorForMinute(10 * 60, bells, 10 * 60 + 10, blueEnd, true))
        assertEquals(ColorActiveBlue, railColorForMinute(10 * 60 + 45, bells, 10 * 60 + 10, blueEnd, true))
    }

    @Test
    fun futureWithinBlueEnd_isBlue() {
        // 09:50 — 2-й урок в будущем, но до blueEnd → синий
        assertEquals(ColorActiveBlue, railColorForMinute(10 * 60, bells, 9 * 60 + 50, blueEnd, true))
    }

    @Test
    fun afterBlueEnd_isMutedGray() {
        // границы 3-го урока (10:55, 11:40) — после cutoff группы 10:45 → приглушённые
        assertEquals(muted, railColorForMinute(10 * 60 + 55, bells, 10 * 60 + 10, blueEnd, true))
        assertEquals(muted, railColorForMinute(11 * 60 + 40, bells, 10 * 60 + 10, blueEnd, true))
    }

    @Test
    fun notToday_allDisabled() {
        assertEquals(ColorTextDisabled, railColorForMinute(10 * 60, bells, 8 * 60, blueEnd, false))
        assertEquals(ColorTextDisabled, railColorForMinute(11 * 60 + 40, bells, 8 * 60, blueEnd, false))
    }

    @Test
    fun noBlueEnd_futureIsMuted() {
        // blueEnd == null (нет cutoff) — будущее не синее, приглушённое
        assertEquals(muted, railColorForMinute(10 * 60, bells, 9 * 60 + 50, null, true))
        // прошедшее всё равно серое
        assertEquals(ColorTextDisabled, railColorForMinute(9 * 60, bells, 9 * 60 + 50, null, true))
    }
}
