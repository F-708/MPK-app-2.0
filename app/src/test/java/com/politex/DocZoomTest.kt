package com.politex

import com.politex.ui.screens.materials.DocZoom
import com.politex.ui.screens.materials.bookmarkSizeFactor
import com.politex.ui.screens.materials.quantizeRenderZoom
import com.politex.ui.screens.materials.restoreScroll
import org.junit.Assert.assertEquals
import org.junit.Test

class DocZoomTest {
    private val eps = 1e-6f

    @Test
    fun `docZoom клампится в 0_5 до 4`() {
        assertEquals(0.5f, DocZoom.apply(0.6f, 0.5f), eps)
        assertEquals(4f, DocZoom.apply(3f, 2f), eps)
        assertEquals(0.5f, DocZoom.apply(0.5f, 0.1f), eps)
        assertEquals(4f, DocZoom.apply(4f, 10f), eps)
    }

    @Test
    fun `обычное применение множителя`() {
        assertEquals(1.5f, DocZoom.apply(1f, 1.5f), eps)
        assertEquals(1f, DocZoom.apply(2f, 0.5f), eps)
        assertEquals(2f, DocZoom.apply(1.4f, 1.4285715f), eps)
    }

    @Test
    fun `сброс (двойной тап и тап по индикатору) — DEFAULT = 100 процентов`() {
        assertEquals(1f, DocZoom.DEFAULT, eps)

        val zoomed = DocZoom.apply(DocZoom.DEFAULT, 3.7f)
        assertEquals(DocZoom.DEFAULT, DocZoom.apply(zoomed, DocZoom.DEFAULT / zoomed), eps)
    }

    @Test
    fun `quantizeRenderZoom — шаг 0_25, границы не выходят за clamp-диапазон`() {
        assertEquals(1f, quantizeRenderZoom(1.1f), eps)
        assertEquals(2.25f, quantizeRenderZoom(2.3f), eps)
        assertEquals(0.5f, quantizeRenderZoom(0.5f), eps)
        assertEquals(4f, quantizeRenderZoom(4f), eps)
        assertEquals(0.5f, quantizeRenderZoom(0.1f), eps)
        assertEquals(4f, quantizeRenderZoom(99f), eps)
    }

    @Test
    fun `restoreScroll — вертикаль масштабируется ratio, горизонталь держит точку под центроидом`() {
        val r = restoreScroll(pageIdx = 3, offInPage = 100, ratio = 2f, contentX = 500f, cx = 200f, hMax = 5000)
        assertEquals(3, r.page)
        assertEquals(200, r.vOffset)
        assertEquals(800, r.hValue) // 500*2 - 200: та же точка контента снова под центроидом
    }

    @Test
    fun `restoreScroll — ratio 1 не сдвигает позицию`() {
        val r = restoreScroll(pageIdx = 2, offInPage = 37, ratio = 1f, contentX = 640f, cx = 320f, hMax = 2000)
        assertEquals(2, r.page)
        assertEquals(37, r.vOffset)
        assertEquals(320, r.hValue)
    }

    @Test
    fun `restoreScroll — hValue клампится в 0 до hMax, vOffset не отрицательный`() {
        assertEquals(0, restoreScroll(0, 0, 0.5f, contentX = 100f, cx = 400f, hMax = 1000).hValue)
        assertEquals(1000, restoreScroll(0, 0, 2f, contentX = 4000f, cx = 10f, hMax = 1000).hValue)
        assertEquals(0, restoreScroll(0, -5, 1f, contentX = 0f, cx = 0f, hMax = 0).vOffset)
        assertEquals(0, restoreScroll(-1, 0, 1f, contentX = 0f, cx = 0f, hMax = 0).page)
    }

    @Test
    fun `bookmarkSizeFactor — размер закладки меняется с зумом не более чем на 20 процентов`() {
        assertEquals(1f, bookmarkSizeFactor(1f), eps)
        assertEquals(1.1f, bookmarkSizeFactor(1.1f), eps)
        assertEquals(0.8f, bookmarkSizeFactor(0.5f), eps)
        assertEquals(0.8f, bookmarkSizeFactor(0.1f), eps)
        assertEquals(1.2f, bookmarkSizeFactor(4f), eps)
        assertEquals(1.2f, bookmarkSizeFactor(1.5f), eps)
    }
}
