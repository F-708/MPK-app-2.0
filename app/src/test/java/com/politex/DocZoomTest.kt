package com.politex

import com.politex.ui.screens.materials.DocZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// DD1: layout-модель зума PDF — pageW = containerW * zoom, высота следует из аспекта,
// PdfRenderer не перерисовывается (Image масштабирует готовый битмап).
class DocZoomTest {
    private val eps = 1e-6f
    private val containerW = 1080f
    private val a4Aspect = 1.414f // высота/ширина страницы
    private val viewportH = 2000f

    @Test
    fun `DD1 zoom клампится в 0_5 до 4`() {
        assertEquals(0.5f, DocZoom.apply(0.6f, 0.5f), eps)
        assertEquals(4f, DocZoom.apply(3f, 2f), eps)
        assertEquals(0.5f, DocZoom.apply(0.5f, 0.1f), eps)
        assertEquals(4f, DocZoom.apply(4f, 10f), eps)
    }

    @Test
    fun `DD1 обычное применение множителя`() {
        assertEquals(1.5f, DocZoom.apply(1f, 1.5f), eps)
        assertEquals(1f, DocZoom.apply(2f, 0.5f), eps)
        assertEquals(2f, DocZoom.apply(1.4f, 1.4285715f), eps)
    }

    @Test
    fun `DD1 сброс (двойной тап и тап по индикатору) — DEFAULT = 100 процентов`() {
        assertEquals(1f, DocZoom.DEFAULT, eps)

        val zoomed = DocZoom.apply(DocZoom.DEFAULT, 3.7f)
        assertEquals(DocZoom.DEFAULT, DocZoom.apply(zoomed, DocZoom.DEFAULT / zoomed), eps)
    }

    @Test
    fun `DD1 pageW = containerW * zoom`() {
        assertEquals(540f, DocZoom.pageWidthPx(containerW, 0.5f), eps)
        assertEquals(containerW, DocZoom.pageWidthPx(containerW, 1f), eps)
        assertEquals(2160f, DocZoom.pageWidthPx(containerW, 2f), eps)
    }

    @Test
    fun `DD1 pageW клампится вместе с zoom`() {
        assertEquals(containerW * DocZoom.MIN, DocZoom.pageWidthPx(containerW, 0.01f), eps)
        assertEquals(containerW * DocZoom.MAX, DocZoom.pageWidthPx(containerW, 100f), eps)
    }

    @Test
    fun `DD1 при отдалении страница ЦЕЛИКОМ по высоте — высота = pageW * aspect`() {
        val heightAt1 = DocZoom.pageWidthPx(containerW, 1f) * a4Aspect
        val heightAt05 = DocZoom.pageWidthPx(containerW, 0.5f) * a4Aspect
        // высота пропорциональна зуму: zoom 0.5 → вдвое меньшая страница, а не обрезка вьюпорта
        assertEquals(heightAt1 / 2f, heightAt05, eps)
        assertTrue("при zoom < 1 страница помещается во вьюпорт целиком", heightAt05 < viewportH)
        assertTrue("при zoom = 1 A4-страница тоже видна целиком по высоте", heightAt1 <= viewportH)
    }

    @Test
    fun `DD2 strokeLayerScale = 1_zoom — экранная толщина штриха пропорциональна зуму`() {
        assertEquals(2f, DocZoom.strokeLayerScale(0.5f), eps)
        assertEquals(1f, DocZoom.strokeLayerScale(1f), eps)
        assertEquals(0.5f, DocZoom.strokeLayerScale(2f), eps)
        assertEquals(0.25f, DocZoom.strokeLayerScale(4f), eps)
        assertTrue(DocZoom.strokeLayerScale(0f).isFinite())
    }
}
