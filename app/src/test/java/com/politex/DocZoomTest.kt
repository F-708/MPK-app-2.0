package com.politex

import com.politex.ui.screens.materials.DocZoom
import com.politex.ui.screens.materials.quantizeRenderZoom
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Y3: единый зум документа (PDF + DOCX) — чистые функции.
 * Clamp 0.5..4, сброс (двойной тап → DEFAULT=1), квантование зума рендера.
 */
class DocZoomTest {
    private val eps = 1e-6f

    @Test
    fun `docZoom клампится в 0_5 до 4`() {
        assertEquals(0.5f, DocZoom.apply(0.6f, 0.5f), eps) // уход вниз — clamp MIN
        assertEquals(4f, DocZoom.apply(3f, 2f), eps) // уход вверх — clamp MAX
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
        // логика сброса: любое текущее значение → DEFAULT
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
}
