package com.politex

import androidx.compose.ui.geometry.Offset
import com.politex.ui.screens.materials.DocZoom
import com.politex.ui.screens.materials.LiveTransform
import com.politex.ui.screens.materials.applyLive
import com.politex.ui.screens.materials.bookmarkSizeFactor
import com.politex.ui.screens.materials.commitDeltaY
import com.politex.ui.screens.materials.focalAnchorX
import com.politex.ui.screens.materials.liveAnchor
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

    // FF4: двухфазный зум. Фаза 1 — applyLive: чистая функция, docZoom в сигнатуре НЕТ
    // (live-фаза его не трогает — компилируется как гарантия). Фаза 2 — commit формулы.
    @Test
    fun `FF4 applyLive - первый кадр (pan=0) даёт pan = c(1-k) + d k`() {
        val center = Offset(540f, 1000f)
        val centroid = Offset(840f, 1200f)
        val d = Offset(20f, -10f)
        val t = applyLive(LiveTransform(1f, Offset.Zero), 1.5f, d, centroid, center, 0.25f, 8f)
        assertEquals(1.5f, t.zoom, eps)
        val c = centroid - center
        val expected = c * (1f - 1.5f) + d * 1.5f
        assertEquals(expected.x, t.pan.x, eps)
        assertEquals(expected.y, t.pan.y, eps)
    }

    @Test
    fun `FF4 applyLive - фокальный инвариант по многим кадрам точка остаётся под центроидом`() {
        val center = Offset(540f, 1000f)
        var t = LiveTransform(1f, Offset.Zero)
        var c = Offset(300f, 800f)
        // контентная точка под центроидом (смещение от центра): q = (c - center - pan)/zoom
        val q = c - center
        repeat(6) { i ->
            val k = if (i % 2 == 0) 1.25f else 0.9f
            val d = Offset(15f * (i - 2), -7f * i)
            c += d
            t = applyLive(t, k, d, c, center, 0.1f, 10f)
            // инвариант: zoom*q + pan == c - center (точка q под текущим центроидом)
            val screen = q * t.zoom + t.pan
            assertEquals((c - center).x, screen.x, 1e-2f)
            assertEquals((c - center).y, screen.y, 1e-2f)
        }
    }

    @Test
    fun `FF4 applyLive - liveZoom клампится в границы, pan считается с ЭФФЕКТИВНЫМ k`() {
        val center = Offset(100f, 100f)
        val centroid = Offset(300f, 300f)
        val t = applyLive(LiveTransform(3.9f, Offset.Zero), 2f, Offset.Zero, centroid, center, 0.5f, 4f)
        assertEquals(4f, t.zoom, eps)
        val k = 4f / 3.9f
        val expected = (centroid - center) * (1f - k)
        assertEquals(expected.x, t.pan.x, 1e-3f)
        assertEquals(expected.y, t.pan.y, 1e-3f)
    }

    @Test
    fun `FF4 commit - docZoom = clamp(docZoom liveZoom)`() {
        assertEquals(2.4f, DocZoom.apply(1.2f, 2f), eps)
        assertEquals(DocZoom.MAX, DocZoom.apply(3f, 2f), eps)
        assertEquals(DocZoom.MIN, DocZoom.apply(0.6f, 0.5f), eps)
        // ratio для restore = newZoom/oldZoom — ровно liveZoom, если clamp не сработал
        val old = 1.4f
        val live = 1.8f
        assertEquals(live, DocZoom.apply(old, live) / old, eps)
    }

    @Test
    fun `FF4 liveAnchor - layout-координата точки под центроидом`() {
        // identity live: layer-координата = центроид
        assertEquals(300f, liveAnchor(540f, 300f, 0f, 1f), eps)
        // pan +100: контент сдвинут вправо ⇒ точка под пальцем на 100px левее в layer
        assertEquals(200f, liveAnchor(540f, 300f, 100f, 1f), eps)
        // zoom 2 вокруг center 540: c=740 ⇒ 540 + (740-540)/2 = 640
        assertEquals(640f, liveAnchor(540f, 740f, 0f, 2f), eps)
        // zoom 0 не делит на ноль
        assertTrue(liveAnchor(540f, 740f, 0f, 0f).isFinite())
    }

    @Test
    fun `FF4 commitDeltaY - чистый драг (ratio=1) сдвигает скролл на -livePanY`() {
        assertEquals(-50f, commitDeltaY(1f, 1000f, 400f, 50f, 1f), eps)
        assertEquals(30f, commitDeltaY(1f, 1000f, 400f, -30f, 1f), eps)
    }

    @Test
    fun `FF4 commitDeltaY - фокальная поправка при центроиде в центре = (ratio-1) centerY`() {
        // ratio == liveZoom (clamp не сработал), cy == centerY, panY == 0
        assertEquals((2f - 1f) * 1000f, commitDeltaY(2f, 1000f, 1000f, 0f, 2f), eps)
        // без поправки restoreY оставил бы верх вьюпорта на месте — контент «уехал» бы вверх
        assertEquals(0f, commitDeltaY(1f, 1000f, 1000f, 0f, 1f), eps)
    }

    @Test
    fun `FF4 commit round-trip - restoreX возвращает точку под центроид в новой раскладке`() {
        val centerX = 540f
        val h = 100f
        val cx = 300f
        val live = LiveTransform(1.5f, Offset(40f, 0f))
        val ratio = live.zoom
        // точка layout под центроидом во время live-фазы (ровно так считает коллектор)
        val contentX = focalAnchorX(h, liveAnchor(centerX, cx, live.pan.x, live.zoom))
        val hNew = restoreX(contentX, ratio, cx, maxScroll = 10_000f)
        // после commit точка масштабировалась на ratio и снова под центроидом: hNew + cx = contentX*ratio
        assertEquals(contentX * ratio, hNew + cx, eps)
    }
}
