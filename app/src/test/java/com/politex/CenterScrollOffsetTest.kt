package com.politex

import com.politex.ui.screens.centerScrollOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** X15: центрирование выбранного чипа фильтра — чистая функция centerScrollOffset (px). */
class CenterScrollOffsetTest {
    @Test
    fun `first chip scrolls to zero`() {
        assertEquals(0, centerScrollOffset(chipStart = 0, chipWidth = 100, viewport = 800, maxScroll = 1000))
    }

    @Test
    fun `middle chip is centered`() {
        // центр чипа 550, половина вьюпорта 400 → 150
        assertEquals(150, centerScrollOffset(chipStart = 500, chipWidth = 100, viewport = 800, maxScroll = 1000))
    }

    @Test
    fun `last chip clamps to max`() {
        // центр 1750 - 400 = 1350 > maxScroll 1000 → 1000
        assertEquals(1000, centerScrollOffset(chipStart = 1700, chipWidth = 100, viewport = 800, maxScroll = 1000))
    }
}
