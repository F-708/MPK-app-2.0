package com.politex

import com.politex.ui.screens.shouldDelete
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * X11a: пороги swipe-to-delete — чистая функция shouldDelete.
 * Стенд: ширина 300px, порог флинга 36px (12dp на плотности 3x).
 * Граница позиции включительная: 0.35·300 = 105f точно (IEEE754) → удаляет.
 */
class SwipeToDeleteThresholdTest {
    @Test
    fun `drag past 35 percent deletes`() {
        assertTrue(shouldDelete(offsetX = -160f, widthPx = 300f, lastFrameDeltaPx = 0f, flingThresholdPx = 36f))
    }

    @Test
    fun `exactly 35 percent deletes`() {
        assertTrue(shouldDelete(offsetX = -105f, widthPx = 300f, lastFrameDeltaPx = 0f, flingThresholdPx = 36f))
    }

    @Test
    fun `34 percent springs back`() {
        assertFalse(shouldDelete(offsetX = -102f, widthPx = 300f, lastFrameDeltaPx = 0f, flingThresholdPx = 36f))
    }

    @Test
    fun `left fling at threshold deletes even at small offset`() {
        assertTrue(shouldDelete(offsetX = -20f, widthPx = 300f, lastFrameDeltaPx = -36f, flingThresholdPx = 36f))
    }

    @Test
    fun `left fling under threshold does not delete at small offset`() {
        assertFalse(shouldDelete(offsetX = -20f, widthPx = 300f, lastFrameDeltaPx = -35f, flingThresholdPx = 36f))
    }

    @Test
    fun `right fling is a return gesture and never deletes`() {
        assertFalse(shouldDelete(offsetX = -100f, widthPx = 300f, lastFrameDeltaPx = 200f, flingThresholdPx = 36f))
    }

    @Test
    fun `zero width never deletes`() {
        assertFalse(shouldDelete(offsetX = -10f, widthPx = 0f, lastFrameDeltaPx = -100f, flingThresholdPx = 36f))
    }
}
