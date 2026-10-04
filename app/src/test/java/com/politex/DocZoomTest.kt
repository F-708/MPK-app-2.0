package com.politex

import androidx.compose.ui.geometry.Offset
import com.politex.ui.screens.materials.DocZoom
import com.politex.ui.screens.materials.PAN_ENABLED_SCALE
import com.politex.ui.screens.materials.clampPan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocZoomTest {
    private val eps = 1e-6f

    @Test
    fun `CC4 scale клампится в 0_5 до 4`() {
        assertEquals(0.5f, DocZoom.apply(0.6f, 0.5f), eps)
        assertEquals(4f, DocZoom.apply(3f, 2f), eps)
        assertEquals(0.5f, DocZoom.apply(0.5f, 0.1f), eps)
        assertEquals(4f, DocZoom.apply(4f, 10f), eps)
    }

    @Test
    fun `CC4 обычное применение множителя`() {
        assertEquals(1.5f, DocZoom.apply(1f, 1.5f), eps)
        assertEquals(1f, DocZoom.apply(2f, 0.5f), eps)
        assertEquals(2f, DocZoom.apply(1.4f, 1.4285715f), eps)
    }

    @Test
    fun `CC4 сброс (двойной тап и тап по индикатору) — DEFAULT = 100 процентов`() {
        assertEquals(1f, DocZoom.DEFAULT, eps)

        val zoomed = DocZoom.apply(DocZoom.DEFAULT, 3.7f)
        assertEquals(DocZoom.DEFAULT, DocZoom.apply(zoomed, DocZoom.DEFAULT / zoomed), eps)
    }

    @Test
    fun `CC4 clampPan — при scale 1 и ниже pan зафиксирован в нуле`() {
        assertEquals(Offset.Zero, clampPan(Offset(500f, -700f), 1f, 1080f, 2000f))
        assertEquals(Offset.Zero, clampPan(Offset(500f, 700f), 0.5f, 1080f, 2000f))
    }

    @Test
    fun `CC4 clampPan — границы (scale-1)*size-2 по каждой оси`() {
        val p = clampPan(Offset(1e6f, -1e6f), 2f, 1080f, 2000f)
        assertEquals(540f, p.x, eps) // (2-1)*1080/2
        assertEquals(-1000f, p.y, eps) // -(2-1)*2000/2

        val q = clampPan(Offset(-1e6f, 1e6f), 4f, 100f, 200f)
        assertEquals(-150f, q.x, eps) // -(4-1)*100/2
        assertEquals(300f, q.y, eps) // (4-1)*200/2
    }

    @Test
    fun `CC4 clampPan — внутри границ смещение не меняется`() {
        val p = Offset(100f, -200f)
        assertEquals(p, clampPan(p, 3f, 1080f, 2000f))
    }

    @Test
    fun `CC4 одиночный палец панит только выше порога, при scale 1 скролл не блокируется`() {
        assertTrue(PAN_ENABLED_SCALE > 1f)
        assertTrue(DocZoom.DEFAULT < PAN_ENABLED_SCALE) // scale 1 → палец не handled → LazyColumn скроллится
        assertTrue(DocZoom.MAX > PAN_ENABLED_SCALE)
    }
}
