package com.politex

import com.politex.ui.screens.materials.DocZoom
import com.politex.ui.screens.materials.bookmarkSizeFactor
import com.politex.ui.screens.materials.focalAnchorX
import com.politex.ui.screens.materials.restoreX
import com.politex.ui.screens.materials.restoreY
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

    // EE1: фокальный зум — точка под пальцами остаётся под пальцами.
    @Test
    fun `EE1 focalAnchorX - точка контента под центроидом = scroll + cx`() {
        assertEquals(150f, focalAnchorX(100f, 50f), eps)
        assertEquals(0f, focalAnchorX(0f, 0f), eps)
        assertEquals(-20f, focalAnchorX(10f, -30f), eps)
    }

    @Test
    fun `EE1 restoreX - contentX_ratio - cx, clamp в 0_max`() {
        assertEquals(300f, restoreX(200f, 2f, 100f, 1000f), eps) // 200*2-100
        assertEquals(500f, restoreX(200f, 10f, 0f, 500f), eps) // 2000 clamp max
        assertEquals(0f, restoreX(50f, 0.5f, 100f, 1000f), eps) // -75 clamp 0
        // zoom<1 ⇒ max=0 ⇒ всегда 0 (restore не борется с центрированием)
        assertEquals(0f, restoreX(500f, 0.5f, 100f, 0f), eps)
    }

    @Test
    fun `EE1 restoreX - отрицательный max трактуется как 0`() {
        assertEquals(0f, restoreX(100f, 2f, 50f, -10f), eps)
    }

    @Test
    fun `EE1 restoreY - внутристраничный offset масштабируется пропорционально zoom`() {
        assertEquals(200f, restoreY(100f, 2f), eps)
        assertEquals(50f, restoreY(100f, 0.5f), eps)
        assertEquals(0f, restoreY(0f, 3f), eps)
    }

    @Test
    fun `EE1 инвариант фокуса - scroll' + cx = contentX ratio (точка остаётся под центроидом)`() {
        val scroll = 120f
        val cx = 300f
        val ratio = 1.75f
        val contentX = focalAnchorX(scroll, cx)
        val newScroll = restoreX(contentX, ratio, cx, maxScroll = 10_000f)
        assertEquals(contentX * ratio, newScroll + cx, eps)
    }

    // EE6: bookmark-кнопка масштабируется с zoom, clamp ±20%.
    @Test
    fun `EE6 bookmarkSizeFactor клампится в 0_8 до 1_2`() {
        assertEquals(0.8f, bookmarkSizeFactor(0.5f), eps)
        assertEquals(0.8f, bookmarkSizeFactor(0.8f), eps)
        assertEquals(1f, bookmarkSizeFactor(1f), eps)
        assertEquals(1.2f, bookmarkSizeFactor(1.2f), eps)
        assertEquals(1.2f, bookmarkSizeFactor(4f), eps)
    }
}
