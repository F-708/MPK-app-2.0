package com.politex

import com.politex.ui.screens.shouldDelete
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V13: пороги swipe-to-delete — чистая функция shouldDelete.
 * Стенд: ширина 300px, порог флинга 72px (24dp на плотности 3x).
 */
class SwipeToDeleteThresholdTest {
    @Test
    fun `drag past half deletes`() {
        assertTrue(shouldDelete(offsetX = -160f, widthPx = 300f, lastFrameDeltaPx = 0f, flingThresholdPx = 72f))
    }

    @Test
    fun `drag under half springs back`() {
        assertFalse(shouldDelete(offsetX = -140f, widthPx = 300f, lastFrameDeltaPx = 0f, flingThresholdPx = 72f))
    }

    @Test
    fun `exactly half is not past half`() {
        assertFalse(shouldDelete(offsetX = -150f, widthPx = 300f, lastFrameDeltaPx = -71f, flingThresholdPx = 72f))
    }

    @Test
    fun `left fling deletes even at small offset`() {
        assertTrue(shouldDelete(offsetX = -20f, widthPx = 300f, lastFrameDeltaPx = -80f, flingThresholdPx = 72f))
    }

    @Test
    fun `right fling is a return gesture and never deletes`() {
        assertFalse(shouldDelete(offsetX = -100f, widthPx = 300f, lastFrameDeltaPx = 200f, flingThresholdPx = 72f))
    }

    @Test
    fun `zero width never deletes`() {
        assertFalse(shouldDelete(offsetX = -10f, widthPx = 0f, lastFrameDeltaPx = -100f, flingThresholdPx = 72f))
    }
}
